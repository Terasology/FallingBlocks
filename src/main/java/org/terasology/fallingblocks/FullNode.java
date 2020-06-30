// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.math.geom.Vector3i;

/**
 * Nodes entirely full with one component.
 */
public abstract class FullNode extends Node {
    
    private static final Logger logger = LoggerFactory.getLogger(FullNode.class);
    
    Set<Component> components;
    Component component;
    
    @Override
    public Set<Component> getComponents() {
        return components;
    }
    
    public Component getComponent() {
        return component;
    }
    
    public abstract FullNode getSimilar(int size);
    
    @Override
    public Pair<Node, Pair<Component, Set<Pair<Integer, Component>>>> insertFullNode(Vector3i pos, FullNode node, Set<Pair<Integer, Node>> siblings) {
        if(node.size == size) {
            // the siblings argument isn't needed here because as a FullNode, this is necessarily touching every possible neighbour already, so using component.touching is simpler.
            logger.info("Reached the leaf "+getClass()+" of size "+size);
            Set<Pair<Integer, Component>> touching = new HashSet(component.touching);
            component.replaceWith(node.getComponent());
            return new Pair(node, new Pair(node.getComponent(), touching));
        } else {
            logger.info("Inserting in full node of size "+size+". Replacing.");
            return equivalentInternalNode().insertFullNode(pos, node, siblings);
        }
    }
    
    /**
     * Replace an UnloadedNode with something else.
     */
    @Override
    public Set<Component> insertNewChunk(Node newNode, Vector3i pos) {
        throw new RuntimeException("Trying to insert new chunk in a leaf node. Node can't replace itself.");
    }
    
    public InternalNode equivalentInternalNode() {
        Node[] children = new Node[8];
        for(int i=0; i<8; i++) {
            children[i] = getSimilar(size/2);
        }
        InternalNode replacementNode = new InternalNode(size, children);
        Component replacementComponent = replacementNode.getComponents().iterator().next();
        TreeUtils.assrt(replacementNode.getComponents().size() == 1);
        if(size > 2) {
            for(Pair<Integer, Component> touching : component.touching) {
                if(touching.b.subcomponents != null) {
                    for(Pair<Integer, Component> subcomponent : touching.b.subcomponents) {
                        if(TreeUtils.isOctantOnSide(subcomponent.a, -touching.a)) {
                            Component neighbour = ((FullNode)children[touching.a+subcomponent.a]).getComponent();
                            subcomponent.b.touching.add(new Pair(-touching.a, neighbour));
                            neighbour.touching.add(new Pair(touching.a, subcomponent.b));
                        }
                    }
                }
            }
        }
        component.replaceWith(replacementComponent);
        logger.info("Splitting node of type "+getClass()+", size "+size);
        return replacementNode;
    }
}
