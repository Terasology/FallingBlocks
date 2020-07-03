// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.node;

import java.util.*;

import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.Pair;
import org.terasology.math.geom.Vector3i;

/**
 * An octree node storing data on the connected components of the solid blocks within its region.
 * In order to avoid having a separate node for every single block, each node doesn't know its own location.
 */
public abstract class Node {
    public int size;
    
    public abstract Set<Chain> getChains();
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    public abstract Pair<Integer, Node> canShrink();
    
    public abstract Pair<Node, Set<Chain>> removeBlock(Vector3i pos);
    
    public Pair<Node, Chain> addBlock(Vector3i pos) {
        Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> result = insertFullNode(pos, new SolidNode(1), new HashSet<>());
        return new Pair<>(result.a, result.b.a);
    }
    
    /**
     * Replace a node with one of the same size that is entirely solid (i.e. SolidNode
     * or UnloadedNode).
     * 
     * @param pos      The position where the node is added, relative to this node.
     * @param node     The node to add
     * @param siblings The nodes adjacent to the new block, with the same size as this.
     * @return The node to replace this with, the chain containing the new block, and the adjacent chains.
     */
    abstract Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> insertFullNode(Vector3i pos, FullNode node, Set<Pair<Integer, Node>> siblings);

    /**
     * Like insertFullNode, but assuming that this node is the same size, and is therefore replaced entirely.
     */
    public Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> replaceWithFullNode(FullNode node, Set<Pair<Integer, Node>> siblings) {
        if (node.size != size) {
            throw new IllegalArgumentException("replaceWithFullNode is only for nodes of the same size.");
        }
        //logger.info("Replacing Node, size "+size);
        Chain chain = node.getChain();
        if (!getChains().isEmpty()) {
            Chain firstOldChain = getChains().iterator().next();
            Set<Chain> tempChains = new HashSet<>(getChains());
            for (Chain oldChain : tempChains) {
                if (oldChain != firstOldChain) {
                    firstOldChain.merge(oldChain);
                }
            }
            firstOldChain.inactivate(true);
        }
        Set<Pair<Integer, Chain>> nextTouching = new HashSet<>();
        for (Pair<Integer, Node> siblingPair : siblings) {
            int direction = siblingPair.a;
            Node sibling = siblingPair.b;
            for (Chain siblingChain : sibling.getChains()) {
                if (chain.updateTouching(siblingChain, direction)) {
                    nextTouching.add(new Pair<>(direction, siblingChain));
                }
            }
        }
        return new Pair<>(node, new Pair<>(chain, nextTouching));
    }
    
    /**
     * Replace an UnloadedNode with something else.
     */
    public abstract Set<Chain> insertNewChunk(Node newNode, Vector3i pos);
    
    /**
     * Replace something else with an UnloadedNode.
     */
    public Pair<Node, Chain> removeChunk(Vector3i pos, int chunkSize) {
        Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> result = insertFullNode(pos, new UnloadedNode(chunkSize), new HashSet<>());
        return new Pair<>(result.a, result.b.a);
    }
    
    /**
     * For debugging purposes: check that all the state is valid.
     */
    public void validate() {
        validate(new Stack<>());
    }
    
    public void validate(Stack<Integer> location) {
    }
}
