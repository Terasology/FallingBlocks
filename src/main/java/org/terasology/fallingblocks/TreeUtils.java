// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import org.joml.Vector3i;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.fallingblocks.node.EmptyNode;
import org.terasology.fallingblocks.node.InternalNode;
import org.terasology.fallingblocks.node.Node;
import org.terasology.fallingblocks.node.SolidNode;
import org.terasology.fallingblocks.node.UnloadedNode;

import static org.terasology.fallingblocks.Tree.CHUNK_NODE_SIZE;

public final class TreeUtils {
    public static final int[] DIRECTIONS = new int[]{-4, -2, -1, 1, 2, 4};

    private TreeUtils() {
    }

    // Does the block count as solid for the purposes of connectivity?
    // I'm avoiding inlining this because I'm not sure if it'll have to be changed at some point.
    public static boolean isSolid(Block block) {
        return !block.isPenetrable();
    }

    public static boolean[] extractChunkData(WorldProvider world, Vector3i pos) {
        boolean[] data = new boolean[CHUNK_NODE_SIZE * CHUNK_NODE_SIZE * CHUNK_NODE_SIZE];
        for (int x = 0; x < CHUNK_NODE_SIZE; x++) {
            for (int y = 0; y < CHUNK_NODE_SIZE; y++) {
                for (int z = 0; z < CHUNK_NODE_SIZE; z++) {
                    data[(x * CHUNK_NODE_SIZE + y) * CHUNK_NODE_SIZE + z] = isSolid(world.getBlock(pos.x + x, pos.y + y, pos.z + z));
                }
            }
        }
        return data;
    }

    /**
     * Produce a new node representing the given region.
     */
    public static Node buildNode(Tree tree, boolean[] data, int size, Vector3i pos) {
        if (size == 1) {
            if (data[(pos.x * CHUNK_NODE_SIZE + pos.y) * CHUNK_NODE_SIZE + pos.z]) {
                return new SolidNode(1, tree);
            } else {
                return EmptyNode.get(1, tree);
            }
        } else {
            Node[] children = new Node[8];
            boolean empty = true;
            boolean solid = true;
            int i = 0;
            for (int cx = 0; cx < 2; cx++) {
                for (int cy = 0; cy < 2; cy++) {
                    for (int cz = 0; cz < 2; cz++) {
                        children[i] = buildNode(tree, data, size / 2, new Vector3i(cx, cy, cz).mul(size / 2).add(pos));
                        empty = empty && children[i] instanceof EmptyNode;
                        solid = solid && children[i] instanceof SolidNode;
                        i++;
                    }
                }
            }
            if (empty) {
                return EmptyNode.get(size, tree);
            } else if (solid) {
                for (Node child : children) {
                    // There are no references to these, but they still need to have their IDs in the IntPairSetHeaps revoked.
                    ((SolidNode) child).getChain().inactivate(false);
                }
                return new SolidNode(size, tree);
            } else {
                return new InternalNode(size, children, tree);
            }
        }
    }

    /**
     * Produce a new node that is all unloaded except for one preexisting child node.
     */
    public static Node buildExpandedNode(Tree tree, Node old, Vector3i pos, int size) {
        Node oldCopy = old;
        if (oldCopy.size == size) {
            return oldCopy;
        } else if (oldCopy.size < size / 2) {
            oldCopy = buildExpandedNode(tree, oldCopy, modVector(pos, size / 2), size / 2);
        }
        Node[] children = new Node[8];
        int octant = octantOfPosition(pos, size);
        for (int i = 0; i < 8; i++) {
            if (i == octant) {
                children[i] = oldCopy;
            } else {
                children[i] = new UnloadedNode(size / 2, tree);
            }
        }
        return new InternalNode(size, children, tree);
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
        if (direction == 0) {
            return isAdjacent(octant1, octant2);
        }
        return (isOctantOnSide(octant1, direction) && isOctantOnSide(octant2, -direction)
                && octant1 - direction == octant2) ? direction : 0;
    }

    /**
     * In a node with the given size, which octant would the given position be in?
     */
    public static int octantOfPosition(Vector3i pos, int size) {
        return (pos.x >= size / 2 ? 4 : 0) + (pos.y >= size / 2 ? 2 : 0) + (pos.z >= size / 2 ? 1 : 0);
    }

    /**
     * Does a cube of size `innerSize` at position `pos` inside a larger cube of size `size` touch side `side`?
     */
    public static boolean isPositionOnSide(Vector3i pos, int side, int innerSize, int size) {
        switch (side) {
            case -4:
                return pos.x == 0;
            case -2:
                return pos.y == 0;
            case -1:
                return pos.z == 0;
            case 1:
                return pos.z == size - innerSize;
            case 2:
                return pos.y == size - innerSize;
            case 4:
                return pos.x == size - innerSize;
            default:
                throw new IllegalArgumentException(side + " is not a valid side.");
        }
    }

    public static boolean isPositionInternal(Vector3i pos, int size) {
        return (pos.x > 0) && (pos.y > 0) && (pos.z > 0)
                && (pos.x < size - 1) && (pos.y < size - 1) && (pos.z < size - 1);
    }

    public static Vector3i modVector(Vector3i v, int s) {
        return new Vector3i((v.x % s + s) % s, (v.y % s + s) % s, (v.z % s + s) % s);
    }

    public static Vector3i octantVector(int octant, int size) {
        return new Vector3i(isOctantOnSide(octant, 4) ? size : 0,
                isOctantOnSide(octant, 2) ? size : 0,
                isOctantOnSide(octant, 1) ? size : 0);
    }

    public static void assrt(boolean valid) {
        if (!valid) {
            throw new AssertionError();
        }
    }

    public static void assrt(boolean valid, Object message) {
        if (!valid) {
            throw new AssertionError(message);
        }
    }
}
