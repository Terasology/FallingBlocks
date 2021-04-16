// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.ecs;

import gnu.trove.list.TFloatList;
import org.joml.RoundingMode;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.terasology.engine.entitySystem.Component;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.event.ReceiveEvent;
import org.terasology.engine.entitySystem.metadata.ComponentMetadata;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.engine.logic.common.RetainComponentsComponent;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.network.NetworkComponent;
import org.terasology.engine.physics.engine.PhysicsEngine;
import org.terasology.engine.registry.In;
import org.terasology.engine.rendering.logic.ChunkMeshComponent;
import org.terasology.engine.rendering.primitives.ChunkMesh;
import org.terasology.engine.rendering.primitives.ChunkTessellator;
import org.terasology.engine.world.BlockEntityRegistry;
import org.terasology.engine.world.ChunkView;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockComponent;
import org.terasology.engine.world.block.BlockManager;
import org.terasology.engine.world.block.BlockRegion;
import org.terasology.engine.world.block.regions.BlockRegionComponent;
import org.terasology.engine.world.chunks.Chunks;
import org.terasology.joml.geom.AABBf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RegisterSystem(RegisterMode.AUTHORITY)
public class FallWithAnimationSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    private static final int PERSISTENCE_TICKS = 4;

    @In
    private EntityManager entityManager;

    @In
    private WorldProvider worldProvider;

    @In
    private BlockEntityRegistry blockEntityRegistry;

    @In
    private ChunkTessellator chunkTessellator;
    private boolean headless;

    @In
    private BlockManager blockManager;
    private Block air;

    @Override
    public void initialise() {
        air = blockManager.getBlock(BlockManager.AIR_ID);
        headless = chunkTessellator == null;
        if (headless) {
            chunkTessellator = new ChunkTessellator(null);
        }
    }

    @ReceiveEvent
    public void onBlockGroupDetached(BlockGroupDetachedEvent event, EntityRef eventEntity) {
        Set<Vector3i> positions = event.blockPositions;
        Vector3f basePositionf = new Vector3f();
        BlockRegion boundingBox = new BlockRegion(BlockRegion.INVALID);
        for (Vector3i pos : positions) {
            basePositionf.add(pos.x, pos.y, pos.z);
            boundingBox.union(pos);
        }
        Vector3i basePosition = basePositionf.div(positions.size()).get(RoundingMode.HALF_DOWN, new Vector3i());
        BlockRegion chunkBoundingBox = Chunks.toChunkRegion(new BlockRegion(boundingBox).expand(1, 1, 1));
        ChunkView chunkView = worldProvider.getWorldViewAround(chunkBoundingBox);
        ChunkMesh mesh = chunkTessellator.generateEmptyMesh();
        for (Vector3i pos : positions) {
            worldProvider.getBlock(pos).getMeshGenerator().generateChunkMesh(chunkView, mesh, pos.x, pos.y, pos.z);
        }
        for (ChunkMesh.RenderType renderType : ChunkMesh.RenderType.values()) {
            TFloatList vertices = mesh.getVertexElements(renderType).vertices;
            for (int i = 0; i < vertices.size(); i += 3) {
                vertices.set(i + 0, vertices.get(i + 0) - basePosition.x);
                vertices.set(i + 1, vertices.get(i + 1) - basePosition.y);
                vertices.set(i + 2, vertices.get(i + 2) - basePosition.z);
            }
        }
        chunkTessellator.generateOptimizedBuffers(chunkView, mesh, 1, 0);
        if (!headless) {
            mesh.generateVBOs();
        }
        LocationComponent locationComponent = new LocationComponent(new Vector3f(basePosition));
        ChunkMeshComponent meshComponent = new ChunkMeshComponent(mesh, boundingBox.getBounds(new AABBf()).translate(-basePosition.x, -basePosition.y, -basePosition.z));


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

        Map<Vector3ic, Block> blocks = new HashMap<>();
        Map<Vector3ic, int[]> extraData = new HashMap<>();
        Map<Vector3ic, Set<Component>> oldComponents = new HashMap<>();
        Map<EntityRef, BlockRegion> blockRegions = new HashMap<>();
        Set<Vector3ic> frontier = new HashSet<>();
        for (Vector3i pos : positions) {
            Vector3i relativePos = pos.sub(basePosition, new Vector3i());
            blocks.put(relativePos, worldProvider.getBlock(pos));

            if (!positions.contains(pos.add(0, -1, 0, new Vector3i()))) {
                frontier.add(relativePos);
            }

            int[] localExtraData = new int[extraDataCount];
            for (int i = 0; i < extraDataCount; i++) {
                localExtraData[i] = worldProvider.getExtraData(i, pos);
            }
            extraData.put(relativePos, localExtraData);

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
                    oldComponents.put(relativePos, components);
                } else if (regionComponent != null && !blockRegions.containsKey(oldEntity)) {
                    oldEntity.removeComponent(BlockRegionComponent.class);
                    blockRegions.put(oldEntity, regionComponent.region.translate(-basePosition.x, -basePosition.y, -basePosition.z, new BlockRegion(0, 0, 0)));
                }
            }
        }

        Map<Vector3ic, Block> blockRemovals = new HashMap<>();
        for (Vector3i pos : positions) {
            blockRemovals.put(pos, air);
        }
        worldProvider.setBlocks(blockRemovals);

        DetachedBlockGroupComponent blocksComponent = new DetachedBlockGroupComponent();
        blocksComponent.blocks = blocks;
        blocksComponent.components = oldComponents;
        blocksComponent.blockRegions = blockRegions;
        blocksComponent.extraData = extraData;
        blocksComponent.frontier = frontier;
        blocksComponent.mass = event.mass;
        blocksComponent.support = event.support;
        NetworkComponent networkComponent = new NetworkComponent();
        entityManager.create(locationComponent, meshComponent, blocksComponent, networkComponent);

        event.consume();
    }

    @Override
    public void update(float delta) {
        entityLoop: for (EntityRef entity : entityManager.getEntitiesWith(DetachedBlockGroupComponent.class, LocationComponent.class)) {
            LocationComponent location = entity.getComponent(LocationComponent.class);
            DetachedBlockGroupComponent blockGroup = entity.getComponent(DetachedBlockGroupComponent.class);
            if (blockGroup.dying > 0) {
                if (blockGroup.dying > PERSISTENCE_TICKS) {
                    entity.destroy();
                } else {
                    blockGroup.dying++;
                    entity.saveComponent(blockGroup);
                }
                continue;
            }
            blockGroup.velocity.add(0, -PhysicsEngine.GRAVITY * (1 - blockGroup.support / blockGroup.mass) * delta, 0);
            Vector3fc oldPositionf = location.getWorldPosition(new Vector3f());
            Vector3ic oldPositioni = oldPositionf.get(RoundingMode.FLOOR, new Vector3i());
            Vector3fc newPositionf = blockGroup.velocity.mul(delta, new Vector3f()).add(oldPositionf);
            Vector3ic newPositioni = newPositionf.get(RoundingMode.FLOOR, new Vector3i());
            for (Vector3i possibleLandingPosition = oldPositioni.add(0, -1, 0, new Vector3i()); possibleLandingPosition.y >= newPositioni.y(); possibleLandingPosition.y--) {
                for (Vector3ic pos : blockGroup.frontier) {
                    Vector3ic worldPos = pos.add(possibleLandingPosition, new Vector3i());
                    if (!worldProvider.getBlock(worldPos).isPenetrable()) {
                        land(entity, blockGroup, possibleLandingPosition.add(0, 1, 0));
                        continue entityLoop;
                    }
                }
            }
            location.setWorldPosition(newPositionf);

            entity.saveComponent(blockGroup);
            entity.saveComponent(location);
        }
    }

    private void land(EntityRef entity, DetachedBlockGroupComponent blockGroup, Vector3ic overallPosition) {
        Map<Vector3ic, Block> blockChanges = new HashMap<>();
        for (Vector3ic relativePosition : blockGroup.blocks.keySet()) {
            blockChanges.put(relativePosition.add(overallPosition, new Vector3i()), blockGroup.blocks.get(relativePosition));
        }
        worldProvider.setBlocks(blockChanges);
        for (Vector3ic relativePosition : blockGroup.blocks.keySet()) {
            Vector3i pos = relativePosition.add(overallPosition, new Vector3i());
            int[] extraData = blockGroup.extraData.get(relativePosition);
            for (int i = 0; i < extraData.length; i++) {
                worldProvider.setExtraData(i, pos, extraData[i]);
            }

            Set<Component> oldComponents = blockGroup.components.get(relativePosition);
            if (oldComponents != null) {
                EntityRef newEntity = blockEntityRegistry.getEntityAt(pos);
                for (Component oldComponent : oldComponents) {
                    if (newEntity.getComponent(oldComponent.getClass()) != null) {
                        newEntity.removeComponent(oldComponent.getClass());
                    }
                    newEntity.addComponent(oldComponent);
                }
            }
        }

        for (EntityRef regionEntity : blockGroup.blockRegions.keySet()) {
            regionEntity.addComponent(new BlockRegionComponent(blockGroup.blockRegions.get(regionEntity).translate(overallPosition)));
        }
        // Make sure that this entity no longer owns any others, otherwise there might be odd effects.
        blockGroup.components = null;
        blockGroup.blockRegions = null;

        // Put off actually removing the entity for a few frames so that the chunk mesh can be rebuilt.
        blockGroup.dying = 1;
        entity.saveComponent(blockGroup);
        entity.updateComponent(LocationComponent.class, location -> {
            location.setWorldPosition(new Vector3f(overallPosition));
            return location;
        });
    }
}
