// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.updates;

import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.Tree;

import java.util.Collections;
import java.util.Set;

public class ValidateUpdate implements Update {
    public ValidateUpdate() {
    }

    @Override
    public Set<Chain> execute(Tree tree) {
        tree.rootNode.validate();
        return Collections.emptySet();
    }
}
