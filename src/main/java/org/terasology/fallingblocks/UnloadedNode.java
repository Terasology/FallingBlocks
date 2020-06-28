// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.math.geom.Vector3i;

/**
 * Nodes to fill in the spaces in the octree where the chunks aren't actually loaded.
 */
public class UnloadedNode extends Node {
    private final Set<Component> components;
    private final Component component;
    
    public UnloadedNode(int size) {
        this.size = size;
        component = new UnloadedComponent(this);
        components = new HashSet(Arrays.asList(component));
    }
    
    // The list is immutable, so it's safe to return.
    @Override
    public Set<Component> getComponents() {
        return components;
    }
    
    public Component getComponent() {
        return component;
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        return new Pair(-1, null);
    }
    
    @Override
    public Pair<Node, Set<Component>> removeBlock(Vector3i pos) {
        throw new RuntimeException("Trying to remove a block from an unloaded node.");
    }
    
    @Override
    public Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> insertFullNode(Vector3i pos, Node node, Set<Pair<Integer, Node>> siblings) {
        throw new RuntimeException("Trying to insert something in an unloaded node.");
    }
    
    /**
     * Replace an UnloadedNode with something else.
     */
    @Override
    public Set<Component> insertNewChunk(Node newNode, Vector3i pos) {
        throw new RuntimeException("Trying to insert new chunk in an unloaded node. Node can't replace itself.");
    }
    
    public void validate(Stack<Integer> location) {
        component.validate(location);
        TreeUtils.assrt(components.contains(component));
        TreeUtils.assrt(components.size() == 1);
        TreeUtils.assrt(component != null);
        TreeUtils.assrt(component instanceof UnloadedComponent);
    }
}
