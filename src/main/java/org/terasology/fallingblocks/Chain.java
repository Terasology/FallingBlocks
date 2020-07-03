// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.fallingblocks.node.InternalNode;
import org.terasology.fallingblocks.node.Node;
import org.terasology.math.geom.Vector3i;

/**
 * A connected component of solid blocks within an octree node.
 */
public class Chain {
    // Pairs of the index of a child node of this chain's node, and a chain within that child which is part of this.
    public Set<Pair<Integer, Chain>> subchains;
    // Pairs of a chain in another node of the same size, with the direction to that node.
    public Set<Pair<Integer, Chain>> touching;
    public Chain parent;
    public final Node node;
    public boolean supported; //Does this chain contain any unloaded Chains (which are assumed to be supported)?
    boolean active = true; //Is this chain currently part of the overall octree structure?
    
    public Chain(int childIndex, Chain childChain, InternalNode node) {
        subchains = new HashSet<>();
        touching = new HashSet<>();
        subchains.add(new Pair<>(childIndex, childChain));
        deriveTouchingFromSubchains();
        childChain.parent = this;
        this.node = node;
        resetSupported();
    }
    
    public Chain(Set<Pair<Integer, Chain>> subchains, Chain parent, Node node) {
        this.subchains = subchains;
        touching = new HashSet<>();
        deriveTouchingFromSubchains();
        this.parent = parent;
        this.node = node;
        resetSupported();
    }
    
    /**
     * Adds those chains that it can be derived that ke'a touches this from
     * the subchains. This may miss some, in the cases that this is touching
     * a FullChain, which doesn't have subchains.
     */
    void deriveTouchingFromSubchains() {
        for (Pair<Integer, Chain> subchain : subchains) {
            int octant = subchain.a;
            Chain child = subchain.b;
            for (Pair<Integer, Chain> childTouching : child.touching) {
                int side = childTouching.a;
                Chain newTouching = childTouching.b.parent;
                if (TreeUtils.isOctantOnSide(octant, side)) {
                    this.touching.add(new Pair<>(side, newTouching));
                    newTouching.touching.add(new Pair<>(-side, this));
                }
            }
        }
    }
    
    public void resetSupported() {
        supported = subchains.stream().anyMatch((Pair<Integer, Chain> sc) -> sc.b.supported);
    }
    
    public void merge(Chain sibling) {
        TreeUtils.assrt(active);
        TreeUtils.assrt(sibling.isActive());
        TreeUtils.assrt(sibling.parent == null || sibling.parent.isActive());
        TreeUtils.assrt(sibling != this);
        TreeUtils.assrt(sibling.node == node);
        subchains.addAll(sibling.subchains);
        touching.addAll(sibling.touching);
        for (Pair<Integer, Chain> t : sibling.touching) {
            t.b.touching.add(new Pair<>(-t.a, this));
        }
        for (Pair<Integer, Chain> newSubchain : sibling.subchains) {
            newSubchain.b.parent = this;
        }
        if (sibling.parent != null) {
            Iterator<Pair<Integer, Chain>> it = sibling.parent.subchains.iterator();
            while (it.hasNext()) {
                if (it.next().b == sibling) {
                    it.remove();
                    break;
                }
            }
        }
        if (parent == null) {
            parent = sibling.parent;
        } else if (sibling.parent != parent && sibling.parent != null) {
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
        if (subchains != null) {
            for (Pair<Integer, Chain> sc : subchains) {
                sc.b.parent = replacement;
                if (replacement.subchains != null) {
                    replacement.subchains.add(sc);
                }
            }
        }
        for (Pair<Integer, Chain> t : touching) {
            t.b.touching.remove(new Pair<>(-t.a, this));
            if (replacement.baseIsTouching(t.b, t.a)) {
                t.b.touching.add(new Pair<>(-t.a, replacement));
                replacement.touching.add(t);
            }
        }
        if (parent != null) {
            for (Pair<Integer, Chain> sc : parent.subchains) {
                if (sc.b == this) {
                    parent.subchains.remove(sc);
                    parent.subchains.add(new Pair<>(sc.a, replacement));
                    break;
                }
            }
            if (replacement.supported != supported) {
                for (Chain ancestor = this; ancestor.parent != null; ancestor = ancestor.parent) {
                    ancestor.parent.resetSupported();
                }
            }
        }
        replacement.parent = parent;
        active = false;
    }
    
    /**
     * Work out whether this is touching the given chain without just
     * getting the result from the cached set (this.touching).
     */
    public boolean baseIsTouching(Chain sibling, int direction) {
        if (sibling instanceof FullChain) {
            return sibling.baseIsTouching(this, -direction);
        }
        for (Pair<Integer, Chain> subchain1 : subchains) {
            for (Pair<Integer, Chain> subchain2 : sibling.subchains) {
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
            return touching.contains(new Pair<>(direction, sibling));
        }
    }
    
    public boolean updateTouching(Chain sibling, int direction) {
        if (sibling instanceof FullChain) {
            return sibling.updateTouching(this, -direction);
        }
        boolean result = false;
        for (Pair<Integer, Chain> subchain1 : subchains) {
            for (Pair<Integer, Chain> subchain2 : sibling.subchains) {
                int adjacency = TreeUtils.isAdjacent(subchain1.a, subchain2.a, direction);

                if (adjacency != 0 && subchain1.b.updateTouching(subchain2.b, adjacency)) {
                    result = true;
                    // The test has side-effects, so the loops must continue.
                }
            }
        }
        if (result && direction != 0) {
            touching.add(new Pair<>(direction, sibling));
            sibling.touching.add(new Pair<>(-direction, this));
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
        Set<Pair<Integer, Chain>> unprocessedSubchains = new HashSet<>(subchains);
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
                Chain fragment = new Chain(connectedComponent, parent, node);
                result.add(fragment);
                for (Pair<Integer, Chain> subchain : connectedComponent) {
                    subchain.b.parent = fragment;
                }
                for (Pair<Integer, Chain> touchingThis : touching) {
                    if (fragment.baseIsTouching(touchingThis.b, touchingThis.a)) {
                        fragment.touching.add(touchingThis);
                        touchingThis.b.touching.add(new Pair<>(-touchingThis.a, fragment));
                    }
                }
            }
        }
        if (result.size() != 1 && parent != null) {
            Integer thisOctant = null;
            Iterator<Pair<Integer, Chain>> siblings = parent.subchains.iterator();
            while (siblings.hasNext()) {
                Pair<Integer, Chain> sibling = siblings.next();
                if (sibling.b == this) {
                    thisOctant = sibling.a;
                    siblings.remove();
                    break;
                }
            }
            if (thisOctant == null) {
                throw new RuntimeException("Chain not found in parent chain, child level = "+node.size+".");
            }
            for (Chain fragment : result) {
                parent.subchains.add(new Pair<>(thisOctant, fragment));
            }
        }
        if (result.size() != 1) {
            TreeUtils.assrt(!result.contains(this));
            inactivate(false);
            node.getChains().addAll(result);
        } else {
            resetSupported();
            Set<Pair<Integer, Chain>> tempTouching = new HashSet<>(touching);
            for (Pair<Integer, Chain> t : tempTouching) {
                if (!baseIsTouching(t.b, t.a)) {
                    touching.remove(t);
                    t.b.touching.remove(new Pair<>(-t.a, this));
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
        for (Pair<Integer, Chain> subchain : subchains) {
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
        for (Pair<Integer, Chain> subchain : subchains) {
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
        for (Pair<Integer, Chain> t : touching) {
            t.b.touching.remove(new Pair<>(-t.a, this));
        }
        if (subchains != null) {
            for (Pair<Integer, Chain> sc : subchains) {
                if (sc.b.parent == this && sc.b.isActive()) {
                    sc.b.inactivate(false);
                }
            }
        }
        if (removeAncestors && parent != null) {
            parent.subchains.removeIf((Pair<Integer, Chain> sc) -> sc.b == this);
            if (parent.subchains.isEmpty()) {
                parent.inactivate(true);
            }
        }
        active = false;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public String toString() {
        String result = "Cmp "+node.size+" [";
        boolean first = true;
        for (Pair<Integer, Chain> subchain : subchains) {
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
            for (Pair<Integer, Chain> subchain : parent.subchains) {
                if (subchain.b == this) {
                    found++;
                }
            }
            TreeUtils.assrt(found >= 1);
            TreeUtils.assrt(found <= 1);
            TreeUtils.assrt(parent.node.size == node.size * 2);
        }
        TreeUtils.assrt(!subchains.isEmpty());
        for (Pair<Integer, Chain> subchain : subchains) {
            TreeUtils.assrt(subchain.b.parent == this);
            TreeUtils.assrt(subchain.b.isActive()); // In theory, this is covered by the next test (if it's in a node's chain list, it'll be validated), but it looks like it's failing.
            TreeUtils.assrt(((InternalNode) node).children[subchain.a].getChains().contains(subchain.b));
        }
        boolean prevSupported = supported;
        resetSupported();
        TreeUtils.assrt(prevSupported || !supported, "size "+node.size);
        TreeUtils.assrt(!prevSupported || supported, "size "+node.size);
        // Checking that the subchains actually do all touch would be good, but also complicated.
        for (Pair<Integer, Chain> t : touching) {
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
        
        if (chain1.subchains == null || chain2.subchains == null) {
            return;
        }
        
        for (Pair<Integer, Chain> sc1 : chain1.subchains) {
            for (Pair<Integer, Chain> sc2 : chain2.subchains) {
                if (TreeUtils.isAdjacent(sc1.a, sc2.a, direction) != 0) {
                    validateTouching(sc1.b, sc2.b, direction);
                }
            }
        }
    }
}
