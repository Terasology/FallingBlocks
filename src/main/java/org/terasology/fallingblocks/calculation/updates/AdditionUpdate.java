// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.calculation.updates;

import org.joml.Vector3i;
import org.terasology.fallingblocks.calculation.Chain;
import org.terasology.fallingblocks.calculation.Pair;
import org.terasology.fallingblocks.calculation.Tree;
import org.terasology.fallingblocks.calculation.node.Node;

import java.util.Collections;
import java.util.Set;

public class AdditionUpdate implements Update {
    Vector3i pos;

    public AdditionUpdate(Vector3i pos) {
        this.pos = pos;
    }

    @Override
    public Set<Chain> execute(Tree tree) {
        Pair<Node, Chain> additionResult = tree.rootNode.addBlock(new Vector3i(pos).sub(tree.rootNodePos));
        tree.rootNode = additionResult.a;
        return Collections.singleton(additionResult.b);
    }
}
