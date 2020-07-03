// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.node;

import java.util.*;

import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.FullChain;
import org.terasology.fallingblocks.Pair;
import org.terasology.math.geom.Vector3i;

/**
 * Nodes full of solid blocks.
 */
public class SolidNode extends FullNode {
    public SolidNode(int size) {
        this.size = size;
        chain = new FullChain(this, false);
        chains = new HashSet<>(1);
        chains.add(chain);
    }
    
    @Override
    public FullNode getSimilar(int size) {
        return new SolidNode(size);
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
            chain.parent.subchains.removeIf(sc -> sc.b == chain);
            chain.inactivate(false);
            return new Pair<>(EmptyNode.get(size), chain.parent.checkConnectivity());
        } else {
            return equivalentInternalNode().removeBlock(pos);
        }
    }
}
