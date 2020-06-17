// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.math.geom.Vector3i;

public class InternalNode extends Node {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalNode.class);
    
    // The child nodes, or nulls for empty regions, in the order:
    // -x-y-z, -x-y+z, -x+y-z, -x+y+z, +x-y-z, +x-y+z, +x+y-z, +x+y+z
    // The cardinal directions have corresponding labels:
    // +x: 4, +y: 2, +z: 1, -x: -4, -y: -2, -z: -1
    public Node[] children;
    
    // The connected components of the solid blocks in this region
    private ArrayList<Component> components;
    
    public InternalNode(int size, Node[] children) {
        this.size = size;
        this.children = children;
        
        components = new ArrayList<>();
        for(int i=0; i<children.length; i++) {
            if(children[i] != null) {
                for(Component c : children[i].getComponents()) {
                    components.add(new Component(i, c, this));
                }
            }
        }
        
        for(int i=0; i<components.size(); i++) { //TODO: find a better way to detect connected components of a graph without an edge list.
            for(int j=i+1; j<components.size(); j++) {
                if(components.get(i).isTouching(components.get(j), 0)) {
                    components.get(i).merge(components.get(j));
                    components.remove(j);
                    j = i; //It's about to be incremented, so effectively reset it to i+1.
                }
            }
        }
    }
    
    @Override
    public List<Component> getComponents() {
        return components;
    }
    
    @Override
    public Pair<Node, Set<Component>> removeBlock(Vector3i pos) {
        int octant = TreeUtils.octantOfPosition(pos, size);
        Vector3i subPosition = TreeUtils.modVector(pos, size/2);
        Set<Component> splitComponents = null; //Set to null initially just to avoid the uninitialized error, but it should always be set to something later.
        // If the children are leaves, they don't actually have references to Components, so a more brute-force method is necessary to find which of the components of this node contains the removed block.
        if(size == 2) {
            children[octant] = null;
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
        return new Pair(components.isEmpty() ? null : this, splitComponents);
    }
    
    @Override
    public Pair<Component, Set<Integer>> addBlock(Vector3i pos) {
        int octant = TreeUtils.octantOfPosition(pos, size);
        Vector3i subPosition = TreeUtils.modVector(pos, size/2);
        Component newComponent = null;
        Component parentComponent = null; // If newComponent != null, parentComponent == newComponent.parent. Set to null initially just to avoid the uninitialized error, but it should always be set to something later.
        Set<Integer> exposure;
        if(children[octant] == null) {
            children[octant] = TreeUtils.buildSingletonNode(size/2, subPosition);
            newComponent = children[octant].getComponents().get(0);
            exposure = new HashSet<>();
            if(subPosition.x == 0) exposure.add(-4);
            if(subPosition.y == 0) exposure.add(-2);
            if(subPosition.z == 0) exposure.add(-1);
            if(subPosition.x == size/2-1) exposure.add(4);
            if(subPosition.y == size/2-1) exposure.add(2);
            if(subPosition.z == size/2-1) exposure.add(1);
        } else {
            Pair<Component, Set<Integer>> p = children[octant].addBlock(subPosition);
            newComponent = p.a;
            parentComponent = newComponent.parent;
            exposure = p.b;
        }
        if(newComponent == null || newComponent.parent == null) { // The block hasn't merged into any existing components.
            parentComponent = new Component(octant, newComponent, this);
        }
        if(newComponent != null && newComponent.parent == null) {
            newComponent.parent = parentComponent;
        }
        boolean merged = false; // Keeps track of whether newComponent.parent ends up as something already in the components list.
        if(!exposure.isEmpty()) {
            List<Component> tempComponents = new ArrayList(components); // The list may be changed during the iteration, so the iterator may misbehave.
            for(Component component : tempComponents) {
                if(component == parentComponent) {
                    continue;
                }
                // The test for isTouching here could be refined to something referring to the new block position, but I'm keeping it as the simpler less efficient version initially.
                if(component.isTouching(parentComponent, 0)) {
                    component.merge(parentComponent);
                    parentComponent = component;
                    merged = true;
                }
            }
            exposure.removeIf((Integer side) -> !TreeUtils.isOctantOnSide(octant, side));
        }
        if(!merged) {
            components.add(parentComponent);
        }
        return new Pair<>(parentComponent, exposure);
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        Pair<Integer, Node> result = new Pair(-1, null);
        for(int i=0; i<8; i++) {
            if(children[i] != null && !(children[i] instanceof UnloadedNode)) {
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
            return new HashSet();
        }
        int octant = TreeUtils.octantOfPosition(pos, size);
        Node oldChild = children[octant];
        if(oldChild instanceof UnloadedNode) {
            children[octant] = TreeUtils.buildExpandedNode(newNode, TreeUtils.modVector(pos, size/2), size/2);
            Component component = oldChild.getComponents().get(0).parent;
            component.subcomponents.removeIf((Pair<Integer,Component> subcomponent) -> subcomponent.a == octant);
            for(Component childComponent : children[octant].getComponents()) {
                childComponent.parent = component;
                component.subcomponents.add(new Pair(octant, childComponent));
            }
            return component.checkConnectivity();
        } else {
            return oldChild.insertNewChunk(newNode, TreeUtils.modVector(pos, size/2));
        }
    }
    
    /**
     * Replace something else with an UnloadedNode.
     */
    @Override
    public Pair<Node, Component> removeChunk(Vector3i pos, int chunkSize) {
        UnloadedNode uNode = new UnloadedNode(size);
        Pair<Node, Component> unloadedResult = new Pair(uNode, uNode.getComponents().get(0));
        if(chunkSize == size) {
            return unloadedResult;
        } else {
            int octant = TreeUtils.octantOfPosition(pos, size);
            
            Node oldChild = children[octant];
            if(oldChild == null) {
                oldChild = new InternalNode(size/2, new Node[8]);
            }
            Pair<Node, Component> childResult = oldChild.removeChunk(TreeUtils.modVector(pos, size/2), chunkSize);
            children[octant] = childResult.a;
            if(canShrink().a == -1) { // All the children are unloaded.
                return unloadedResult;
            }
            Component newComponent = new Component(octant, childResult.b, this);
            if(children[octant] instanceof UnloadedNode) {
                for(Component component : components) {
                    component.subcomponents.removeIf((Pair<Integer,Component> subcomponent) -> subcomponent.a == octant);
                }
            }
            
            List<Component> tempComponents = new ArrayList(components); // The list may be changed during the iteration, so the iterator may misbehave.
            components.add(newComponent);
            for(Component component : tempComponents) {
                if(component.isTouching(newComponent, 0)) {
                    component.merge(newComponent);
                    newComponent = component;
                }
            }
            return new Pair(this, newComponent);
        }
    }
}
