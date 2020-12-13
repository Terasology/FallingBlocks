// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.fallingblocks.node.InternalNode;
import org.terasology.fallingblocks.node.Node;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

/**
 * A connected component of solid blocks within an octree node.
 */
public class Chain {
    private static final Logger logger = LoggerFactory.getLogger(Chain.class);

    public final int subchainId;
    public final int touchingId;
    public Chain parent;
    public final Node node;
    public final Tree tree;
    public boolean supported; //Does this chain contain any unloaded Chains (which are assumed to be supported)?
    boolean active = true; //Is this chain currently part of the overall octree structure?
    
    public Chain(int childIndex, Chain childChain, InternalNode node) {
        this.tree = node.tree;
        subchainId = tree.subchains.allocate();
        touchingId = tree.touching.allocate();
        addSubchain(childIndex, childChain);
        deriveTouchingFromSubchains();
        this.node = node;
        resetSupported();
    }
    
    public Chain(Set<Pair<Integer, Chain>> subchains, Node node) {
        this.tree = node.tree;
        touchingId = tree.touching.allocate();
        if (subchains != null) {
            subchainId = tree.subchains.allocate();
            for (Pair<Integer, Chain> subchain : subchains) {
                addSubchain(subchain.a, subchain.b);
            }
        } else {
            subchainId = -1;
        }
        deriveTouchingFromSubchains();
        this.node = node;
        resetSupported();
    }
    
    /**
     * Adds those chains that it can be derived that ke'a touches this from
     * the subchains. This may miss some, in the cases that this is touching
     * a FullChain, which doesn't have subchains.
     */
    void deriveTouchingFromSubchains() {
        for (Pair<Integer, Chain> subchain : subchains()) {
            int octant = subchain.a;
            Chain child = subchain.b;
            for (Pair<Integer, Chain> childTouching : child.touching()) {
                int side = childTouching.a;
                Chain newTouching = childTouching.b.parent;
                if (TreeUtils.isOctantOnSide(octant, side)) {
                    addTouching(this, newTouching, side);
                }
            }
        }
    }
    
    public void resetSupported() {
        for (Pair<Integer, Chain> sc : subchains()) {
            if (sc.b.supported) {
                supported = true;
                return;
            }
        }
        supported = false;
    }
    
    public void merge(Chain sibling) {
        TreeUtils.assrt(active);
        TreeUtils.assrt(sibling.isActive());
        TreeUtils.assrt(sibling.parent == null || sibling.parent.isActive());
        TreeUtils.assrt(sibling != this);
        TreeUtils.assrt(sibling.node == node);
        for (Pair<Integer, Chain> t : sibling.touching()) {
            t.b.removeTouching(sibling);
            if (!isTouching(t.b, t.a)) {
                addTouching(this, t.b, t.a);
            }
        }
        for (Pair<Integer, Chain> sc : sibling.subchains()) {
            addSubchain(sc.a, sc.b);
        }
        if (sibling.parent != null) {
            int octant = sibling.parent.removeSubchain(sibling);
            if (parent == null) {
                sibling.parent.addSubchain(octant, this);
            }
        }
        if (sibling.parent != parent && sibling.parent != null && parent != null) {
            parent.merge(sibling.parent);
        }
        if (supported != sibling.supported && parent != null) {
            for (Chain ancestor = this; ancestor.parent != null; ancestor = ancestor.parent) {
                ancestor.supported = true;
            }
        }
        supported = supported || sibling.supported;
        sibling.inactivate(false);
    }
    
    public void replaceWith(Chain replacement) {
        for (Pair<Integer, Chain> sc : subchains()) {
            replacement.addSubchain(sc.a, sc.b);
        }
        for (Pair<Integer, Chain> t : touching()) {
            t.b.removeTouching(this);
            if (replacement.baseIsTouching(t.b, t.a)) {
                addTouching(replacement, t.b, t.a);
            }
        }
        if (parent != null) {
            for (int i = 0; i < parent.numSubchains(); i++) {
                if (parent.getSubchain(i) == this) {
                    tree.subchains.setB(parent.subchainId, i, replacement);
                    replacement.parent = parent;
                }
            }
            if (replacement.supported != supported) {
                for (Chain ancestor = this; ancestor.parent != null; ancestor = ancestor.parent) {
                    ancestor.parent.resetSupported();
                }
            }
        }
        releaseId();
        active = false;
        replacement.validate(new Stack<>());
    }
    
    /**
     * Work out whether this is touching the given chain without just
     * getting the result from the cached set (this.touching).
     */
    public boolean baseIsTouching(Chain sibling, int direction) {
        if (sibling instanceof FullChain) {
            return sibling.baseIsTouching(this, -direction);
        }
        for (Pair<Integer, Chain> subchain1 : subchains()) {
            for (Pair<Integer, Chain> subchain2 : sibling.subchains()) {
                int adjacency = TreeUtils.isAdjacent(subchain1.a, subchain2.a, direction);
                if (adjacency == 0) {
                    continue;
                }
                if (subchain1.b.isTouching(subchain2.b, adjacency)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public boolean isTouching(Chain sibling, int direction) {
        if (direction == 0) {
            return baseIsTouching(sibling, direction);
        } else {
            for (Pair<Integer, Chain> t : touching()) {
                if (t.b == sibling) {
                    return true;
                }
            }
            return false;
        }
    }
    
    public boolean updateTouching(Chain sibling, int direction) {
        if (sibling instanceof FullChain) {
            return sibling.updateTouching(this, -direction);
        }
        boolean result = false;
        for (Pair<Integer, Chain> subchain1 : subchains()) {
            for (Pair<Integer, Chain> subchain2 : sibling.subchains()) {
                int adjacency = TreeUtils.isAdjacent(subchain1.a, subchain2.a, direction);

                if (adjacency != 0 && subchain1.b.updateTouching(subchain2.b, adjacency)) {
                    result = true;
                    // The test has side-effects, so the loops must continue.
                }
            }
        }
        if (result && direction != 0) {
            addTouching(this, sibling, direction);
        }
        return result;
    }
    
    /**
     * Tests whether the subchains are actually touching, and updates the node and
     * parent chain with the new chains this splits into, recursively splitting
     * the parent as well if necessary.
     *
     * Returns the top-level chains (i.e. most distant ancestor) resulting from this split.
     */
    public Set<Chain> checkConnectivity() {
        Set<Chain> result = new HashSet<>();
        Set<Pair<Integer, Chain>> unprocessedSubchains = new HashSet<>();
        for (Pair<Integer, Chain> sc : subchains()) {
            unprocessedSubchains.add(sc);
        }
        Stack<Pair<Integer, Chain>> edge = new Stack<>();
        while (!unprocessedSubchains.isEmpty()) {
            Set<Pair<Integer, Chain>> connectedComponent = new HashSet<>();
            Pair<Integer, Chain> start = unprocessedSubchains.iterator().next();
            unprocessedSubchains.remove(start);
            edge.push(start);
            while (!edge.empty()) {
                Pair<Integer, Chain> current = edge.pop();
                connectedComponent.add(current);
                Iterator<Pair<Integer, Chain>> others = unprocessedSubchains.iterator();
                while (others.hasNext()) {
                    Pair<Integer, Chain> other = others.next();
                    int direction = TreeUtils.isAdjacent(current.a, other.a);
                    if (direction != 0 && current.b.isTouching(other.b, direction)) {
                        edge.push(other);
                        others.remove();
                    }
                }
            }
            if (result.isEmpty() && unprocessedSubchains.isEmpty()) {
                result.add(this);
            } else {
                Chain fragment = new Chain(connectedComponent, node);
                result.add(fragment);
                for (Pair<Integer, Chain> touchingThis : touching()) {
                    if (fragment.baseIsTouching(touchingThis.b, touchingThis.a)) {
                        addTouching(fragment, touchingThis.b, touchingThis.a);
                    }
                }
            }
        }
        if (result.size() != 1 && parent != null) {
            int thisOctant = parent.removeSubchain(this);
            for (Chain fragment : result) {
                parent.addSubchain(thisOctant, fragment);
            }
        }
        if (result.size() != 1) {
            TreeUtils.assrt(!result.contains(this));
            inactivate(false);
            node.getChains().addAll(result);
        } else {
            resetSupported();
            int i = 0;
            while (i < numTouching()) {
                Chain touching = getTouching(i);
                if (!baseIsTouching(touching, getTouchingDirection(i))) {
                    removeTouching(touching);
                    touching.removeTouching(this);
                } else {
                    i++;
                }
            }
        }
        if (parent == null) {
            return result;
        } else {
            return parent.checkConnectivity();
        }
    }
            
    
    public boolean isTouching(int side) {
        for (Pair<Integer, Chain> subchain : subchains()) {
            if (TreeUtils.isOctantOnSide(subchain.a, side) && subchain.b.isTouching(side)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isTouchingAnySide() {
        return isTouching(4)
            || isTouching(2)
            || isTouching(1)
            || isTouching(-1)
            || isTouching(-2)
            || isTouching(-4);
    }
    
    public Set<Vector3i> getPositions(Vector3i pos) {
        Set<Vector3i> result = new HashSet<>();
        int size = node.size;
        for (Pair<Integer, Chain> subchain : subchains()) {
            Vector3i subPosition = new Vector3i(pos);
            int octant = subchain.a;
            subPosition.add(TreeUtils.isOctantOnSide(octant, 4) ? size/2 : 0,
                            TreeUtils.isOctantOnSide(octant, 2) ? size/2 : 0,
                            TreeUtils.isOctantOnSide(octant, 1) ? size/2 : 0);
            result.addAll(subchain.b.getPositions(subPosition));
        }
        return result;
    }
    
    public void inactivate(boolean removeAncestors) {
        node.getChains().remove(this);
        for (Pair<Integer, Chain> t : touching()) {
            t.b.removeTouching(this);
        }
        for (Pair<Integer, Chain> sc : subchains()) {
            if (sc.b.parent == this && sc.b.isActive()) {
                sc.b.inactivate(false);
            }
        }
        if (removeAncestors && parent != null) {
            parent.removeSubchain(this);
            if (parent.numSubchains() == 0) {
                parent.inactivate(true);
            }
        }
        releaseId();
        active = false;
    }

    private void releaseId() {
        tree.subchains.remove(subchainId);
        tree.touching.remove(touchingId);
    }
    
    public boolean isActive() {
        return active;
    }

    public Iterable<Pair<Integer, Chain>> subchains() {
        return tree.subchains.iterator(subchainId);
    }

    public int numSubchains() {
        if (subchainId == -1) {
            return 0;
        } else {
            return tree.subchains.getSize(subchainId);
        }
    }

    public int getSubchainOctant(int i) {
        return tree.subchains.getA(subchainId, i);
    }

    public Chain getSubchain(int i) {
        return tree.subchains.getB(subchainId, i);
    }

    public void addSubchain(int octant, Chain child) {
        TreeUtils.assrt(isActive());
        if (child.parent == this) {
            return;
        }
        int size = tree.subchains.expand(subchainId, 1);
        tree.subchains.set(subchainId, size - 1, octant, child);
        child.parent = this;
    }

    /**
     * @return the corresponding octant
     */
    public int removeSubchain(Chain child) {
        int i = 0;
        for (Pair<Integer, Chain> t : subchains()) {
            if (t.b == child) {
                tree.subchains.remove(subchainId, i);
                return t.a;
            } else {
                i++;
            }
        }
        return -1;
    }

    public Iterable<Pair<Integer, Chain>> touching() {
        return tree.touching.iterator(touchingId);
    }

    public int numTouching() {
        return tree.touching.getSize(touchingId);
    }

    public int getTouchingDirection(int i) {
        return tree.touching.getA(touchingId, i);
    }

    public Chain getTouching(int i) {
        return tree.touching.getB(touchingId, i);
    }

    public static void addTouching(Chain a, Chain b, int direction) {
        TreeUtils.assrt(b != null);
        TreeUtils.assrt(a != b);
        if (a.isTouching(b, direction)) {
            return;
        }
        a.tree.touching.set(a.touchingId, a.tree.touching.expand(a.touchingId, 1) - 1,  direction, b);
        b.tree.touching.set(b.touchingId, b.tree.touching.expand(b.touchingId, 1) - 1,  -direction, a);
    }

    public void removeTouching(Chain touching) {
        int i = 0;
        for (Pair<Integer, Chain> t : touching()) {
            if (t.b == touching) {
                tree.touching.remove(touchingId, i);
                return;
            } else {
                i++;
            }
        }
    }
    
    public String toString() {
        String result = "Cmp "+node.size+" [";
        boolean first = true;
        for (Pair<Integer, Chain> subchain : subchains()) {
            if (first) {
                first = false;
            } else {
                result += ", ";
            }
            result += subchain.toString();
        }
        return result + "]";
    }

    public void validate(Stack<Integer> location) {
        TreeUtils.assrt(node.getChains().contains(this));
        TreeUtils.assrt(active);
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
        TreeUtils.assrt(numSubchains() > 0);
        for (Pair<Integer, Chain> subchain : subchains()) {
            TreeUtils.assrt(subchain.b.parent == this);
            TreeUtils.assrt(subchain.b.isActive()); // In theory, this is covered by the next test (if it's in a node's chain list, it'll be validated), but it looks like it's failing.
            TreeUtils.assrt(((InternalNode) node).children[subchain.a].getChains().contains(subchain.b));
        }
        boolean prevSupported = supported;
        resetSupported();
        TreeUtils.assrt(prevSupported || !supported, "size "+node.size);
        TreeUtils.assrt(!prevSupported || supported, "size "+node.size);
        // Checking that the subchains actually do all touch would be good, but also complicated.
        for (Pair<Integer, Chain> t : touching()) {
            TreeUtils.assrt(baseIsTouching(t.b, t.a), "direction "+t.a+" size "+node.size+" location "+location);
            TreeUtils.assrt(t.b.isTouching(this, -t.a));
            TreeUtils.assrt(node.size == t.b.node.size);
            //TreeUtils.assrt(parent == t.b.parent || parent.isTouching(t.b.parent, t.a), "size = "+node.size+", direction = "+t.a);
        }
    }
    
    /**
     * Assert that, for two adjacent chains, all the appropriate subchains
     * (and sub-sub-chains ect.) are touching.
     */
    public static void validateTouching(Chain chain1, Chain chain2, int direction) {
        if (chain1.baseIsTouching(chain2, direction)) {
            TreeUtils.assrt(chain1.isTouching(chain2, direction), "size = "+chain1.node.size+" direction = "+direction);
        }
        
        for (Pair<Integer, Chain> sc1 : chain1.subchains()) {
            for (Pair<Integer, Chain> sc2 : chain2.subchains()) {
                if (TreeUtils.isAdjacent(sc1.a, sc2.a, direction) != 0) {
                    validateTouching(sc1.b, sc2.b, direction);
                }
            }
        }
    }
}
