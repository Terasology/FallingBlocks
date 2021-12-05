// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.arrays;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A finite set of natural numbers optimised for finding the lowest non-member.
 */
public class SpaceTracker {
    // The represented set is [0..max) \ missing
    private int max = 0;
    private SortedSet<Integer> missing = new TreeSet<>();

    public SpaceTracker() {
    }

    public void add(int n) {
        if (n >= max) {
            for (int i = max; i < n; i++) {
                missing.add(i);
            }
            max = n + 1;
        } else {
            missing.remove(n);
        }
    }

    public void remove(int n) {
        missing.add(n);
    }

    public int peek() {
        if (!missing.isEmpty()) {
            return missing.first();
        } else {
            return max;
        }
    }

    public int next() {
        int n = peek();
        add(n);
        return n;
    }
}
