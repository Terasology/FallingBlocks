// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import org.terasology.entitySystem.Component;

public class LevitatingBlockComponent implements Component {
    /** The total mass of blocks this can support, or 0 for unlimited. */
    public float strength = 0;
}
