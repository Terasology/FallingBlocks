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
    
    private Map<Vector3i, Node> rootNodes;
    private static int ROOT_NODE_SIZE = ChunkConstants.SIZE_X; //This actually needs to be the minimum of SIZE_X, SIZE_Y and SIZE_Z, but it's assumed later that SIZE_Y >= SIZE_X = SIZE_Z anyway.

    @In
    private CameraTargetSystem cameraTarget;
    
    @Override
    public void initialise() {
        rootNodes = new HashMap<>();
        brick = blockManager.getBlock("coreAssets:brick");
        
        /*
        int[] directions = new int[]{4,2,1,-1,-2,-4};
        for(int oct1 = 0; oct1<8; oct1++) {
            logger.info("bitCount("+oct1+") = "+Integer.bitCount(oct1));
            for(int side : directions) {
                logger.info("isOctantOnSide("+oct1+","+side+") = "+Node.isOctantOnSide(oct1, side));
            }
            for(int oct2 = 0; oct2 < 8; oct2++) {
                logger.info("adjacent("+oct1+","+oct2+") = "+Node.isAdjacent(oct1, oct2));
            }
        }*/
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
        boolean oldSolid = Node.isSolid(event.getOldType());
        boolean newSolid = Node.isSolid(event.getNewType());
        if(oldSolid != newSolid) {
            Vector3i pos = event.getBlockPosition();
        Vector3i relativePos = Node.modVector(pos, ROOT_NODE_SIZE);
        Vector3i chunkPos = new Vector3i(pos).sub(relativePos);
            Node rootNode = rootNodes.get(chunkPos);
            Collection<Component> updatedComponents;
            if(rootNode == null) { //necessarily newSolid == true.
                rootNode = Node.buildSingletonNode(ROOT_NODE_SIZE, relativePos);
                updatedComponents = rootNode.getComponents();
            } else if(newSolid) {
                updatedComponents = new HashSet();
                updatedComponents.add(rootNode.addBlock(relativePos).a);
            } else {
                Pair<Node, Pair<Set<Component>, Component>> result = rootNode.removeBlock(relativePos);
                rootNode = result.a;
                updatedComponents = result.b.a;
            }
            
            if(rootNode != null) {
                updatedComponents.stream()
                    .filter((Component c) -> !c.isTouchingAnySide())
                    .map((Component c) -> c.getPositions(chunkPos, ROOT_NODE_SIZE))
                    .forEach(this::blockGroupDetached);
            }
        }
    }
    
    @ReceiveEvent
    public void chunkLoaded(OnChunkLoaded event, EntityRef entity) {
        Vector3i pos = event.getChunkPos();
        pos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for(int y=0; y<ChunkConstants.SIZE_Y; y += ROOT_NODE_SIZE) {
            Node node = Node.buildNode(worldProvider, ROOT_NODE_SIZE, pos.x, pos.y + y, pos.z);
            if(node != null) {
                Vector3i nodePos = new Vector3i(pos).addY(y);
                logger.info("Creating new root node at "+nodePos);
                rootNodes.put(nodePos, node);
                node.getInternalPositions(nodePos).forEach(this::blockGroupDetached);
            }
        }
    }
    
    @ReceiveEvent
    public void chunkUnloaded(BeforeChunkUnload event, EntityRef entity) {
        Vector3i pos = event.getChunkPos();
        pos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for(int y=0; y<ChunkConstants.SIZE_Y; y += ROOT_NODE_SIZE) {
            rootNodes.remove(new Vector3i(pos).addY(y));
        }
    }
    
    private void blockGroupDetached(Set<Vector3i> positions) {
        logger.info("Found detached group!");
        for(Vector3i pos : positions) {
            // for debugging purposes.
            logger.info("Bricking "+pos);
            worldProvider.setBlock(pos, brick);
        }
    }
    
    @Command(shortDescription = "Print debug information relating to FallingBlocks.", helpText = "Print the location of the targeted block within the octree, and connected components of all the nodes up to size *level*.")
    public String printOctreeData(@CommandParam(value = "Level") int level) {
        if(!cameraTarget.isTargetAvailable()) {
            return "No indicated block.";
        }
        Vector3i pos = cameraTarget.getTargetBlockPosition();
        String result = "Indicated block: "+pos.x+", "+pos.y+", "+pos.z+"\n";
        Vector3i relativePos = Node.modVector(pos, ROOT_NODE_SIZE);
        Vector3i chunkPos = new Vector3i(pos).sub(relativePos);
        Node node = rootNodes.get(chunkPos);
        while(node instanceof InternalNode) {
            InternalNode iNode = (InternalNode) node;
            int octant = Node.octantOfPosition(iNode.size, relativePos);
            result += "Level "+iNode.size+", octant "+octant+", relative pos "+relativePos.toString()+"\n";
            if(iNode.size <= level) {
                result += node.getComponents().toString()+"\n";
            }
            relativePos = Node.modVector(relativePos, iNode.size/2);
            node = iNode.children[octant];
        }
        return result;
    }
}
