// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.ecs;

import org.joml.Vector3f;
import org.joml.Vector3ic;
import org.terasology.engine.entitySystem.Component;
import org.terasology.engine.entitySystem.Owns;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.network.Replicate;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockRegion;

import java.util.Map;
import java.util.Set;

public class DetachedBlockGroupComponent implements Component {
    // The total mass of all of the blocks in the group
    @Replicate
    public float mass;

    // The total levitation ability of all of the blocks in the group
    @Replicate
    public float support;

    @Replicate
    public Vector3f velocity = new Vector3f();

    // The blocks, with positions relative to the position of the entity
    @Replicate
    public Map<Vector3ic, Block> blocks;

    // The components of the block entities, removed from the entities themselves do the EntityAwareWorldProvider doesn't mess with them
    @Replicate
    @Owns
    public Map<Vector3ic, Set<Component>> components;

    // The block region entities, with their BlockRegionComponents removed to deactivate them temporarily
    @Replicate
    @Owns
    public Map<EntityRef, BlockRegion> blockRegions;

    // All the extra data fields for each block
    @Replicate
    public Map<Vector3ic, int[]> extraData;

    @Replicate
    // The lower edge of the group, the only part of it that can collide with blocks
    public Set<Vector3ic> frontier;

    // 0 if the block group is still falling, otherwise, the number of ticks since it landed.
    @Replicate
    public int dying;
}
