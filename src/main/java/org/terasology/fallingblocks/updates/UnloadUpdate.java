// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.updates;

import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.FullChain;
import org.terasology.fallingblocks.Pair;
import org.terasology.fallingblocks.Tree;
import org.terasology.fallingblocks.TreeUtils;
import org.terasology.fallingblocks.node.Node;
import org.terasology.math.geom.Vector3i;

import java.util.*;

public class UnloadUpdate implements Update {
    Vector3i pos;

    public UnloadUpdate(Vector3i pos) {
        this.pos = pos;
    }

    @Override
    public Set<Chain> execute(Tree tree) {
        tree.rootNode = tree.rootNode.removeChunk(pos.sub(tree.rootNodePos), Tree.CHUNK_NODE_SIZE).a;
        Pair<Integer, Node> shrinking = tree.rootNode.canShrink();
        if (shrinking.a >= 0) {
            //logger.info("Shrinking root node to octant "+shrinking.a+", size "+shrinking.b.size+".");
            Set<Chain> oldChains = new HashSet<>(tree.rootNode.getChains());
            tree.rootNode = shrinking.b;
            for (Chain chain : tree.rootNode.getChains()) {
                TreeUtils.assrt(!(chain.parent instanceof FullChain));
                chain.parent = null;
            }
            for (Chain chain : oldChains) {
                chain.inactivate(false); // It has to be done in this order so as to not also inactivate the child chains.
            }
            tree.rootNodePos.add(TreeUtils.octantVector(shrinking.a, tree.rootNode.size));
        } else if (shrinking.a == -1) {
            tree.rootNode = null;
            tree.rootNodePos = null;
        }
        return Collections.emptySet();
    }
}
