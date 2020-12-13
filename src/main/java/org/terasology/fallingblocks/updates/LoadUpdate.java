// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.updates;

import org.joml.Vector3i;
import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.Tree;
import org.terasology.fallingblocks.TreeUtils;
import org.terasology.fallingblocks.node.Node;

import java.util.Set;

public class LoadUpdate implements Update {
    boolean[] data;
    Vector3i pos;

    public LoadUpdate(boolean[] data, Vector3i pos) {
        this.data = data;
        this.pos = pos;
    }

    @Override
    public Set<Chain> execute(Tree tree) {
        Node node = TreeUtils.buildNode(tree, data, Tree.CHUNK_NODE_SIZE, new Vector3i());
        if (tree.rootNode == null) {
            //logger.info("Starting new root node.");
            tree.rootNode = node;
            tree.rootNodePos = pos;
            return tree.rootNode.getChains();
        }
        while (!tree.isWithinRootNode(pos)) {
            Vector3i relativePos = TreeUtils.modVector(new Vector3i(tree.rootNodePos).add(Tree.ROOT_OFFSET, Tree.ROOT_OFFSET, Tree.ROOT_OFFSET), tree.rootNode.size * 2);
            Vector3i newRootNodePos = new Vector3i(tree.rootNodePos).sub(relativePos);
            //logger.info("Expanding root node from "+rootNodePos+", "+rootNode.size+" to "+newRootNodePos);
            tree.rootNode = TreeUtils.buildExpandedNode(tree, tree.rootNode, relativePos, tree.rootNode.size * 2);
            tree.rootNodePos = newRootNodePos;
        }
        TreeUtils.assrt(tree.rootNode != node);
        return tree.rootNode.insertNewChunk(node, new Vector3i(pos).sub(tree.rootNodePos));
    }
}
