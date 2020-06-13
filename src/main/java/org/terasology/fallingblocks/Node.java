// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.terasology.math.geom.Vector3i;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;

/**
 * An octree node storing data on the connected components of the solid blocks within its region.
 * In order to avoid having a separate node for every single block, each node doesn't know its own location.
 */
public abstract class Node {
    // Does the block count as solid for the purposes of connectivity?
    // I'm avoiding inlining this because I'm not sure if it'll have to be changed at some point.
    public static boolean isSolid(Block block) {
        return block.isAttachmentAllowed();
    }
    
    /**
     * Produce either a new node representing the given region, or null if the region has no solid blocks.
     */
    public static Node buildNode(WorldProvider world, int size, int x, int y, int z) {
        if(size == 1) {
            if(isSolid(world.getBlock(x,y,z))) {
                return LeafNode.node;
            } else {
                return null;
            }
        } else {
            Node[] children = new Node[8];
            boolean anyChildren = false;
            int i=0;
            for(int cx=0; cx<2; cx++) {
                for(int cy=0; cy<2; cy++) {
                    for(int cz=0; cz<2; cz++) {
                        children[i] = buildNode(world, size/2, x+cx*size/2, y+cy*size/2, z+cz*size/2);
                        if(children[i] != null) {
                            anyChildren = true;
                        }
                        i++;
                    }
                }
            }
            if(anyChildren) {
                return new InternalNode(size, children);
            } else {
                return null;
            }
        }
    }
    
    /**
     * Produce a new node containing a single solid block at the specified position.
     */
    public static Node buildSingletonNode(int size, Vector3i pos) {
        if(size == 1) {
            return LeafNode.node;
        } else {
            Node[] children = new Node[8];
            Node child = buildSingletonNode(size/2, modVector(pos, size/2));
            children[octantOfPosition(size, pos)] = child;
            return new InternalNode(size, children);
        }
    }
    
    /**
     * Is the octant with the given index in the further side-wards half
     */
    public static boolean isOctantOnSide(int octant, int side) {
        return side > 0 ? ((side & octant) != 0) : (((-side) & octant) == 0);
    }
    
    /**
     * Returns 0 if the sibling octants aren't adjacent, and the direction from octant1 to octant2 if they are.
     */
    public static int isAdjacent(int octant1, int octant2) {
        return Integer.bitCount(octant1 ^ octant2) == 1 ? octant2 - octant1 : 0;
    }
    
    /**
     * Tests the adjacency of child nodes of adjacent nodes, adjacent in the specified direction, or the same node it the direction is 0.
     */
    public static int isAdjacent(int octant1, int octant2, int direction) {
        if(direction == 0) {
            return isAdjacent(octant1, octant2);
        }
        return (isOctantOnSide(octant1, direction) && isOctantOnSide(octant2, -direction) && octant1-direction == octant2) ? direction : 0;
    }
    
    /**
     * In a node with the given size, which octant would the given position be in?
     */
    public static int octantOfPosition(int size, Vector3i pos) {
        return (pos.x >= size/2 ? 4 : 0) + (pos.y >= size/2 ? 2 : 0) + (pos.z >= size/2 ? 1 : 0);
    }
    
    public static boolean isPositionInternal(int size, Vector3i pos) {
        return (pos.x > 0) && (pos.y > 0) && (pos.z > 0)
               && (pos.x < size-1) && (pos.y < size-1) && (pos.z < size-1);
    }
    
    public static Vector3i modVector(Vector3i v, int s) {
        return new Vector3i((v.x % s + s) % s, (v.y % s + s) % s, (v.z % s + s) % s);
    }
    
    public abstract List<Component> getComponents();
    
    public abstract Stream<Set<Vector3i>> getInternalPositions(Vector3i pos);
    
    public abstract Pair<Node, Pair<Set<Component>, Component>> removeBlock(Vector3i pos);
    
    /**
     * Returns the component the new block ended up in, and which surfaces the new block is exposed to.
     */
    public abstract Pair<Component, Set<Integer>> addBlock(Vector3i pos);
}
