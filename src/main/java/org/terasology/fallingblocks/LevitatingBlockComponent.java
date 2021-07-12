// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import org.terasology.gestalt.entitysystem.component.Component;

public class LevitatingBlockComponent implements Component<LevitatingBlockComponent> {
    /**
     * The total mass of blocks this can support, or 0 for unlimited.
     */
    public float strength = 0;

    @Override
    public void copy(LevitatingBlockComponent other) {
        this.strength = other.strength;
    }
}
