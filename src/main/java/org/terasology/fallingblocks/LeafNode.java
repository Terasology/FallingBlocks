// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.terasology.math.geom.Vector3i;

/**
 * A singleton representing a single solid block.
 */
public class LeafNode extends Node {
    public static final LeafNode node = new LeafNode();
    private final Set<Component> components = new HashSet(Arrays.asList((Component)null));
    
    private LeafNode() {
        size = 1;
    }
    
    @Override
    public Set<Component> getComponents() {
        return components;
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        return new Pair(-2, null);
    }
    
    @Override
    public Pair<Node, Set<Component>> removeBlock(Vector3i pos) {
        return new Pair(EmptyNode.get(size), null);
    }
    
    @Override
    public Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> insertFullNode(Vector3i pos, Node node, Set<Pair<Integer, Node>> siblings) {
        throw new RuntimeException("Trying to add a block to a leaf node even though it should already have a block there.");
    }
    
    /**
     * Replace an UnloadedNode with something else.
     */
    @Override
    public Set<Component> insertNewChunk(Node newNode, Vector3i pos) {
        throw new RuntimeException("Trying to insert new chunk in a leaf node. Node can't replace itself.");
    }
}
