// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.calculation;

import org.joml.Vector3i;
import org.terasology.fallingblocks.calculation.node.FullNode;
import org.terasology.fallingblocks.calculation.node.SolidNode;
import org.terasology.fallingblocks.calculation.node.UnloadedNode;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * The connected component within a FullNode, filling its space.
 */
public class FullChain extends Chain {
    // subchains it not used, so it is left as null. I suspect there's some better way to arrange the classes, perhaps with a common abstract superclass like Node, that avoids having superfluous class members.
    
    public FullChain(FullNode node, boolean supported) {
        super(null, node);
        this.supported = supported;
    }
    
    @Override
    void deriveTouchingFromSubchains() {
        // There are no subchains.
    }
    
    @Override
    public void resetSupported() {
        supported = true;
    }
    
    @Override
    public void merge(Chain sibling) {
        throw new UnsupportedOperationException("FullChains are always the only chain in their node, therefore there is nothing it would be valid for them to merge with.");
    }
    
    @Override
    public boolean baseIsTouching(Chain sibling, int direction) {
        return sibling.isTouching(-direction);
    }
    
    public boolean updateTouching(Chain sibling, int direction) {
        if (baseIsTouching(sibling, direction)) {
            Chain.addTouching(this, sibling, direction);
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Tests whether the subchains are actually touching, and updates the node and
     * parent chain with the new chains this splits into, recursively splitting
     * the parent as well if necessary.
     *
     * Returns the top-level chains (i.e. most distant ancestor) resulting from this split.
     */
    @Override
    public Set<Chain> checkConnectivity() {
        // This can't actually be disconnected, so just return the result without modifying anything.
        if (parent == null) {
            Set<Chain> result = new HashSet<>();
            result.add(this);
            return result;
        } else {
            return parent.checkConnectivity();
        }
    }
    
    @Override
    public boolean isTouching(int side) {
        return true;
    }
    
    @Override
    public boolean isTouchingAnySide() {
        return true;
    }
    
    @Override
    public Set<Vector3i> getPositions(Vector3i pos) {
        Set<Vector3i> result = new HashSet<>();
        for (int x = 0; x < node.size; x++) {
            for (int y = 0; y < node.size; y++) {
                for (int z = 0; z < node.size; z++) {
                    result.add(new Vector3i(pos).add(x, y, z));
                }
            }
        }
        return result;
    }
    
    public String toString() {
        return "FCmp " + node.size;
    }
    
    @Override
    public void validate(Stack<Integer> location) {
        TreeUtils.assrt(active);
        TreeUtils.assrt(node.getChains().contains(this));
        if (parent != null) {
            int found = 0;
            for (Pair<Integer, Chain> subchain : parent.subchains()) {
                if (subchain.b == this) {
                    found++;
                }
            }
            TreeUtils.assrt(found >= 1);
            TreeUtils.assrt(found <= 1);
            TreeUtils.assrt(parent.node.size == node.size * 2);
        }
        TreeUtils.assrt(subchainId == -1);
        if (supported) {
            TreeUtils.assrt(node instanceof UnloadedNode);
        } else {
            TreeUtils.assrt(node instanceof SolidNode);
        }
        for (Pair<Integer, Chain> t : touching()) {
            TreeUtils.assrt(baseIsTouching(t.b, t.a), "direction "+t.a+" size "+node.size+" location "+location);
            TreeUtils.assrt(t.b.isTouching(this, -t.a));
            TreeUtils.assrt(node.size == t.b.node.size);
            //TreeUtils.assrt(parent == t.b.parent || parent.isTouching(t.b.parent, t.a), "size = "+node.size+", direction = "+t.a);
        }
    }
}
