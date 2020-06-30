// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.math.geom.Vector3i;

/**
 * Nodes full of solid blocks.
 */
public class SolidNode extends FullNode {
    public static final SolidNode node = new SolidNode(1);
    
    private SolidNode(int size) {
        this.size = size;
        if(size == 1) {
            component = null;
        } else {
            component = new FullComponent(this, false);
        }
        components = new HashSet();
        components.add(component);
    }
    
    public static SolidNode get(int size) {
        if(size == 1) {
            return node;
        } else {
            return new SolidNode(size);
        }
    }
    
    @Override
    public FullNode getSimilar(int size) {
        return SolidNode.get(size);
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        return new Pair(-2, null);
    }
    
    @Override
    public Pair<Node, Set<Component>> removeBlock(Vector3i pos) {
        if(size == 1) {
            return new Pair(EmptyNode.get(size), null);
        } else {
            return equivalentInternalNode().removeBlock(pos);
        }
    }
}
