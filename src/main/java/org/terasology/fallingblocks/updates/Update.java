// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.updates;

import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.Tree;

import java.util.Set;

/**
 * A message from the main thread to the UpdateThread to modify the octree somehow.
 */
public interface Update {
    Set<Chain> execute(Tree tree);
}
