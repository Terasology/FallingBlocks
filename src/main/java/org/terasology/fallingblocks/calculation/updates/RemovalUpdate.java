// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.calculation.updates;

import org.joml.Vector3i;
import org.terasology.fallingblocks.calculation.Chain;
import org.terasology.fallingblocks.calculation.Tree;

import java.util.Set;

public class RemovalUpdate implements Update{
    Vector3i pos;

    public RemovalUpdate(Vector3i pos) {
        this.pos = pos;
    }

    @Override
    public Set<Chain> execute(Tree tree) {
        return tree.rootNode.removeBlock(new Vector3i(pos).sub(tree.rootNodePos)).b;
    }
}
