// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.calculation.node;

import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.fallingblocks.calculation.Chain;
import org.terasology.fallingblocks.calculation.Pair;
import org.terasology.fallingblocks.calculation.TreeUtils;

import java.util.Set;

/**
 * Nodes entirely full with one chain.
 */
public abstract class FullNode extends Node {
    
    private static final Logger logger = LoggerFactory.getLogger(FullNode.class);
    
    Set<Chain> chains;
    Chain chain;
    
    @Override
    public Set<Chain> getChains() {
        return chains;
    }
    
    public Chain getChain() {
        return chain;
    }
    
    public abstract FullNode getSimilar(int size);
    
    @Override
    public Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> insertFullNode(Vector3i pos, FullNode node, Set<Pair<Integer, Node>> siblings) {
        if (size == node.size) {
            // A custom implementation could be a little more efficient, but this is simpler.
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
        throw new RuntimeException("Trying to insert new chunk in a leaf node. Node can't replace itself.");
    }
    
    public InternalNode equivalentInternalNode() {
        Node[] children = new Node[8];
        for (int i = 0; i < 8; i++) {
            children[i] = getSimilar(size/2);
        }
        InternalNode replacementNode = new InternalNode(size, children, tree);
        Chain replacementChain = replacementNode.getChains().iterator().next();
        TreeUtils.assrt(replacementNode.getChains().size() == 1);
        for (Pair<Integer, Chain> touching : chain.touching()) {
            for (Pair<Integer, Chain> subchain : touching.b.subchains()) {
                if (TreeUtils.isOctantOnSide(subchain.a, -touching.a) && subchain.b.isTouching(-touching.a)) {
                    Chain neighbour = ((FullNode) children[touching.a + subchain.a]).getChain();
                    Chain.addTouching(subchain.b, neighbour, -touching.a);
                }
            }
        }
        chain.replaceWith(replacementChain);
        //logger.info("Splitting node of type "+getClass()+", size "+size);
        return replacementNode;
    }
}
