// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;
import java.util.concurrent.*;

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
import org.terasology.fallingblocks.node.Node;
import org.terasology.fallingblocks.updates.AdditionUpdate;
import org.terasology.fallingblocks.updates.LoadUpdate;
import org.terasology.fallingblocks.updates.RemovalUpdate;
import org.terasology.fallingblocks.updates.UnloadUpdate;
import org.terasology.fallingblocks.updates.UpdateThread;
import org.terasology.fallingblocks.updates.Update;
import org.terasology.fallingblocks.updates.ValidateUpdate;
import org.terasology.input.cameraTarget.CameraTargetSystem;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.health.DestroyEvent;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.WorldProvider;
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

    private BlockingQueue<Update> updateQueue;
    private BlockingQueue<Set<Vector3i>> detachedChainQueue;
    private Object updatingFinishedMonitor;
    private UpdateThread updateThread;

    @In
    private CameraTargetSystem cameraTarget;
    
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
            blockGroupDetached(positions);
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
        for (Vector3i pos : positions) {
            blockEntityRegistry.getBlockEntityAt(pos).send(new DestroyEvent(EntityRef.NULL, EntityRef.NULL, fallingDamageType));
        }
    }

    @Override
    public void shutdown() {
        updateThread.interrupt();
    }
    
    @Command(shortDescription = "Print debug information relating to FallingBlocks.", helpText = "Validate the current state of the octree of FallingBlocks.")
    public String fallingBlocksDebug() {
        updateQueue.add(new ValidateUpdate());
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
}
