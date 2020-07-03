// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.node;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.Pair;
import org.terasology.fallingblocks.TreeUtils;
import org.terasology.math.geom.Vector3i;

public class InternalNode extends Node {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalNode.class);
    
    // The child nodes, in the order:
    // -x-y-z, -x-y+z, -x+y-z, -x+y+z, +x-y-z, +x-y+z, +x+y-z, +x+y+z
    // The cardinal directions have corresponding labels:
    // +x: 4, +y: 2, +z: 1, -x: -4, -y: -2, -z: -1
    public Node[] children;
    
    // The connected components of the solid blocks in this region
    private Set<Chain> chains;
    
    public InternalNode(int size, Node[] children) {
        this.size = size;
        this.children = children;
        
        Set<Pair<Integer, Chain>> subChains = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            for (Chain c : children[i].getChains()) {
                subChains.add(new Pair<>(i, c));
            }
        }
        
        chains = new HashSet<>();
        
        // DFS to construct the Chains, and update the touching sets of the subchains at the same time. The search is arranged a little non-standardly in order to catch all the touching pairs.
        Stack<Pair<Integer, Chain>> stack = new Stack<>();
        while (!subChains.isEmpty()) {
            Set<Pair<Integer, Chain>> rawChain = new HashSet<>();
            Pair<Integer, Chain> first = subChains.iterator().next();
            rawChain.add(first);
            stack.push(first);
            while (!stack.isEmpty()) {
                Pair<Integer, Chain> current = stack.pop();
                for (Pair<Integer, Chain> next : subChains) {
                    int direction = TreeUtils.isAdjacent(current.a, next.a);
                    if (direction != 0 && current.b.updateTouching(next.b, direction)) {
                        if (!rawChain.contains(next)) {
                            rawChain.add(next);
                            stack.push(next);
                        }
                    }
                }
            }
            subChains.removeAll(rawChain);
            
            Chain chain = new Chain(rawChain, null, this);
            chains.add(chain);
            for (Pair<Integer, Chain> sc : rawChain) {
                sc.b.parent = chain;
            }
        }
    }
    
    @Override
    public Set<Chain> getChains() {
        return chains;
    }
    
    @Override
    public Pair<Node, Set<Chain>> removeBlock(Vector3i pos) {
        int octant = TreeUtils.octantOfPosition(pos, size);
        Vector3i subPosition = TreeUtils.modVector(pos, size/2);
        Pair<Node, Set<Chain>> childResult = children[octant].removeBlock(subPosition);
        children[octant] = childResult.a;
        return new Pair<>(chains.isEmpty() ? EmptyNode.get(size) : this, childResult.b);
    }
    
    /**
     * @param pos      The position where the block is added, relative to this node.
     * @param siblings The nodes adjacent to the new block, with the same size as this.
     * @return The node to replace this with, the chain containing the new block, and the adjacent chains.
     */
    @Override
    public Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> insertFullNode(Vector3i pos, FullNode node, Set<Pair<Integer, Node>> siblings) {
        if (size == node.size) {
            return replaceWithFullNode(node, siblings);
        }
        
        int octant = TreeUtils.octantOfPosition(pos, size);
        Vector3i subPosition = TreeUtils.modVector(pos, size/2);
        //logger.info("Inserting node of size "+node.size+", this node size = "+size+", block in octant "+octant+" with position "+pos+" and subPosition "+subPosition+".");
        Set<Pair<Integer, Node>> nextSiblings = new HashSet<>();
        for (int i = 0; i < TreeUtils.DIRECTIONS.length; i++) {
            int side = TreeUtils.DIRECTIONS[i];
            //logger.info("Trying new sibling on side "+side);
            if (TreeUtils.isOctantOnSide(octant, -side) && TreeUtils.isPositionOnSide(subPosition, side, node.size, size/2)) {
                //logger.info("Correct position.");
                if (!(children[octant+side] instanceof EmptyNode)) {
                    //logger.info("New sibling  on side "+side+", "+children[octant+side].getClass());
                    nextSiblings.add(new Pair<>(side, children[octant+side]));
                }
            }
        }
        for (Pair<Integer, Node> siblingPair : siblings) {
            int side = siblingPair.a;
            Node sibling = siblingPair.b;
            TreeUtils.assrt(sibling.size == size);
            TreeUtils.assrt(TreeUtils.isOctantOnSide(octant, side));
            if (sibling instanceof InternalNode) {
                Node child = ((InternalNode) sibling).children[octant-side];
                //logger.info("Existing sibling on side "+side+", "+child.getClass());
                if (!(child.getChains().isEmpty())) {
                    nextSiblings.add(new Pair<>(side, child));
                }
            }
        }
        
        Pair<Node, Pair<Chain, Set<Pair<Integer, Chain>>>> p = children[octant].insertFullNode(subPosition, node, nextSiblings);
        children[octant] = p.a;
        Chain newChain = p.b.a;
        Set<Pair<Integer, Chain>> touching = p.b.b;

        if (newChain.parent == null) { // The block hasn't merged into any existing chains.
            chains.add(new Chain(octant, newChain, this));
        }
        
        Set<Pair<Integer, Chain>> nextTouching = new HashSet<>();

        //logger.info("Back to size "+size);
        for (Pair<Integer, Node> sibling : siblings) {
            if (sibling.b instanceof FullNode) {
                nextTouching.add(new Pair<>(sibling.a, ((FullNode) sibling.b).getChain()));
            }
        }
        for (Pair<Integer, Chain> t : touching) {
            Chain touchingChain = t.b.parent;
            TreeUtils.assrt(touchingChain != null); //If this is the root node, touching is already empty.
            if (touchingChain == newChain.parent) {
                //logger.info("touching on side "+t.a+" superfluous.");
                continue;
            } else if (touchingChain.node == this) {
                //logger.info("touching on side "+t.a+" needs merging.");
                touchingChain.merge(newChain.parent);
                //newChain.parent is set to touchingChain.
            } else {
                //logger.info("touching on side "+t.a+" needs recording, carrying up.");
                nextTouching.add(new Pair<>(t.a, touchingChain));
            }
        }
        //logger.info("After merging down, "+chains.size()+" chains left.");

        for (Pair<Integer, Chain> t : nextTouching) {
            TreeUtils.assrt(newChain.parent.baseIsTouching(t.b, t.a));
            TreeUtils.assrt(t.b.baseIsTouching(newChain.parent,-t.a));
            newChain.parent.touching.add(t);
            t.b.touching.add(new Pair<>(-t.a, newChain.parent));
            //logger.info("Recording touch, side "+t.a);
        }

        boolean uniformClass = true;
        for (int i = 1; i < 8; i++) {
            if (children[i].getClass() != children[0].getClass()) {
                uniformClass = false;
            }
        }
        if (uniformClass && (children[0] instanceof FullNode)) {
            //logger.info("Replacing with "+children[0].getClass()+". size "+size);
            Set<Pair<Integer, Node>> altSiblings = new HashSet<>();
            for (Pair<Integer, Chain> t : newChain.parent.touching) {
                altSiblings.add(new Pair<>(t.a, t.b.node));
            }
            // This larger replacement node may be touching nodes farther away than those in the siblings set, so a new version is necessary. This seems a little dodgy in that it breaks a few of the general assumptions in how insertFullNode works, but it should be okay.
            return replaceWithFullNode(node.getSimilar(size), altSiblings);
        }

        return new Pair<>(this, new Pair<>(newChain.parent, nextTouching));
    }
    
    /**
     * Returns an octant if there's only one loaded child node, -1 if there are none, and -2 if there are multiple.
     */
    @Override
    public Pair<Integer, Node> canShrink() {
        int resultIndex = -1;
        Node resultNode = null;
        for (int i = 0; i < 8; i++) {
            if (!(children[i] instanceof UnloadedNode)) {
                if (resultIndex == -1) {
                    resultIndex = i;
                    resultNode = children[i];
                } else {
                    resultIndex = -2;
                    resultNode = null;
                }
            }
        }
        return new Pair<>(resultIndex, resultNode);
    }
    
    /**
     * Replace an UnloadedNode with something else.
     */
    @Override
    public Set<Chain> insertNewChunk(Node newNode, Vector3i pos) {
        //logger.info("Inserting at relative position "+pos+". Node size = "+size+". Octant = "+TreeUtils.octantOfPosition(pos, size)+".");
        if (newNode.size >= size) {
            logger.warn("Adding already loaded chunk.");
            return Collections.EMPTY_SET;
        }
        int octant = TreeUtils.octantOfPosition(pos, size);
        Node oldChild = children[octant];
        if (oldChild instanceof UnloadedNode) {
            children[octant] = TreeUtils.buildExpandedNode(newNode, TreeUtils.modVector(pos, size/2), size/2);
            Chain chain = ((UnloadedNode) oldChild).getChain();
            TreeUtils.assrt(chain.isActive());
            TreeUtils.assrt(chain.parent.isActive());
            chain.parent.subchains.removeIf((Pair<Integer, Chain> subchain) -> subchain.a == octant);
            for (Chain childChain : children[octant].getChains()) {
                childChain.parent = chain.parent;
                chain.parent.subchains.add(new Pair<>(octant, childChain));
                for (Pair<Integer, Chain> t : chain.touching) {
                    childChain.updateTouching(t.b, t.a);
                }
            }
            chain.inactivate(false);
            return chain.parent.checkConnectivity();
        } else {
            return oldChild.insertNewChunk(newNode, TreeUtils.modVector(pos, size/2));
        }
    }
    
    @Override
    public void validate(Stack<Integer> location) {
        for (int i = 0; i < 8; i++) {
            TreeUtils.assrt(children[i].size == size/2);
            for (Chain chain : children[i].getChains()) {
                if (chain != null) {
                    TreeUtils.assrt(chain.parent.node == this);
                    TreeUtils.assrt(chains.contains(chain.parent), size);
                    for (int j = 0; j < 8; j++) {
                        int direction = TreeUtils.isAdjacent(i, j);
                        if (direction != 0) {
                            for (Chain chain2 : children[j].getChains()) {
                                Chain.validateTouching(chain, chain2, direction);
                            }
                        }
                    }
                }
            }
            location.push(i);
            children[i].validate(location);
            location.pop();
        }
        for (Chain chain : chains) {
            chain.validate(location);
            TreeUtils.assrt(chain.node == this);
            for (Chain otherChain : chains) {
                TreeUtils.assrt(chain == otherChain || !chain.isTouching(otherChain, 0));
            }
        }
    }
}
