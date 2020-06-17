// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import org.terasology.math.geom.Vector3i;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;

public class TreeUtils {
    // Does the block count as solid for the purposes of connectivity?
    // I'm avoiding inlining this because I'm not sure if it'll have to be changed at some point.
    public static boolean isSolid(Block block) {
        return block.isAttachmentAllowed();
    }
    
    /**
     * Produce either a new node representing the given region, or null if the region has no solid blocks.
     */
    public static Node buildNode(WorldProvider world, int size, Vector3i pos) {
        if(size == 1) {
            if(isSolid(world.getBlock(pos))) {
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
                        children[i] = buildNode(world, size/2, new Vector3i(cx,cy,cz).scale(size/2).add(pos));
                        if(children[i] != null) {
                            anyChildren = true;
                        }
                        i++;
                    }
                }
            }
            if(anyChildren || size >= 32) { //TODO: after null nodes are replaced with AirNodes, this hacky workaround will be unnecessary.
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
            children[octantOfPosition(pos, size)] = child;
            return new InternalNode(size, children);
        }
    }
    
    /**
     * Produce a new node that is all unloaded except for one preexisting child node.
     */
    public static Node buildExpandedNode(Node old, Vector3i pos, int size) {
        if(old.size == size) {
            return old;
        } else if(old.size < size/2) {
            old = buildExpandedNode(old, modVector(pos, size/2), size/2);
        }
        Node[] children = new Node[8];
        int octant = octantOfPosition(pos, size);
        for(int i=0; i<8; i++) {
            if(i == octant) {
                children[i] = old;
            } else {
                children[i] = new UnloadedNode(size/2);
            }
        }
        return new InternalNode(size, children);
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
    public static int octantOfPosition(Vector3i pos, int size) {
        return (pos.x >= size/2 ? 4 : 0) + (pos.y >= size/2 ? 2 : 0) + (pos.z >= size/2 ? 1 : 0);
    }
    
    public static boolean isPositionInternal(Vector3i pos, int size) {
        return (pos.x > 0) && (pos.y > 0) && (pos.z > 0)
               && (pos.x < size-1) && (pos.y < size-1) && (pos.z < size-1);
    }
    
    public static Vector3i modVector(Vector3i v, int s) {
        return new Vector3i((v.x % s + s) % s, (v.y % s + s) % s, (v.z % s + s) % s);
    }
    
    public static Vector3i octantVector(int octant, int size) {
        return new Vector3i(isOctantOnSide(octant, 4) ? size : 0, isOctantOnSide(octant, 2) ? size : 0, isOctantOnSide(octant, 1) ? size : 0);
    }
}
