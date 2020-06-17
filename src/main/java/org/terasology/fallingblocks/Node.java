// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
    
    /**
     * Returns the component the new block ended up in, and which surfaces the new block is exposed to.
     */
    public abstract Pair<Component, Set<Integer>> addBlock(Vector3i pos);
    
    /**
     * Replace an UnloadedNode with something else.
     */
    public abstract Set<Component> insertNewChunk(Node newNode, Vector3i pos);
    
    /**
     * Replace something else with an UnloadedNode.
     */
    public abstract Pair<Node, Component> removeChunk(Vector3i pos, int chunkSize);
    
    /**
     * For debugging purposes: check that all the state is valid.
     */
    public void validate() {
    }
}
