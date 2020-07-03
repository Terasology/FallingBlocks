// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.math.geom.Vector3i;

/**
 * Nodes full of solid blocks.
 */
public class SolidNode extends FullNode {
    public SolidNode(int size) {
        this.size = size;
        component = new FullComponent(this, false);
        components = new HashSet(1);
        components.add(component);
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
        return new Pair(-2, null);
    }
    
    @Override
    public Pair<Node, Set<Component>> removeBlock(Vector3i pos) {
        if(size == 1) {
            component.parent.subcomponents.removeIf(sc -> sc.b == component);
            component.inactivate(false);
            return new Pair(EmptyNode.get(size), component.parent.checkConnectivity());
        } else {
            return equivalentInternalNode().removeBlock(pos);
        }
    }
}
