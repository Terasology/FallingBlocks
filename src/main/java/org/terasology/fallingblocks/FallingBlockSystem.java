// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.input.cameraTarget.CameraTargetSystem;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.event.OnChunkLoaded;
import org.terasology.world.chunks.event.BeforeChunkUnload;

@RegisterSystem(RegisterMode.AUTHORITY)
public class FallingBlockSystem extends BaseComponentSystem{
    
    private static final Logger logger = LoggerFactory.getLogger(FallingBlockSystem.class);
    
    @In
    private WorldProvider worldProvider;
    
    @In
    private BlockManager blockManager;
    private Block brick;
    private Block plank;
    
    private Node rootNode = null;
    private Vector3i rootNodePos = null;
    private static int ROOT_NODE_SIZE = ChunkConstants.SIZE_X; //This actually needs to be the minimum of SIZE_X, SIZE_Y and SIZE_Z, but it's assumed later that SIZE_Y >= SIZE_X = SIZE_Z anyway.
    private static final int ROOT_OFFSET = 0xAAAAAAA0; //The octree structure divides at different levels in fixed locations. This constant is chosen so that, as far as possible, the highest-level divisions are far from the origin, so that the root node isn't likely to need to be very large just because the relevant region overlaps one of the divisions.

    @In
    private CameraTargetSystem cameraTarget;
    
    @Override
    public void initialise() {
        brick = blockManager.getBlock("coreAssets:brick");
        plank = blockManager.getBlock("coreAssets:plank");
    }
    
    // TODO: Maybe make this a WorldChangeListener instead? Compare efficiency. Aggregate changes into batches (should improve efficiency and avoid confusing reentrant behaviour when falling blocks cause block updates).
    /**
     * Called every time a block is changed.
     * This means that the type of the block has changed.
     *
     * @param event       The block change event
     * @param blockEntity The entity of the block being changed
     */
    @ReceiveEvent
    public void blockUpdate(OnChangedBlock event, EntityRef blockEntity) {
        boolean oldSolid = TreeUtils.isSolid(event.getOldType());
        boolean newSolid = TreeUtils.isSolid(event.getNewType());
        if(oldSolid != newSolid) {
            Vector3i pos = new Vector3i(event.getBlockPosition()).sub(rootNodePos);
            Collection<Component> updatedComponents;
            if(newSolid) {
                updatedComponents = new HashSet();
                updatedComponents.add(rootNode.addBlock(pos).a);
            } else {
                updatedComponents = rootNode.removeBlock(pos).b;
            }
            
            for(Component component : updatedComponents) {
                checkComponentDetached(component);
            }
        }
    }
    
    @ReceiveEvent
    public void chunkLoaded(OnChunkLoaded event, EntityRef entity) {
        Vector3i chunkPos = event.getChunkPos();
        chunkPos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for(int y=0; y<ChunkConstants.SIZE_Y; y += ROOT_NODE_SIZE) {
            Vector3i pos = new Vector3i(chunkPos).addY(y);
            //logger.info("Loading chunk at "+pos+".");
            Node node = TreeUtils.buildNode(worldProvider, ROOT_NODE_SIZE, pos);
            if(rootNodePos == null) {
                //logger.info("Starting new root node.");
                rootNode = node;
                rootNodePos = pos;
            }
            while(!isWithinRootNode(pos)) {
                Vector3i relativePos = TreeUtils.modVector(new Vector3i(rootNodePos).add(ROOT_OFFSET, ROOT_OFFSET, ROOT_OFFSET), rootNode.size * 2);
                Vector3i newRootNodePos = new Vector3i(rootNodePos).sub(relativePos);
                //logger.info("Expanding root node from "+rootNodePos+", "+rootNode.size+" to "+newRootNodePos);
                // buildExpandedNode could return a different type of node if its old node argument is a non-internal node, with the same size as the size argument. That can't happen here.
                rootNode = TreeUtils.buildExpandedNode(rootNode, relativePos, rootNode.size*2);
                rootNodePos = newRootNodePos;
            }
            if(rootNode != node) {
                Set<Component> updatedComponents = rootNode.insertNewChunk(node, new Vector3i(pos).sub(rootNodePos));
                for(Component component : updatedComponents) {
                    checkComponentDetached(component);
                }
            }
        }
    }
    
    @ReceiveEvent
    public void chunkUnloaded(BeforeChunkUnload event, EntityRef entity) {
        Vector3i chunkPos = event.getChunkPos();
        chunkPos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for(int y=0; y<ChunkConstants.SIZE_Y; y += ROOT_NODE_SIZE) {
            Vector3i pos = new Vector3i(chunkPos).addY(y);
            rootNode = rootNode.removeChunk(pos.sub(rootNodePos), ROOT_NODE_SIZE).a;
            Pair<Integer, Node> shrinking = rootNode.canShrink();
            if(shrinking.a >= 0) {
                //logger.info("Shrinking root node to octant "+shrinking.a+".");
                rootNode = shrinking.b;
                rootNodePos.sub(TreeUtils.octantVector(shrinking.a, rootNode.size));
            } else if(shrinking.a == -1) {
                rootNode = null;
                rootNodePos = null;
            }
        }
    }
    
    private boolean isWithinRootNode(Vector3i pos) {
        return rootNodePos != null
            && pos.x >= rootNodePos.x
            && pos.y >= rootNodePos.y
            && pos.z >= rootNodePos.z
            && pos.x < rootNodePos.x + rootNode.size
            && pos.y < rootNodePos.y + rootNode.size
            && pos.z < rootNodePos.z + rootNode.size;
    }
    
    private void checkComponentDetached(Component component) {
        if(component.node != rootNode) {
            throw new RuntimeException("Detached node not actually root node. Size="+component.node.size);
        }
        if(!component.supported && !component.isTouchingAnySide()) {
            blockGroupDetached(component.getPositions(rootNodePos));
        }
    }
    
    private void blockGroupDetached(Set<Vector3i> positions) {
        //logger.info("Found detached group!");
        for(Vector3i pos : positions) {
            // for debugging purposes.
            //logger.info("Bricking "+pos);
            worldProvider.setBlock(pos, brick);
        }
    }
    
    /*@Command(shortDescription = "Print debug information relating to FallingBlocks.", helpText = "Print the location of the targeted block within the octree, and connected components of all the nodes up to size *level*.")
    public String printOctreeData(@CommandParam(value = "Level") int level) {
        if(!cameraTarget.isTargetAvailable()) {
            return "No indicated block.";
        }
        Vector3i pos = cameraTarget.getTargetBlockPosition();
        String result = "Indicated block: "+pos.x+", "+pos.y+", "+pos.z+"\n";
        Vector3i relativePos = TreeUtils.modVector(pos, ROOT_NODE_SIZE);
        Vector3i chunkPos = new Vector3i(pos).sub(relativePos);
        Node node = rootNodes.get(chunkPos);
        while(node instanceof InternalNode) {
            InternalNode iNode = (InternalNode) node;
            int octant = TreeUtils.octantOfPosition(iNode.size, relativePos);
            result += "Level "+iNode.size+", octant "+octant+", relative pos "+relativePos.toString()+"\n";
            if(iNode.size <= level) {
                result += node.getComponents().toString()+"\n";
            }
            relativePos = TreeUtils.modVector(relativePos, iNode.size/2);
            node = iNode.children[octant];
        }
        return result;
    }*/
}
