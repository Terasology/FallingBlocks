// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.node;

import java.util.*;

import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.FullChain;
import org.terasology.fallingblocks.Pair;
import org.terasology.fallingblocks.TreeUtils;
import org.terasology.math.geom.Vector3i;

/**
 * Nodes to fill in the spaces in the octree where the chunks aren't actually loaded.
 */
public class UnloadedNode extends FullNode {
    
    public UnloadedNode(int size) {
        this.size = size;
        chain = new FullChain(this, true);
        chains = new HashSet<>();
        chains.add(chain);
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        return new Pair<>(-1, null);
    }
    
    @Override
    public Pair<Node, Set<Chain>> removeBlock(Vector3i pos) {
        throw new RuntimeException("Trying to remove a block from an unloaded node.");
    }
    
    @Override
    public FullNode getSimilar(int size) {
        return new UnloadedNode(size);
    }
    
    public void validate(Stack<Integer> location) {
        chain.validate(location);
        TreeUtils.assrt(chains.contains(chain));
        TreeUtils.assrt(chains.size() == 1);
        TreeUtils.assrt(chain != null);
        TreeUtils.assrt(chain instanceof FullChain);
    }
}
