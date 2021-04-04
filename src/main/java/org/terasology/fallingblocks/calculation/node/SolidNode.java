// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.calculation.node;

import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.fallingblocks.calculation.Chain;
import org.terasology.fallingblocks.calculation.FullChain;
import org.terasology.fallingblocks.calculation.Pair;
import org.terasology.fallingblocks.calculation.Tree;

import java.util.HashSet;
import java.util.Set;

/**
 * Nodes full of solid blocks.
 */
public class SolidNode extends FullNode {
    private static final Logger logger = LoggerFactory.getLogger(SolidNode.class);

    public SolidNode(int size, Tree tree) {
        this.size = size;
        this.tree = tree;
        chain = new FullChain(this, false);
        chains = new HashSet<>(1);
        chains.add(chain);
    }
    
    @Override
    public FullNode getSimilar(int size) {
        return new SolidNode(size, tree);
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        return new Pair<>(-2, null);
    }
    
    @Override
    public Pair<Node, Set<Chain>> removeBlock(Vector3i pos) {
        if (size == 1) {
            chain.parent.removeSubchain(chain);
            chain.inactivate(false);
            return new Pair<>(EmptyNode.get(size, tree), chain.parent.checkConnectivity());
        } else {
            return equivalentInternalNode().removeBlock(pos);
        }
    }
}
