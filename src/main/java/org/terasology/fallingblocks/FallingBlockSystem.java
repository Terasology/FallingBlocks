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
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.input.cameraTarget.CameraTargetSystem;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.logic.health.DestroyEvent;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.event.OnChunkLoaded;
import org.terasology.world.chunks.event.BeforeChunkUnload;

@RegisterSystem(RegisterMode.AUTHORITY)
public class FallingBlockSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(FallingBlockSystem.class);
    
    @In
    private WorldProvider worldProvider;
    
    @In
    private BlockEntityRegistry blockEntityRegistry;
    
    @In
    private PrefabManager prefabManager;
    private Prefab fallingDamageType;
    
    private Node rootNode = null;
    private Vector3i rootNodePos = null;
    private static int ROOT_NODE_SIZE = ChunkConstants.SIZE_X; //This actually needs to be the minimum of SIZE_X, SIZE_Y and SIZE_Z, but it's assumed later that SIZE_Y >= SIZE_X = SIZE_Z anyway.
    private static final int ROOT_OFFSET = 0xAAAAAAA0; //The octree structure divides at different levels in fixed locations. This constant is chosen so that, as far as possible, the highest-level divisions are far from the origin, so that the root node isn't likely to need to be very large just because the relevant region overlaps one of the divisions.
    
    private Set<Vector3i> additionQueue;
    private Set<Vector3i> removalQueue;

    @In
    private CameraTargetSystem cameraTarget;
    
    @Override
    public void initialise() {
        additionQueue = new HashSet();
        removalQueue = new HashSet();
        fallingDamageType = prefabManager.getPrefab("fallingBlocks:blockFallingDamage");
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
        if(oldSolid && !newSolid) {
            removalQueue.add(event.getBlockPosition());
        } else if (newSolid && !oldSolid) {
            additionQueue.add(event.getBlockPosition());
        }
    }
    
    /**
     * Called once per tick.
     */
    @Override
    public void update(float delta) {
        if(!removalQueue.isEmpty() || !additionQueue.isEmpty()) {
            // If a block group falls, that causes more removals, but those need to be dealt with separately or it could get messy.
            Set<Vector3i> oldRemovals = removalQueue;
            removalQueue = new HashSet();
            Set<Vector3i> oldAdditions = additionQueue;
            additionQueue = new HashSet();
            
            // All the top-level components at risk of being detached by this update.
            Set<Component> updatedComponents = new HashSet();
            
            // Dealing with additions first is probably a bit more efficient.
            for(Vector3i worldPos : oldAdditions) {
                if(!worldProvider.isBlockRelevant(worldPos)) {
                    continue;
                }
                Vector3i pos = new Vector3i(worldPos).sub(rootNodePos);
                updatedComponents.add(rootNode.addBlock(pos).a);
            }
            for(Vector3i worldPos : oldRemovals) {
                if(!worldProvider.isBlockRelevant(worldPos)) {
                    continue;
                }
                Vector3i pos = new Vector3i(worldPos).sub(rootNodePos);
                updatedComponents.addAll(rootNode.removeBlock(pos).b);
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
                //logger.info("Shrinking root node to octant "+shrinking.a+", size "+shrinking.b.size+".");
                rootNode = shrinking.b;
                if(rootNode != null) {
                    for(Component component : rootNode.getComponents()) {
                        component.parent.inactivate();
                        component.parent = null;
                    }
                }
                rootNodePos.add(TreeUtils.octantVector(shrinking.a, rootNode.size));
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
        if(component.isActive() && !component.supported && !component.isTouchingAnySide()) {
            blockGroupDetached(component.getPositions(rootNodePos));
        }
    }
    
    private void blockGroupDetached(Set<Vector3i> positions) {
        //logger.info("Block group falling.");
        for(Vector3i pos : positions) {
            blockEntityRegistry.getBlockEntityAt(pos).send(new DestroyEvent(EntityRef.NULL, EntityRef.NULL, fallingDamageType));
        }
    }
    
    @Command(shortDescription = "Print debug information relating to FallingBlocks.", helpText = "Validate the current state of the octree of FallingBlocks.")
    public String fallingBlocksDebug() {
        rootNode.validate();
        return "Success.";
    }
}
