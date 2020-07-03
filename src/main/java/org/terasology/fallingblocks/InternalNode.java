// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;
import java.util.stream.Collectors;

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
        
        Set<Pair<Integer, Component>> subComponents = new HashSet();
        for(int i=0; i<8; i++) {
            for(Component c : children[i].getComponents()) {
                subComponents.add(new Pair(i, c));
            }
        }
        
        components = new HashSet();
        
        // DFS to construct the Components, and update the touching sets of the subcomponents at the same time. The search is arranged a little non-standardly in order to catch all the touching pairs.
        Stack<Pair<Integer, Component>> stack = new Stack();
        while(!subComponents.isEmpty()) {
            Set<Pair<Integer, Component>> rawComponent = new HashSet();
            Pair<Integer, Component> first = subComponents.iterator().next();
            rawComponent.add(first);
            stack.push(first);
            while(!stack.isEmpty()) {
                Pair<Integer, Component> current = stack.pop();
                for(Pair<Integer, Component> next : subComponents) {
                    int direction = TreeUtils.isAdjacent(current.a, next.a);
                    if(direction != 0 && current.b.updateTouching(next.b, direction)) {
                        if(!rawComponent.contains(next)) {
                            rawComponent.add(next);
                            stack.push(next);
                        }
                    }
                }
            }
            subComponents.removeAll(rawComponent);
            
            Component component = new Component(rawComponent, null, this);
            components.add(component);
            for(Pair<Integer, Component> sc : rawComponent) {
                sc.b.parent = component;
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
        Pair<Node, Set<Component>> childResult = children[octant].removeBlock(subPosition);
        children[octant] = childResult.a;
        return new Pair(components.isEmpty() ? EmptyNode.get(size) : this, childResult.b);
    }
    
    /**
     * @param pos      The position where the block is added, relative to this node.
     * @param siblings The nodes adjacent to the new block, with the same size as this.
     * @return The node to replace this with, the component containing the new block, and the adjacent components.
     */
    @Override
    public Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> insertFullNode(Vector3i pos, FullNode node, Set<Pair<Integer, Node>> siblings) {
        if(size == node.size) {
            return replaceWithFullNode(node, siblings);
        }
        
        int octant = TreeUtils.octantOfPosition(pos, size);
        Vector3i subPosition = TreeUtils.modVector(pos, size/2);
        //logger.info("Inserting node of size "+node.size+", this node size = "+size+", block in octant "+octant+" with position "+pos+" and subPosition "+subPosition+".");
        Set<Pair<Integer, Node>> nextSiblings = new HashSet();
        for(int i=0; i<TreeUtils.directions.length; i++) {
            int side = TreeUtils.directions[i];
            //logger.info("Trying new sibling on side "+side);
            if(TreeUtils.isOctantOnSide(octant, -side) && TreeUtils.isPositionOnSide(subPosition, side, node.size, size/2)) {
                //logger.info("Correct position.");
                if(!(children[octant+side] instanceof EmptyNode)) {
                    //logger.info("New sibling  on side "+side+", "+children[octant+side].getClass());
                    nextSiblings.add(new Pair(side, children[octant+side]));
                }
            }
        }
        for(Pair<Integer, Node> siblingPair : siblings) {
            int side = siblingPair.a;
            Node sibling = siblingPair.b;
            TreeUtils.assrt(sibling.size == size);
            TreeUtils.assrt(TreeUtils.isOctantOnSide(octant,side));
            if(sibling instanceof InternalNode) {
                Node child = ((InternalNode)sibling).children[octant-side];
                //logger.info("Existing sibling on side "+side+", "+child.getClass());
                if(!(child.getComponents().isEmpty())) {
                    nextSiblings.add(new Pair(side, child));
                }
            }
        }
        
        Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> p = children[octant].insertFullNode(subPosition, node, nextSiblings);
        children[octant] = p.a;
        Component newComponent = p.b.a;
        Set<Pair<Integer, Component>> touching = p.b.b;

        if(newComponent.parent == null) { // The block hasn't merged into any existing components.
            components.add(new Component(octant, newComponent, this));
        }
        
        Set<Pair<Integer, Component>> nextTouching = new HashSet();

        //logger.info("Back to size "+size);
        for(Pair<Integer, Node> sibling : siblings) {
            if(sibling.b instanceof FullNode) {
                nextTouching.add(new Pair(sibling.a, ((FullNode)sibling.b).getComponent()));
            }
        }
        for(Pair<Integer, Component> t : touching) {
            Component touchingComponent = t.b.parent;
            TreeUtils.assrt(touchingComponent != null); //If this is the root node, touching is already empty.
            if(touchingComponent == newComponent.parent) {
                //logger.info("touching on side "+t.a+" superfluous.");
                continue;
            } else if(touchingComponent.node == this) {
                //logger.info("touching on side "+t.a+" needs merging.");
                touchingComponent.merge(newComponent.parent);
                //newComponent.parent is set to touchingComponent.
            } else {
                //logger.info("touching on side "+t.a+" needs recording, carrying up.");
                nextTouching.add(new Pair(t.a, touchingComponent));
            }
        }
        //logger.info("After merging down, "+components.size()+" components left.");

        for(Pair<Integer, Component> t : nextTouching) {
            TreeUtils.assrt(newComponent.parent.baseIsTouching(t.b, t.a));
            TreeUtils.assrt(t.b.baseIsTouching(newComponent.parent,-t.a));
            newComponent.parent.touching.add(t);
            t.b.touching.add(new Pair(-t.a, newComponent.parent));
            //logger.info("Recording touch, side "+t.a);
        }

        boolean uniformClass = true;
        for(int i=1; i<8; i++) {
            if(children[i].getClass() != children[0].getClass()) {
                uniformClass = false;
            }
        }
        if(uniformClass && (children[0] instanceof FullNode)) {
            //logger.info("Replacing with "+children[0].getClass()+". size "+size);
            Set<Pair<Integer, Node>> altSiblings = new HashSet();
            for(Pair<Integer, Component> t : newComponent.parent.touching) {
                altSiblings.add(new Pair(t.a, t.b.node));
            }
            // This larger replacement node may be touching nodes farther away than those in the siblings set, so a new version is necessary. This seems a little dodgy in that it breaks a few of the general assumptions in how insertFullNode works, but it should be okay.
            return replaceWithFullNode(node.getSimilar(size), altSiblings);
        }

        return new Pair(this, new Pair(newComponent.parent, nextTouching));
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        int resultIndex = -1;
        Node resultNode = null;
        for(int i=0; i<8; i++) {
            if(!(children[i] instanceof UnloadedNode)) {
                if(resultIndex == -1) {
                    resultIndex = i;
                    resultNode = children[i];
                } else {
                    resultIndex = -2;
                    resultNode = null;
                }
            }
        }
        return new Pair(resultIndex, resultNode);
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
            component.inactivate(false);
            return component.parent.checkConnectivity();
        } else {
            return oldChild.insertNewChunk(newNode, TreeUtils.modVector(pos, size/2));
        }
    }
    
    @Override
    public void validate(Stack<Integer> location) {
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
            location.push(i);
            children[i].validate(location);
            location.pop();
        }
        for(Component component : components) {
            component.validate(location);
            TreeUtils.assrt(component.node == this);
            for(Component otherComponent : components) {
                TreeUtils.assrt(component == otherComponent || !component.isTouching(otherComponent, 0));
            }
        }
    }
}
