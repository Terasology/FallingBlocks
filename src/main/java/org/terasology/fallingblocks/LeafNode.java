// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.terasology.math.geom.Vector3i;

/**
 * A singleton representing a single solid block.
 */
public class LeafNode extends Node {
    public static final LeafNode node = new LeafNode();
    private final List<Component> components = Arrays.asList((Component)null);
    
    private LeafNode() {
    }
    
    // The list is immutable, so it's safe to return.
    @Override
    public List<Component> getComponents() {
        return components;
    }
    
    @Override
    public Stream<Set<Vector3i>> getInternalPositions(Vector3i pos){
        return Stream.empty();
    }
    
    @Override
    public Pair<Node, Pair<Set<Component>, Component>> removeBlock(Vector3i pos) {
        return new Pair(null, null);
    }
    
    @Override
    public Pair<Component, Set<Integer>> addBlock(Vector3i pos) {
        throw new RuntimeException("Trying to add a block to a leaf node even though it should already have a block there.");
    }
}
