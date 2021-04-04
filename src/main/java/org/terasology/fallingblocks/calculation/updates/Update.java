// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.calculation.updates;

import java.util.*;

import org.terasology.fallingblocks.calculation.Chain;
import org.terasology.fallingblocks.calculation.Tree;

/**
 * A message from the main thread to the UpdateThread to modify the octree somehow.
 */
public interface Update {
    public Set<Chain> execute(Tree tree);
}
