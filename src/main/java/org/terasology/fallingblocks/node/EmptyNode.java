// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.node;

import org.joml.Vector3i;
import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.Pair;
import org.terasology.fallingblocks.Tree;

import java.util.Collections;
import java.util.Set;

/**
 * Nodes containing no solid blocks.
 */
public class EmptyNode extends Node {

    private EmptyNode(int size, Tree tree) {
        this.size = size;
        this.tree = tree;
        tree.emptyNodes.put(size, this);
    }

    public static EmptyNode get(int size, Tree tree) {
        EmptyNode result = tree.emptyNodes.get(size);
        if (result != null) {
            return result;
        } else {
            return new EmptyNode(size, tree);
        }
    }

    // The list is immutable, so it's safe to return.
    @Override
    public Set<Chain> getChains() {
        return Collections.EMPTY_SET;
    }

    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        return new Pair<>(-2, null);
    }

    @Override
    public Pair<Node, Set<Chain>> removeBlock(Vector3i pos) {
        throw new RuntimeException("Trying to remove a block from an empty node.");
    }

    @Override
    public Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> insertFullNode(Vector3i pos, FullNode node,
                                                                             Set<Pair<Integer, Node>> siblings) {
        if (size == node.size) {
            return replaceWithFullNode(node, siblings);
        } else {
            return equivalentInternalNode().insertFullNode(pos, node, siblings);
        }
    }

    /**
     * Replace an UnloadedNode with something else.
     */
    @Override
    public Set<Chain> insertNewChunk(Node newNode, Vector3i pos) {
        throw new RuntimeException("Trying to add already loaded chunk.");
    }

    private InternalNode equivalentInternalNode() {
        Node[] children = new Node[8];
        Node child = get(size / 2, tree);
        for (int i = 0; i < 8; i++) {
            children[i] = child;
        }
        return new InternalNode(size, children, tree);
    }
}
