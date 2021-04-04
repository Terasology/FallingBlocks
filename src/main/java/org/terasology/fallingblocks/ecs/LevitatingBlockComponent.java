// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.ecs;

import org.terasology.engine.entitySystem.Component;

public class LevitatingBlockComponent implements Component {
    /** The total mass of blocks this can support, or 0 for unlimited. */
    public float strength = 0;
}
