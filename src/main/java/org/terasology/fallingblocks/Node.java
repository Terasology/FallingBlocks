// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.math.geom.Vector3i;

/**
 * An octree node storing data on the connected components of the solid blocks within its region.
 * In order to avoid having a separate node for every single block, each node doesn't know its own location.
 */
public abstract class Node {
    public int size;
    
    public abstract Set<Component> getComponents();
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    public abstract Pair<Integer, Node> canShrink();
    
    public abstract Pair<Node, Set<Component>> removeBlock(Vector3i pos);
    
    public Pair<Node, Component> addBlock(Vector3i pos) {
        Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> result = insertFullNode(pos, LeafNode.node, new HashSet());
        return new Pair(result.a, result.b.a);
    }
    
    /**
     * Replace a node with one of the same size that is entirely solid (i.e. LeafNode
     * or UnloadedNode).
     * 
     * @param pos      The position where the node is added, relative to this node.
     * @param node     The node to add
     * @param siblings The nodes adjacent to the new block, with the same size as this.
     * @return The node to replace this with, the component containing the new block, and the adjacent components.
     */
    abstract Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> insertFullNode(Vector3i pos, Node node, Set<Pair<Integer, Node>> siblings);
    
    /**
     * Replace an UnloadedNode with something else.
     */
    public abstract Set<Component> insertNewChunk(Node newNode, Vector3i pos);
    
    /**
     * Replace something else with an UnloadedNode.
     */
    public Pair<Node, Component> removeChunk(Vector3i pos, int chunkSize) {
        Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> result = insertFullNode(pos, new UnloadedNode(chunkSize), new HashSet());
        return new Pair(result.a, result.b.a);
    }
    
    /**
     * For debugging purposes: check that all the state is valid.
     */
    public void validate() {
        validate(new Stack());
    }
    
    public void validate(Stack<Integer> location) {
    }
}
