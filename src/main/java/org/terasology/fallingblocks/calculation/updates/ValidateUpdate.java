// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.calculation.updates;

import org.terasology.fallingblocks.calculation.Chain;
import org.terasology.fallingblocks.calculation.Tree;

import java.util.Collections;
import java.util.Set;

public class ValidateUpdate implements Update{
    public ValidateUpdate() {
    }

    @Override
    public Set<Chain> execute(Tree tree) {
        tree.rootNode.validate();
        return Collections.emptySet();
    }
}
