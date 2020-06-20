// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.math.geom.Vector3i;

public class InternalNode extends Node {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalNode.class);
    
    // The child nodes, in the order:
    // -x-y-z, -x-y+z, -x+y-z, -x+y+z, +x-y-z, +x-y+z, +x+y-z, +x+y+z
    // The cardinal directions have corresponding labels:
    // +x: 4, +y: 2, +z: 1, -x: -4, -y: -2, -z: -1
    public Node[] children;
    
    // The connected components of the solid blocks in this region
    private Set<Component> components;
    
    public InternalNode(int size, Node[] children) {
        this.size = size;
        this.children = children;
        
        components = new HashSet<>();
        for(int i=0; i<8; i++) {
            for(Component c : children[i].getComponents()) {
                components.add(new Component(i, c, this));
            }
        }
        
        // There are better ways of doing this, but this should suffice.
        Set<Component> tempComponents = new HashSet(components);
        for(Component c1 : tempComponents) {
            boolean merging = true;
            while(merging) {
                merging = false;
                for(Component c2 : tempComponents) {
                    if(c1.isActive() && c2.isActive() && c1 != c2) {
                        c1.updateTouching(c2, 0);
                        if(c1.isTouching(c2, 0)) {
                            c1.merge(c2);
                            merging = true;
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public Set<Component> getComponents() {
        return components;
    }
    
    @Override
    public Pair<Node, Set<Component>> removeBlock(Vector3i pos) {
        int octant = TreeUtils.octantOfPosition(pos, size);
        Vector3i subPosition = TreeUtils.modVector(pos, size/2);
        Set<Component> splitComponents = null; //Set to null initially just to avoid the uninitialized error, but it should always be set to something later.
        // If the children are leaves, they don't actually have references to Components, so a more brute-force method is necessary to find which of the components of this node contains the removed block.
        if(size == 2) {
            children[octant] = EmptyNode.get(1);
            outer : for(Component component : components) {
                for(Pair<Integer, Component> subcomponent : component.subcomponents) {
                    if(subcomponent.a == octant) { // This will be satisfied exactly once.
                        component.subcomponents.remove(subcomponent);
                        splitComponents = component.checkConnectivity();
                        //component.supported must already be false, because all UnloadedComponents have size at least 32, so that doesn't need to be updated.
                        break outer;
                    }
                }
            }
        } else {
            Pair<Node, Set<Component>> childResult = children[octant].removeBlock(subPosition);
            children[octant] = childResult.a;
            splitComponents = childResult.b;
        }
        return new Pair(components.isEmpty() ? EmptyNode.get(size) : this, splitComponents);
    }
    
    /**
     * @param pos      The position where the block is added, relative to this node.
     * @param siblings The nodes adjacent to the new block, with the same size as this.
     * @return The node to replace this with, the component containing the new block, and the adjacent components.
     */
    @Override
    public Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> insertFullNode(Vector3i pos, Node node, Set<Pair<Integer, Node>> siblings) {
        if(size == node.size) {
            if(node.getComponents().size() != 1) {
                throw new IllegalArgumentException("insertFullNode may only be used with UnloadedNodes and LeafNodes.");
            }
            for(Component component : components) {
                if(component.parent != null) {
                    component.parent.subcomponents.removeIf((Pair<Integer,Component> subcomponent) -> subcomponent.b == component);
                }
                component.inactivate();
            }
            Component component = node.getComponents().iterator().next();
            Set<Pair<Integer, Component>> nextTouching = new HashSet();
            for(Pair<Integer, Node> siblingPair : siblings) {
                int direction = siblingPair.a;
                Node sibling = siblingPair.b;
                for(Component siblingComponent : sibling.getComponents()) {
                    if(component.updateTouching(siblingComponent, direction)) {
                        nextTouching.add(new Pair(direction, siblingComponent));
                    }
                }
            }
            return new Pair(node, new Pair(component, nextTouching));
        }
        
        int octant = TreeUtils.octantOfPosition(pos, size);
        Vector3i subPosition = TreeUtils.modVector(pos, size/2);
        //logger.info("Adding block, node size = "+size+", block in octant "+octant+" with position "+pos+" and subPosition "+subPosition+".");
        Set<Pair<Integer, Node>> nextSiblings = new HashSet();
        for(int i=0; i<TreeUtils.directions.length; i++) {
            int side = TreeUtils.directions[i];
            //logger.info("Trying new sibling on side "+side);
            if(TreeUtils.isOctantOnSide(octant, -side) && TreeUtils.isPositionOnSide(subPosition, side, size/2) && !(children[octant+side] instanceof EmptyNode)) {
                //logger.info("New sibling added.");
                nextSiblings.add(new Pair(side, children[octant+side]));
            }
        }
        for(Pair<Integer, Node> siblingPair : siblings) {
            int side = siblingPair.a;
            Node sibling = siblingPair.b;
            TreeUtils.assrt(sibling.size == size);
            //logger.info("Existing sibling on side "+side+", "+sibling.getClass());
            if(sibling != null && sibling instanceof InternalNode) {
                Node child = ((InternalNode)sibling).children[octant-side];
                if(!(child instanceof EmptyNode)) {
                    nextSiblings.add(new Pair(side, child));
                }
            }
        }
        
        Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> p = children[octant].insertFullNode(subPosition, node, nextSiblings);
        children[octant] = p.a;
        Component newComponent = p.b.a;
        Set<Pair<Integer, Component>> touching = p.b.b;
        
        Component parentComponent; // If newComponent != null, parentComponent == newComponent.parent.
        if(newComponent != null && newComponent.parent != null) {
            parentComponent = newComponent.parent;
        } else { // The block hasn't merged into any existing components.
            parentComponent = new Component(octant, newComponent, this);
            components.add(parentComponent);
            if(newComponent != null) {
                newComponent.parent = parentComponent;
            }
        }
        
        Set<Pair<Integer, Component>> nextTouching = new HashSet();
        if(touching == null) {
            TreeUtils.assrt(size == 2, size);
            //logger.info("Size 2, parentComponent = "+parentComponent);
            for(Pair<Integer, Node> siblingPair : siblings) {
                int side = siblingPair.a;
                Node sibling = siblingPair.b;
                for(Component siblingComponent : sibling.getComponents()) {
                    //logger.info("Testing siblings, side "+side+", "+siblingComponent);
                    if(parentComponent.baseIsTouching(siblingComponent, side)) {
                        //logger.info("touching");
                        nextTouching.add(new Pair(side, siblingComponent));
                        parentComponent.touching.add(new Pair(side, siblingComponent));
                        siblingComponent.touching.add(new Pair(-side, parentComponent));
                    }
                }
            }
            Set<Component> tempComponents = new HashSet(components); // The list may be changed during the iteration, so the iterator may misbehave.
            for(Component component : tempComponents) {
                if(component == parentComponent || !component.isActive()) {
                    continue;
                }
                
                if(component.isTouching(parentComponent, 0)) {
                    component.merge(parentComponent);
                    parentComponent = component;
                }
            }
        } else {
            for(Pair<Integer, Node> sibling : siblings) {
                if(sibling.b instanceof UnloadedNode) {
                    nextTouching.add(new Pair(sibling.a, ((UnloadedNode)sibling.b).getComponent()));
                }
            }
            for(Pair<Integer, Component> t : touching) {
                TreeUtils.assrt(t.b.parent != null); //If this is the root node, touching is already empty.
                if(t.b.parent == parentComponent) {
                    continue;
                } else if(t.b.parent.node == this) {
                    t.b.parent.merge(parentComponent);
                    parentComponent = t.b.parent;
                } else {
                    parentComponent.touching.add(new Pair( t.a, t.b.parent));
                    t.b.parent.touching.add(new Pair(-t.a, parentComponent));
                    nextTouching.add(new Pair(t.a, t.b.parent));
                }
            }
        }
        if(canShrink().a == -1) {
            return insertFullNode(pos, new UnloadedNode(size), siblings);
        }
        return new Pair(this, new Pair(parentComponent, nextTouching));
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        Pair<Integer, Node> result = new Pair(-1, null);
        for(int i=0; i<8; i++) {
            if(!(children[i] instanceof UnloadedNode)) {
                if(result.a == -1) {
                    result.a = i;
                    result.b = children[i];
                } else {
                    result.a = -2;
                    result.b = null;
                }
            }
        }
        return result;
    }
    
    /**
     * Replace an UnloadedNode with something else.
     */
    @Override
    public Set<Component> insertNewChunk(Node newNode, Vector3i pos) {
        //logger.info("Inserting at relative position "+pos+". Node size = "+size+". Octant = "+TreeUtils.octantOfPosition(pos, size)+".");
        if(newNode.size >= size) {
            logger.warn("Adding already loaded chunk.");
            return Collections.EMPTY_SET;
        }
        int octant = TreeUtils.octantOfPosition(pos, size);
        Node oldChild = children[octant];
        if(oldChild instanceof UnloadedNode) {
            children[octant] = TreeUtils.buildExpandedNode(newNode, TreeUtils.modVector(pos, size/2), size/2);
            Component component = ((UnloadedNode)oldChild).getComponent();
            TreeUtils.assrt(component.isActive());
            TreeUtils.assrt(component.parent.isActive());
            component.parent.subcomponents.removeIf((Pair<Integer,Component> subcomponent) -> subcomponent.a == octant);
            for(Component childComponent : children[octant].getComponents()) {
                childComponent.parent = component.parent;
                component.parent.subcomponents.add(new Pair(octant, childComponent));
                for(Pair<Integer, Component> t : component.touching) {
                    childComponent.updateTouching(t.b, t.a);
                }
            }
            component.inactivate();
            return component.parent.checkConnectivity();
        } else {
            return oldChild.insertNewChunk(newNode, TreeUtils.modVector(pos, size/2));
        }
    }
    
    @Override
    public void validate() {
        for(int i=0; i<8; i++) {
            TreeUtils.assrt(children[i].size == size/2);
            for(Component component : children[i].getComponents()) {
                if(component != null) {
                    TreeUtils.assrt(component.parent.node == this);
                    TreeUtils.assrt(components.contains(component.parent), size);
                    for(int j=0; j<8; j++) {
                        int direction = TreeUtils.isAdjacent(i, j);
                        if(direction != 0) {
                            for(Component component2 : children[j].getComponents()) {
                                Component.validateTouching(component, component2, direction);
                            }
                        }
                    }
                }
            }
            TreeUtils.assrt((size == 2) || ! (children[i] instanceof LeafNode));
            children[i].validate();
        }
        for(Component component : components) {
            component.validate();
            TreeUtils.assrt(component.node == this);
            for(Component otherComponent : components) {
                TreeUtils.assrt(component == otherComponent || !component.isTouching(otherComponent, 0));
            }
        }
    }
}
