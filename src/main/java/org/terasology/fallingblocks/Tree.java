// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.terasology.fallingblocks.arrays.IntPairSetHeap;
import org.terasology.fallingblocks.arrays.IntPairSetHeapTest;
import org.terasology.fallingblocks.node.EmptyNode;
import org.terasology.fallingblocks.node.Node;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.chunks.ChunkConstants;

public class Tree {
    public Node rootNode = null;
    public Vector3i rootNodePos = null;
    public static final int CHUNK_NODE_SIZE = ChunkConstants.SIZE_X; //This actually needs to be the minimum of SIZE_X, SIZE_Y and SIZE_Z, but it's assumed later that SIZE_Y >= SIZE_X = SIZE_Z anyway.
    public static final int ROOT_OFFSET = 0xAAAAAAA0; //The octree structure divides at different levels in fixed locations. This constant is chosen so that, as far as possible, the highest-level divisions are far from the origin, so that the root node isn't likely to need to be very large just because the relevant region overlaps one of the divisions.

    // All EmptyNodes with the same size and tree are identical, so the same object is used.
    public final Map<Integer, EmptyNode> emptyNodes = new HashMap<>();

    // For all of the chains, the chains in sub-nodes that compose them, and the octants they're in
    IntPairSetHeap<Chain> subchains = new IntPairSetHeap<>(8);

    // For all the chains, the chains in adjacent nodes of the same size that they touch, and the directions to them.
    IntPairSetHeap<Chain> touching = new IntPairSetHeap<>(6);

    public boolean isWithinRootNode(Vector3i pos) {
        return rootNodePos != null
                && pos.x >= rootNodePos.x
                && pos.y >= rootNodePos.y
                && pos.z >= rootNodePos.z
                && pos.x < rootNodePos.x + rootNode.size
                && pos.y < rootNodePos.y + rootNode.size
                && pos.z < rootNodePos.z + rootNode.size;
    }
}
