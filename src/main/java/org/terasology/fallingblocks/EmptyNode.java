// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.terasology.math.geom.Vector3i;

/**
 * Nodes containing no solid blocks.
 */
public class EmptyNode extends Node {
    // Just an ArrayList would be nicer, but to do that I'd need the log of the size.
    private static HashMap<Integer, EmptyNode> nodes = new HashMap();
    
    private EmptyNode(int size) {
        this.size = size;
        nodes.put(size, this);
    }
    
    public static EmptyNode get(int size) {
        EmptyNode result = nodes.get(size);
        if(result != null) {
            return result;
        } else {
            return new EmptyNode(size);
        }
    }
    
    // The list is immutable, so it's safe to return.
    @Override
    public Set<Component> getComponents() {
        return Collections.EMPTY_SET;
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
        throw new RuntimeException("Trying to remove a block from an empty node.");
    }
    
    @Override
    public Pair<Node, Pair<Component, Set<Integer>>> addBlock(Vector3i pos) {
        if(size == 1) {
            return new Pair(LeafNode.node, new Pair(null, new HashSet(Arrays.asList(-4,-2,-1,1,2,4))));
        } else {
            return equivalentInternalNode().addBlock(pos);
        }
    }
    
    /**
     * Replace an UnloadedNode with something else.
     */
    @Override
    public Set<Component> insertNewChunk(Node newNode, Vector3i pos) {
        throw new RuntimeException("Trying to add already loaded chunk.");
    }
    
    /**
     * Replace something else with an UnloadedNode.
     */
    @Override
    public Pair<Node, Component> removeChunk(Vector3i pos, int chunkSize) {
        return equivalentInternalNode().removeChunk(pos, chunkSize);
    }
    
    private InternalNode equivalentInternalNode() {
        Node[] children = new Node[8];
        Node child = get(size/2);
        for(int i=0; i<8; i++) {
            children[i] = child;
        }
        return new InternalNode(size, children);
    }
}
