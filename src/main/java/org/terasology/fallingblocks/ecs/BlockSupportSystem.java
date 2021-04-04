// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.ecs;

import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.event.ReceiveEvent;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.entitySystem.prefab.PrefabManager;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.engine.logic.console.commandSystem.annotations.Command;
import org.terasology.engine.logic.health.DestroyEvent;
import org.terasology.engine.registry.In;
import org.terasology.engine.world.BlockEntityRegistry;
import org.terasology.engine.world.OnChangedBlock;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.chunks.Chunks;
import org.terasology.engine.world.chunks.event.BeforeChunkUnload;
import org.terasology.engine.world.chunks.event.OnChunkLoaded;
import org.terasology.fallingblocks.calculation.Tree;
import org.terasology.fallingblocks.calculation.TreeUtils;
import org.terasology.fallingblocks.calculation.updates.AdditionUpdate;
import org.terasology.fallingblocks.calculation.updates.LoadUpdate;
import org.terasology.fallingblocks.calculation.updates.RemovalUpdate;
import org.terasology.fallingblocks.calculation.updates.UnloadUpdate;
import org.terasology.fallingblocks.calculation.updates.Update;
import org.terasology.fallingblocks.calculation.updates.UpdateThread;
import org.terasology.fallingblocks.calculation.updates.ValidateUpdate;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RegisterSystem(RegisterMode.AUTHORITY)
public class BlockSupportSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final Logger logger = LoggerFactory.getLogger(BlockSupportSystem.class);

    @In
    private WorldProvider worldProvider;

    @In
    private BlockEntityRegistry blockEntityRegistry;

    @In
    private PrefabManager prefabManager;
    private Prefab fallingDamageType;

    private BlockingQueue<Update> updateQueue;
    private BlockingQueue<Set<Vector3i>> detachedChainQueue;
    private Object updatingFinishedMonitor;
    private UpdateThread updateThread;

    @Override
    public void initialise() {
        fallingDamageType = prefabManager.getPrefab("fallingBlocks:blockFallingDamage");
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
        Vector3i chunkPos = new Vector3i(event.getChunkPos());
        chunkPos.mul(Chunks.SIZE_X, Chunks.SIZE_Y, Chunks.SIZE_Z);
        for (int y = 0; y < Chunks.SIZE_Y; y += Tree.CHUNK_NODE_SIZE) {
            Vector3i pos = new Vector3i(chunkPos).add(0, y, 0);
            //logger.info("Loading chunk at "+pos+".");
            boolean[] chunkData = TreeUtils.extractChunkData(worldProvider, pos);
            updateQueue.add(new LoadUpdate(chunkData, pos));
        }
    }

    @ReceiveEvent
    public void chunkUnloaded(BeforeChunkUnload event, EntityRef entity) {
        Vector3i chunkPos = new Vector3i(event.getChunkPos());
        chunkPos.mul(Chunks.SIZE_X, Chunks.SIZE_Y, Chunks.SIZE_Z);
        for (int y = 0; y < Chunks.SIZE_Y; y += Tree.CHUNK_NODE_SIZE) {
            Vector3i pos = new Vector3i(chunkPos).add(0, y, 0);
            //logger.info("Unloading chunk at "+pos+".");
            updateQueue.add(new UnloadUpdate(pos));
        }
    }

    private void blockGroupDetached(Set<Vector3i> positions) {
        logger.info("Block group falling.");
        float totalMass = 0;
        float totalLevitation = 0;
        for (Vector3i pos : positions) {
            Block block = worldProvider.getBlock(pos);
            totalMass += block.getMass();
            Optional<Prefab> blockPrefab = block.getPrefab();
            if (blockPrefab.isPresent()) { // Can't use ifPresent because the local variable totalLevitation is accessed.
                logger.info("Has prefab.");
                LevitatingBlockComponent levitation = blockPrefab.get().getComponent(LevitatingBlockComponent.class);
                if (levitation != null) {
                    logger.info("Has levitation.");
                    if (levitation.strength == 0) {
                        totalLevitation = 1f / 0f;
                    } else {
                        totalLevitation += levitation.strength;
                    }
                }
            }
        }
        logger.info("Levitation {}, mass {}", totalLevitation, totalMass);
        if (totalLevitation >= totalMass) {
            return;
        }

        BlockGroupDetachedEvent event = new BlockGroupDetachedEvent(positions, totalMass, totalLevitation);
        worldProvider.getWorldEntity().send(event);

        // A simple backup behaviour if there is no system dealing with the event.
        if (!event.isConsumed()) {
            for (Vector3i pos : positions) {
                blockEntityRegistry.getBlockEntityAt(pos).send(new DestroyEvent(EntityRef.NULL, EntityRef.NULL, fallingDamageType));
            }
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
