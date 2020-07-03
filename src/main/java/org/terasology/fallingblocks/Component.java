// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.math.geom.Vector3i;

/**
 * A connected component within an octree node.
 */
public class Component {
    // Pairs of the index of a child node of this component's node, and a component within that child which is part of this.
    public Set<Pair<Integer, Component>> subcomponents;
    // Pairs of a component in another node of the same size, with the direction to that node.
    public Set<Pair<Integer, Component>> touching;
    public Component parent;
    public final Node node;
    public boolean supported; //Does this component contain any unloaded Components (which are assumed to be supported)?
    boolean active = true; //Is this component currently part of the overall octree structure?
    
    public Component(int childIndex, Component childComponent, InternalNode node) {
        subcomponents = new HashSet<>();
        touching = new HashSet();
        subcomponents.add(new Pair<>(childIndex, childComponent));
        deriveTouchingFromSubcomponents();
        childComponent.parent = this;
        this.node = node;
        resetSupported();
    }
    
    public Component(Set<Pair<Integer, Component>> subcomponents, Component parent, Node node) {
        this.subcomponents = subcomponents;
        touching = new HashSet();
        deriveTouchingFromSubcomponents();
        this.parent = parent;
        this.node = node;
        resetSupported();
    }
    
    /**
     * Adds those components that it can be derived that ke'a touches this from
     * the subcomponents. This may miss some, in the cases that this is touching
     * a FullComponent, which doesn't have subcomponents.
     */
    void deriveTouchingFromSubcomponents() {
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            int octant = subcomponent.a;
            Component child = subcomponent.b;
            for(Pair<Integer, Component> childTouching : child.touching) {
                int side = childTouching.a;
                Component newTouching = childTouching.b.parent;
                if(TreeUtils.isOctantOnSide(octant, side)) {
                    this.touching.add(new Pair( side, newTouching));
                    newTouching.touching.add(new Pair(-side, this));
                }
            }
        }
    }
    
    public void resetSupported() {
        supported = subcomponents.stream().anyMatch((Pair<Integer, Component> sc) -> sc.b.supported);
    }
    
    public void merge(Component sibling) {
        TreeUtils.assrt(active);
        TreeUtils.assrt(sibling.isActive());
        TreeUtils.assrt(sibling.parent == null || sibling.parent.isActive());
        TreeUtils.assrt(sibling != this);
        TreeUtils.assrt(sibling.node == node);
        subcomponents.addAll(sibling.subcomponents);
        touching.addAll(sibling.touching);
        for(Pair<Integer, Component> t : sibling.touching) {
            t.b.touching.add(new Pair(-t.a, this));
        }
        for(Pair<Integer, Component> newSubcomponent : sibling.subcomponents) {
            newSubcomponent.b.parent = this;
        }
        if(sibling.parent != null) {
            Iterator<Pair<Integer, Component>> it = sibling.parent.subcomponents.iterator();
            while(it.hasNext()) {
                if(it.next().b == sibling) {
                    it.remove();
                    break;
                }
            }
        }
        if(parent == null) {
            parent = sibling.parent;
        } else if(sibling.parent != parent && sibling.parent != null) {
            parent.merge(sibling.parent);
        }
        if(supported != sibling.supported && parent != null) {
            for(Component ancestor = this; ancestor.parent != null; ancestor = ancestor.parent) {
                ancestor.supported = true;
            }
        }
        supported = supported || sibling.supported;
        sibling.inactivate(false);
    }
    
    public void replaceWith(Component replacement) {
        if(subcomponents != null) {
            for(Pair<Integer, Component> sc : subcomponents) {
                sc.b.parent = replacement;
                if(replacement.subcomponents != null) {
                    replacement.subcomponents.add(sc);
                }
            }
        }
        for(Pair<Integer, Component> t : touching) {
            t.b.touching.remove(new Pair(-t.a, this));
            if(replacement.baseIsTouching(t.b, t.a)) {
                t.b.touching.add(new Pair(-t.a, replacement));
                replacement.touching.add(t);
            }
        }
        if(parent != null) {
            for(Pair<Integer, Component> sc : parent.subcomponents) {
                if(sc.b == this) {
                    parent.subcomponents.remove(sc);
                    parent.subcomponents.add(new Pair(sc.a, replacement));
                    break;
                }
            }
            if(replacement.supported != supported) {
                for(Component ancestor = this; ancestor.parent != null; ancestor = ancestor.parent) {
                    ancestor.parent.resetSupported();
                }
            }
        }
        replacement.parent = parent;
        active = false;
    }
    
    /**
     * Work out whether this is touching the given component without just
     * getting the result from the cached set (this.touching).
     */
    public boolean baseIsTouching(Component sibling, int direction) {
        if(sibling instanceof FullComponent) {
            return sibling.baseIsTouching(this, -direction);
        }
        for(Pair<Integer, Component> subcomponent1 : subcomponents) {
            for(Pair<Integer, Component> subcomponent2 : sibling.subcomponents) {
                int adjacency = TreeUtils.isAdjacent(subcomponent1.a, subcomponent2.a, direction);
                if(adjacency == 0) {
                    continue;
                }
                if(subcomponent1.b.isTouching(subcomponent2.b, adjacency)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public boolean isTouching(Component sibling, int direction) {
        if(direction == 0) {
            return baseIsTouching(sibling, direction);
        } else {
            return touching.contains(new Pair(direction, sibling));
        }
    }
    
    public boolean updateTouching(Component sibling, int direction) {
        if(sibling instanceof FullComponent) {
            return sibling.updateTouching(this, -direction);
        }
        boolean result = false;
        for(Pair<Integer, Component> subcomponent1 : subcomponents) {
            for(Pair<Integer, Component> subcomponent2 : sibling.subcomponents) {
                int adjacency = TreeUtils.isAdjacent(subcomponent1.a, subcomponent2.a, direction);

                if(adjacency != 0 && subcomponent1.b.updateTouching(subcomponent2.b, adjacency)) {
                    result = true;
                    // The test has side-effects, so the loops must continue.
                }
            }
        }
        if(result && direction != 0) {
            touching.add(new Pair(direction, sibling));
            sibling.touching.add(new Pair(-direction, this));
        }
        return result;
    }
    
    /**
     * Tests whether the subcomponents are actually touching, and updates the node and
     * parent component with the new components this splits into, recursively splitting
     * the parent as well if necessary.
     *
     * Returns the top-level components (i.e. most distant ancestor) resulting from this split.
     */
    public Set<Component> checkConnectivity() {
        Set<Component> result = new HashSet<>();
        Set<Pair<Integer, Component>> unprocessedSubcomponents = new HashSet<>(subcomponents);
        Stack<Pair<Integer, Component>> edge = new Stack<>();
        while(!unprocessedSubcomponents.isEmpty()) {
            Set<Pair<Integer, Component>> connectedComponent = new HashSet<>();
            Pair<Integer, Component> start = unprocessedSubcomponents.iterator().next();
            unprocessedSubcomponents.remove(start);
            edge.push(start);
            while(!edge.empty()) {
                Pair<Integer, Component> current = edge.pop();
                connectedComponent.add(current);
                Iterator<Pair<Integer, Component>> others = unprocessedSubcomponents.iterator();
                while(others.hasNext()) {
                    Pair<Integer, Component> other = others.next();
                    int direction = TreeUtils.isAdjacent(current.a, other.a);
                    if(direction != 0 && current.b.isTouching(other.b, direction)) {
                        edge.push(other);
                        others.remove();
                    }
                }
            }
            if(result.isEmpty() && unprocessedSubcomponents.isEmpty()) {
                result.add(this);
            } else {
                Component fragment = new Component(connectedComponent, parent, node);
                result.add(fragment);
                for(Pair<Integer, Component> subcomponent : connectedComponent) {
                    subcomponent.b.parent = fragment;
                }
                for(Pair<Integer, Component> touchingThis : touching) {
                    if(fragment.baseIsTouching(touchingThis.b, touchingThis.a)) {
                        fragment.touching.add(touchingThis);
                        touchingThis.b.touching.add(new Pair(-touchingThis.a, fragment));
                    }
                }
            }
        }
        if(result.size() != 1 && parent != null) {
            Integer thisOctant = null;
            Iterator<Pair<Integer, Component>> siblings = parent.subcomponents.iterator();
            while(siblings.hasNext()) {
                Pair<Integer, Component> sibling = siblings.next();
                if(sibling.b == this) {
                    thisOctant = sibling.a;
                    siblings.remove();
                    break;
                }
            }
            if(thisOctant == null) {
                throw new RuntimeException("Component not found in parent component, child level = "+node.size+".");
            }
            for(Component fragment : result) {
                parent.subcomponents.add(new Pair(thisOctant, fragment));
            }
        }
        if(result.size() != 1) {
            TreeUtils.assrt(!result.contains(this));
            inactivate(false);
            node.getComponents().addAll(result);
        } else {
            resetSupported();
            Set<Pair<Integer, Component>> tempTouching = new HashSet(touching);
            for(Pair<Integer, Component> t : tempTouching) {
                if(!baseIsTouching(t.b, t.a)) {
                    touching.remove(t);
                    t.b.touching.remove(new Pair(-t.a, this));
                }
            }
        }
        if(parent == null) {
            return result;
        } else {
            return parent.checkConnectivity();
        }
    }
            
    
    public boolean isTouching(int side) {
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            if(TreeUtils.isOctantOnSide(subcomponent.a, side) && subcomponent.b.isTouching(side)) {
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
        Set<Vector3i> result = new HashSet();
        int size = node.size;
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            Vector3i subPosition = new Vector3i(pos);
            int octant = subcomponent.a;
            subPosition.add(TreeUtils.isOctantOnSide(octant, 4) ? size/2 : 0,
                            TreeUtils.isOctantOnSide(octant, 2) ? size/2 : 0,
                            TreeUtils.isOctantOnSide(octant, 1) ? size/2 : 0);
            result.addAll(subcomponent.b.getPositions(subPosition));
        }
        return result;
    }
    
    public void inactivate(boolean removeAncestors) {
        node.getComponents().remove(this);
        for(Pair<Integer, Component> t : touching) {
            t.b.touching.remove(new Pair(-t.a, this));
        }
        if(subcomponents != null) {
            for(Pair<Integer, Component> sc : subcomponents) {
                if(sc.b.parent == this && sc.b.isActive()) {
                    sc.b.inactivate(false);
                }
            }
        }
        if(removeAncestors && parent != null) {
            parent.subcomponents.removeIf((Pair<Integer, Component> sc) -> sc.b == this);
            if(parent.subcomponents.isEmpty()) {
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
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            if(first) {
                first = false;
            } else {
                result += ", ";
            }
            result += subcomponent.toString();
        }
        return result+"]";
    }
    
    public void validate(Stack<Integer> location) {
        TreeUtils.assrt(node.getComponents().contains(this));
        TreeUtils.assrt(active);
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
        TreeUtils.assrt(!subcomponents.isEmpty());
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            TreeUtils.assrt(subcomponent.b.parent == this);
            TreeUtils.assrt(subcomponent.b.isActive()); // In theory, this is covered by the next test (if it's in a node's component list, it'll be validated), but it looks like it's failing.
            TreeUtils.assrt(((InternalNode)node).children[subcomponent.a].getComponents().contains(subcomponent.b));
        }
        boolean prevSupported = supported;
        resetSupported();
        TreeUtils.assrt(prevSupported || !supported, "size "+node.size);
        TreeUtils.assrt(!prevSupported || supported, "size "+node.size);
        // Checking that the subcomponents actually do all touch would be good, but also complicated.
        for(Pair<Integer, Component> t : touching) {
            TreeUtils.assrt(baseIsTouching(t.b, t.a), "direction "+t.a+" size "+node.size+" location "+location);
            TreeUtils.assrt(t.b.isTouching(this, -t.a));
            TreeUtils.assrt(node.size == t.b.node.size);
            //TreeUtils.assrt(parent == t.b.parent || parent.isTouching(t.b.parent, t.a), "size = "+node.size+", direction = "+t.a);
        }
    }
    
    /**
     * Assert that, for two adjacent components, all the appropriate subcomponents
     * (and sub-sub-components ect.) are touching.
     */
    public static void validateTouching(Component component1, Component component2, int direction) {
        if(component1.baseIsTouching(component2, direction)) {
            TreeUtils.assrt(component1.isTouching(component2, direction), "size = "+component1.node.size+" direction = "+direction);
        }
        
        if(component1.subcomponents == null || component2.subcomponents == null) {
            return;
        }
        
        for(Pair<Integer, Component> sc1 : component1.subcomponents) {
            for(Pair<Integer, Component> sc2 : component2.subcomponents) {
                if(TreeUtils.isAdjacent(sc1.a, sc2.a, direction) != 0) {
                    validateTouching(sc1.b, sc2.b, direction);
                }
            }
        }
    }
}
