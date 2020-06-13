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
    public final InternalNode node;
    
    public Component(int childIndex, Component childComponent, InternalNode node) {
        subcomponents = new HashSet<>();
        subcomponents.add(new Pair<>(childIndex, childComponent));
        if(childComponent != null) {
            childComponent.parent = this;
        }
        this.node = node;
    }
    
    private Component(Set<Pair<Integer, Component>> subcomponents, Component parent, InternalNode node) {
        this.subcomponents = subcomponents;
        this.parent = parent;
        this.node = node;
    }
    
    public void merge(Component sibling) {
        subcomponents.addAll(sibling.subcomponents);
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
        for(Pair<Integer, Component> subcomponent1 : subcomponents) {
            for(Pair<Integer, Component> subcomponent2 : sibling.subcomponents) {
                int adjacency = Node.isAdjacent(subcomponent1.a, subcomponent2.a, direction);
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
     * Tests whether the subcomponents are actually touching, and returns a list of the
     * connected components this should split into, updating the parent's references too.
     * If the set has 1 element, it's this component. Otherwise, none of them are.
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
                    int direction = Node.isAdjacent(current.a, other.a);
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
                    subcomponent.b.parent = fragment;
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
        return result;
    }
            
    
    public boolean isTouching(int side) {
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            if(Node.isOctantOnSide(subcomponent.a, side) && (subcomponent.b == null || subcomponent.b.isTouching(side))) {
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
    
    public Set<Vector3i> getPositions(Vector3i pos, int size) {
        Set<Vector3i> result = new HashSet();
        for(Pair<Integer, Component> subcomponent : subcomponents) {
            Vector3i subPosition = new Vector3i(pos);
            int octant = subcomponent.a;
            subPosition.add(Node.isOctantOnSide(octant, 4) ? size/2 : 0,
                            Node.isOctantOnSide(octant, 2) ? size/2 : 0,
                            Node.isOctantOnSide(octant, 1) ? size/2 : 0);
            if(subcomponent.b == null) {
                result.add(subPosition);
            } else {
                result.addAll(subcomponent.b.getPositions(subPosition, size/2));
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
