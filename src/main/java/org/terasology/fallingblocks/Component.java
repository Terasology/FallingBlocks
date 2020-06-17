// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import org.terasology.math.geom.Vector3i;

/**
 * A connected component within an octree node.
 */
public class Component {
    // Pairs of the index of a child node of this component's node, and a component within that child which is part of this. The components are null if the child node is a leaf.
    public Set<Pair<Integer, Component>> subcomponents;
    public Component parent;
    public final Node node;
    public boolean supported; //Does this component contain any UnloadedComponents (which are assumed to be supported)?
    
    public Component(int childIndex, Component childComponent, InternalNode node) {
        subcomponents = new HashSet<>();
        subcomponents.add(new Pair<>(childIndex, childComponent));
        if(childComponent != null) {
            childComponent.parent = this;
        }
        this.node = node;
        resetSupported();
    }
    
    Component(Set<Pair<Integer, Component>> subcomponents, Component parent, Node node) {
        this.subcomponents = subcomponents;
        this.parent = parent;
        this.node = node;
        resetSupported();
    }
    
    public void resetSupported() {
        supported = subcomponents.stream().anyMatch((Pair<Integer, Component> sc) -> sc.b != null && sc.b.supported);
    }
    
    public void merge(Component sibling) {
        subcomponents.addAll(sibling.subcomponents);
        supported = supported || sibling.supported;
        for(Pair<Integer, Component> newSubcomponent : sibling.subcomponents) {
            if(newSubcomponent.b != null) {
                newSubcomponent.b.parent = this;
            }
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
            parent.node.getComponents().remove(sibling.parent);
        }
    }
    
    public boolean isTouching(Component sibling, int direction) {
        if(sibling instanceof UnloadedComponent) {
            return sibling.isTouching(this, -direction);
        }
        for(Pair<Integer, Component> subcomponent1 : subcomponents) {
            for(Pair<Integer, Component> subcomponent2 : sibling.subcomponents) {
                int adjacency = TreeUtils.isAdjacent(subcomponent1.a, subcomponent2.a, direction);
                if(adjacency == 0) {
                    continue;
                }
                if(subcomponent1.b == null) { // They're both leaves.
                    return true;
                }
                if(subcomponent1.b.isTouching(subcomponent2.b, adjacency)) {
                    return true;
                }
            }
        }
        return false;
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
                    if(direction != 0 && (current.b == null || current.b.isTouching(other.b, direction))) {
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
                    if(subcomponent.b != null) {
                        subcomponent.b.parent = fragment;
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
            node.getComponents().remove(this);
            node.getComponents().addAll(result);
        } else {
            resetSupported();
        }
        if(parent == null) {
            return result;
        } else {
            return parent.checkConnectivity();
        }
    }
            
    
    public boolean isTouching(int side) {
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            if(TreeUtils.isOctantOnSide(subcomponent.a, side) && (subcomponent.b == null || subcomponent.b.isTouching(side))) {
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
            if(subcomponent.b == null) {
                result.add(subPosition);
            } else {
                result.addAll(subcomponent.b.getPositions(subPosition));
            }
        }
        return result;
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
            if(subcomponent.b == null) {
                result += subcomponent.a;
            } else {
                result += subcomponent.toString();
            }
        }
        return result+"]";
    }
}
