// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;
import java.util.concurrent.*;

import org.joml.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.metadata.ComponentMetadata;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.fallingblocks.updates.AdditionUpdate;
import org.terasology.fallingblocks.updates.LoadUpdate;
import org.terasology.fallingblocks.updates.RemovalUpdate;
import org.terasology.fallingblocks.updates.UnloadUpdate;
import org.terasology.fallingblocks.updates.UpdateThread;
import org.terasology.fallingblocks.updates.Update;
import org.terasology.fallingblocks.updates.ValidateUpdate;
import org.terasology.input.cameraTarget.CameraTargetSystem;
import org.terasology.logic.characters.CharacterMovementComponent;
import org.terasology.logic.common.RetainComponentsComponent;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.health.DestroyEvent;
import org.terasology.logic.health.EngineDamageTypes;
import org.terasology.logic.health.HealthComponent;
import org.terasology.logic.health.event.DoDamageEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.AABB;
import org.terasology.math.geom.Vector3i;
import org.terasology.math.geom.Vector3f;
import org.terasology.network.NetworkComponent;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.regions.BlockRegionComponent;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.event.OnChunkLoaded;
import org.terasology.world.chunks.event.BeforeChunkUnload;

@RegisterSystem(RegisterMode.AUTHORITY)
public class FallingBlockSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(FallingBlockSystem.class);

    @In
    private BlockManager blockManager;
    private Block air;
    
    @In
    private WorldProvider worldProvider;
    
    @In
    private BlockEntityRegistry blockEntityRegistry;

    @In
    private EntityManager entityManager;
    
    @In
    private PrefabManager prefabManager;
    private Prefab fallingDamageType;
    public boolean detachByMoving = true; // Ideally this would be configurable, but I don't think there's currently a working way to do module configuration like this.

    private BlockingQueue<Update> updateQueue;
    private BlockingQueue<Set<Vector3i>> detachedChainQueue;
    private Object updatingFinishedMonitor;
    private UpdateThread updateThread;

    @In
    private CameraTargetSystem cameraTarget;
    
    @Override
    public void initialise() {
        fallingDamageType = prefabManager.getPrefab("fallingBlocks:blockFallingDamage");
        air = blockManager.getBlock(BlockManager.AIR_ID);
        updateQueue = new LinkedBlockingQueue<>();
        detachedChainQueue = new LinkedBlockingQueue<>();
        updatingFinishedMonitor = new Object();
        updateThread = new UpdateThread(updateQueue, detachedChainQueue, updatingFinishedMonitor);
        updateThread.start();
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
        if (oldSolid && !newSolid) {
            updateQueue.add(new RemovalUpdate(event.getBlockPosition()));
        } else if (newSolid && !oldSolid) {
            updateQueue.add(new AdditionUpdate(event.getBlockPosition()));
        }
    }
    
    /**
     * Called once per tick.
     */
    @Override
    public void update(float delta) {
        Set<Vector3i> positions = detachedChainQueue.poll();
        while (positions != null) {
            synchronized (updatingFinishedMonitor) {
                blockGroupDetached(positions);
            }
            positions = detachedChainQueue.poll();
        }
    }
    
    @ReceiveEvent
    public void chunkLoaded(OnChunkLoaded event, EntityRef entity) {
        Vector3i chunkPos = event.getChunkPos();
        chunkPos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for (int y = 0; y < ChunkConstants.SIZE_Y; y += Tree.CHUNK_NODE_SIZE) {
            Vector3i pos = new Vector3i(chunkPos).addY(y);
            //logger.info("Loading chunk at "+pos+".");
            boolean[] chunkData = TreeUtils.extractChunkData(worldProvider, pos);
            updateQueue.add(new LoadUpdate(chunkData, pos));
        }
    }
    
    @ReceiveEvent
    public void chunkUnloaded(BeforeChunkUnload event, EntityRef entity) {
        Vector3i chunkPos = event.getChunkPos();
        chunkPos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for (int y = 0; y < ChunkConstants.SIZE_Y; y += Tree.CHUNK_NODE_SIZE) {
            Vector3i pos = new Vector3i(chunkPos).addY(y);
            //logger.info("Unloading chunk at "+pos+".");
            updateQueue.add(new UnloadUpdate(pos));
        }
    }
    
    private void blockGroupDetached(Set<Vector3i> positions) {
        //logger.info("Block group falling.");
        if (detachByMoving) {
            float totalMass = 0;
            for (Vector3i pos : positions) {
                totalMass += worldProvider.getBlock(pos).getMass();
            }

            Map<Vector3i, List<EntityRef>> allEntities = new HashMap<>();
            for (EntityRef entity : entityManager.getEntitiesWith(LocationComponent.class, HealthComponent.class)) {
                // Ideally this would take into account the size of the object too, but I don't know where to find that.
                Vector3f floatPos = new Vector3f(entity.getComponent(LocationComponent.class).getWorldPosition());
                float height = getEntityHeight(entity);
                // The use of Math.floor rather than just casting is necessary to get the correct rounding towards negative infinity behaviour.
                for (int y = (int) Math.floor(floatPos.y - height / 2); y < floatPos.y + height / 2; y++) {
                    Vector3i pos = new Vector3i(Math.round(floatPos.x), y, Math.round(floatPos.z));
                    //logger.info("Found entity subject to damage: "+floatPos+", "+pos);
                    List<EntityRef> entities = allEntities.get(pos);
                    if (entities == null) {
                        entities = new ArrayList<>();
                        allEntities.put(pos, entities);
                    }
                    entities.add(entity);
                }
            }

            int distance = -1;
            List<Vector3i> border = new ArrayList<>(positions);
            boolean contacted = false;
            while (!contacted) {
                distance += 1;
                List<Vector3i> newBorder = new ArrayList<>();
                for (Vector3i pos : border) {
                    Vector3i newPos = new Vector3i(pos).subY(1); // pos itself mustn't be mutated or it could break the HashSet.
                    if (!positions.contains(newPos)) {
                        newBorder.add(newPos);
                        if (!worldProvider.isBlockRelevant(newPos) || TreeUtils.isSolid(worldProvider.getBlock(newPos))) {
                            contacted = true;
                        }
                    }
                }
                border = newBorder;
                if (!contacted) {
                    for (Vector3i pos : border) {
                        if (allEntities.containsKey(pos)) {
                            for (EntityRef entity : allEntities.get(pos)) {
                                float damage = 0.01f * totalMass * (distance+1);
                                //logger.info("Found entity to damage. Amount="+damage);
                                entity.send(new DoDamageEvent((int) damage, EngineDamageTypes.PHYSICAL.get()));
                            }
                        }
                    }
                }
            }

            // The number of extra data slots is not directly accessible, as that system wasn't designed to be used like this.
            int extraDataCount = 0;
            Vector3i examplePos = positions.iterator().next();
            try {
                while (true) {
                    extraDataCount++;
                    worldProvider.getExtraData(extraDataCount, examplePos);
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                // This is actually the expected exit from the loop.
            }

            Map<Vector3i, Block> blockChanges = new HashMap<>();
            Set<Pair<Vector3i, int[]>> extraData = new HashSet<>();
            Map<Vector3i, Set<Component>> oldComponents = new HashMap<>();
            Set<EntityRef> blockRegionsSeen = new HashSet<>(); //blockRegions cover multiple blocks, so they may be encountered multiple times in the loop.
            for (Vector3i pos : positions) {
                Vector3i movedPos = new Vector3i(pos).subY(distance);
                blockChanges.put(movedPos, worldProvider.getBlock(pos));
                int[] localExtraData = new int[extraDataCount];
                for (int i = 0; i < extraDataCount; i++) {
                    localExtraData[i] = worldProvider.getExtraData(i, pos);
                }
                extraData.add(new Pair<>(movedPos, localExtraData));
                EntityRef oldEntity = blockEntityRegistry.getExistingEntityAt(pos);
                if (oldEntity.exists()) {
                    BlockComponent blockComponent = oldEntity.getComponent(BlockComponent.class);
                    BlockRegionComponent regionComponent = oldEntity.getComponent(BlockRegionComponent.class);
                    if (blockComponent != null) {
                        Set<Component> components = new HashSet<>();
                        Set<Class<? extends Component>> ignoredComponents = new HashSet<>();
                        ignoredComponents.add(BlockComponent.class);
                        ignoredComponents.add(LocationComponent.class);
                        ignoredComponents.add(NetworkComponent.class);
                        RetainComponentsComponent retainComponent = oldEntity.getComponent(RetainComponentsComponent.class);
                        if (retainComponent != null) {
                            ignoredComponents.addAll(retainComponent.components);
                        }
                        for (Component component : oldEntity.iterateComponents()) {
                            ComponentMetadata<? extends Component> metadata = entityManager.getComponentLibrary().getMetadata(component.getClass());
                            if (!ignoredComponents.contains(component.getClass()) && !metadata.isRetainUnalteredOnBlockChange()) {
                                components.add(entityManager.getComponentLibrary().copyWithOwnedEntities(component));
                            }
                        }
                        oldComponents.put(movedPos, components);
                    } else if (regionComponent != null && !blockRegionsSeen.contains(oldEntity)) {
                        regionComponent.region = regionComponent.region.move(new Vector3i(0, -distance, 0));
                        oldEntity.saveComponent(regionComponent);
                        blockRegionsSeen.add(oldEntity);
                    }
                }
                
                Block replacedBlock = worldProvider.getBlock(movedPos);
                if (replacedBlock.isLiquid()) {
                    Vector3i placementPos = new Vector3i(movedPos);
                    while (blockChanges.containsKey(placementPos) || (worldProvider.getBlock(placementPos) != air && !positions.contains(placementPos))) {
                        placementPos.addY(1);
                    }
                    localExtraData = new int[extraDataCount];
                    for (int i = 0; i < extraDataCount; i++) {
                        localExtraData[i] = worldProvider.getExtraData(i, movedPos);
                    }
                    blockChanges.put(placementPos, replacedBlock);
                    extraData.add(new Pair<>(placementPos, localExtraData));
                }
            }
            Map<Vector3i, Block> blockRemovals = new HashMap<>();
            for (Vector3i pos : positions) {
                blockRemovals.put(pos, air);
            }
            // Setting everything to air separately first may be necessary to properly reset the block entities in some cases where a block happens to be replaced by another block of the same type.
            worldProvider.setBlocks(blockRemovals);
            worldProvider.setBlocks(blockChanges);
            for (Pair<Vector3i, int[]> pair : extraData) {
                Vector3i pos = pair.a;
                for (int i = 0; i < extraDataCount; i++) {
                    worldProvider.setExtraData(i, pos, pair.b[i]);
                }

                if (oldComponents.containsKey(pos)) {
                    EntityRef newEntity = blockEntityRegistry.getEntityAt(pos);
                    for (Component oldComponent : oldComponents.get(pos)) {
                        if (newEntity.getComponent(oldComponent.getClass()) != null) {
                            newEntity.removeComponent(oldComponent.getClass());
                        }
                        newEntity.addComponent(oldComponent);
                    }
                }
            }
        } else {
            for (Vector3i pos : positions) {
                blockEntityRegistry.getBlockEntityAt(pos).send(new DestroyEvent(EntityRef.NULL, EntityRef.NULL, fallingDamageType));
            }
        }
    }

    public float getEntityHeight(EntityRef entity) {
        // A complete implementation of this would have to deal with CharacterMovementComponent, BoxShapeComponent, CapsuleShapeComponent, CylinderShapeComponent, HullShapeComponent and SphereShapeComponent all separately. CharacterMovementComponent is by far the most important case, so pending a rearrangement of how this works in the engine, I'll only be checking that.
        CharacterMovementComponent movementComponent = entity.getComponent(CharacterMovementComponent.class);
        if (movementComponent == null) {
            return 0.1f;
        } else {
            return movementComponent.height;
        }
    }

    @Override
    public void shutdown() {
        updateThread.interrupt();
    }
    
    @Command(shortDescription = "Print debug information relating to FallingBlocks.", helpText = "Validate the current state of the octree of FallingBlocks.")
    public String fallingBlocksDebug() {
        updateQueue.add(new ValidateUpdate());
        if (!updateThread.isAlive()) {
            return "FallingBlocks updater thread already dead.";
        }
        try {
            synchronized (updatingFinishedMonitor) { // I can't find convenient monitors separate from locks, and Java requires that the lock be acquired before the monitor is usable.
                while (updateQueue.peek() != null) {
                    updatingFinishedMonitor.wait();
                }
            }
        } catch (InterruptedException e) {
            return "Interrupted before validation was completed.";
        }
        return "Success.";
    }
    
    @Command(shortDescription = "Display the status of FallingBlocks's update thread.")
    public String fallingBlocksStatus() {
        if (!updateThread.isAlive()) {
            return "FallingBlocks update thread already dead.";
        } else {
            int updates = updateQueue.size();
            if (updates == 0) {
                return "Updating finished.";
            } else {
                return "Updates left: "+updates;
            }
        }
    }
}
