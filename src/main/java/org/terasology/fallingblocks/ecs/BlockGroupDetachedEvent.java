// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.ecs;

import org.joml.Vector3i;
import org.terasology.engine.entitySystem.event.AbstractConsumableEvent;

import java.util.Set;

public class BlockGroupDetachedEvent extends AbstractConsumableEvent {
    public Set<Vector3i> blockPositions;
    public float mass;
    public float support;

    public BlockGroupDetachedEvent(Set<Vector3i> blockPositions, float mass, float support) {
        this.blockPositions = blockPositions;
        this.mass = mass;
        this.support = support;
    }
}
