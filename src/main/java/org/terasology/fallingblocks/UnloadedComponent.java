// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.math.geom.Vector3i;

/**
 * The connected component within an UnloadedNode, filling its space.
 */
public class UnloadedComponent extends Component {
    // subcomponents it not used, so it is left as null. I suspect there's some better way to arrange the classes, perhaps with a common abstract superclass like Node, that avoids having superfluous class members.
    
    public UnloadedComponent(UnloadedNode node) {
        super(null, null, node);
        supported = true;
    }
    
    @Override
    void deriveTouchingFromSubcomponents() {
        // There are no subcomponents.
    }
    
    @Override
    public void resetSupported() {
        supported = true;
    }
    
    @Override
    public void merge(Component sibling) {
        throw new UnsupportedOperationException("UnloadedComponents are always the only component in their node, therefore there is nothing it would be valid for them to merge with.");
    }
    
    @Override
    public boolean baseIsTouching(Component sibling, int direction) {
        return sibling.isTouching(-direction);
    }
    
    public boolean updateTouching(Component sibling, int direction) {
        if(baseIsTouching(sibling, direction)) {
            touching.add(new Pair(direction, sibling));
            sibling.touching.add(new Pair(-direction, this));
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Tests whether the subcomponents are actually touching, and updates the node and
     * parent component with the new components this splits into, recursively splitting
     * the parent as well if necessary.
     *
     * Returns the top-level components (i.e. most distant ancestor) resulting from this split.
     */
    @Override
    public Set<Component> checkConnectivity() {
        // An UnloadedNode can't be the root node therefore this must have a parent. Also, it can't be disconnected.
        return parent.checkConnectivity();
    }
    
    @Override
    public boolean isTouching(int side) {
        return true;
    }
    
    @Override
    public boolean isTouchingAnySide() {
        return true;
    }
    
    // I don't think this overridden version of getPositions will actually be used, but it seems pretty harmless.
    @Override
    public Set<Vector3i> getPositions(Vector3i pos) {
        Set<Vector3i> result = new HashSet();
        for(int x=0; x<node.size; x++) {
            for(int y=0; y<node.size; y++) {
                for(int z=0; z<node.size; z++) {
                    result.add(new Vector3i(pos).add(x,y,z));
                }
            }
        }
        return result;
    }
    
    public String toString() {
        return "UCmp "+node.size;
    }
    
    @Override
    public void validate(Stack<Integer> location) {
        TreeUtils.assrt(active);
        TreeUtils.assrt(node.getComponents().contains(this));
        if(parent != null) {
            int found = 0;
            for(Pair<Integer, Component> subcomponent : parent.subcomponents) {
                if(subcomponent.b == this) {
                    found++;
                }
            }
            TreeUtils.assrt(found >= 1);
            TreeUtils.assrt(found <= 1);
            TreeUtils.assrt(parent.node.size == node.size * 2);
        }
        TreeUtils.assrt(subcomponents == null);
        TreeUtils.assrt(supported);
        for(Pair<Integer, Component> t : touching) {
            TreeUtils.assrt(baseIsTouching(t.b, t.a), "direction "+t.a+" size "+node.size+" location "+location);
            TreeUtils.assrt(t.b.isTouching(this, -t.a));
            TreeUtils.assrt(node.size == t.b.node.size);
            //TreeUtils.assrt(parent == t.b.parent || parent.isTouching(t.b.parent, t.a), "size = "+node.size+", direction = "+t.a);
        }
    }
}
