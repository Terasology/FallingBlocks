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
    
    public abstract List<Component> getComponents();
    
    public abstract Stream<Set<Vector3i>> getInternalPositions(Vector3i pos);
    
    public abstract Pair<Node, Pair<Set<Component>, Component>> removeBlock(Vector3i pos);
    
    /**
     * Returns the component the new block ended up in, and which surfaces the new block is exposed to.
     */
    public abstract Pair<Component, Set<Integer>> addBlock(Vector3i pos);
}
