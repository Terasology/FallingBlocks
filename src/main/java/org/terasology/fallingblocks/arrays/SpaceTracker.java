// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.arrays;

/**
 * A finite set of natural numbers optimised for finding the lowest non-member.
 */
public class SpaceTracker {
    // The bits of each int store whether 32 consecutive numbers are members of the set, 1 for member 0 for non-member, LSB first.
    private int[] leaves = new int[]{0};
    // For any aligned power-of-2-sized block, the value in the middle (rounding up) is whether all the numbers in that range are members. The first element is unused, for consistency.
    private boolean[] full = new boolean[]{false, false};
    // The logarithm of the number of leaves
    private int maxLevel = 0;

    public SpaceTracker() {
    }

    public void add(int n) {
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        while (n / 32 >= leaves.length) {
            int[] tempLeaves = leaves;
            leaves = new int[tempLeaves.length * 2];
            System.arraycopy(tempLeaves, 0, leaves, 0, tempLeaves.length);
            boolean[] tempFull = full;
            full = new boolean[tempFull.length * 2];
            System.arraycopy(tempFull, 0, full, 0, tempFull.length);
            maxLevel++;
        }
        int i = n / 32;
        leaves[i] |= 1 << (n % 32);
        if (leaves[i] == -1) {
            i = i * 2 + 1; // The index into `full`
            full[i] = true;
            int level = 0;
            while (level < maxLevel && full[getSibling(i, level)]) {
                i = getParent(i, level);
                level++;
                full[i] = true;
            }
        }
    }

    public void remove(int n) {
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        int i = n / 32;
        leaves[i] &= ~(1 << (n % 32));
        i = i * 2 + 1; // The index into `full`
        int level = 0;
        while (i < full.length && full[i]) {
            full[i] = false;
            i = getParent(i, level);
            level++;
        }
    }

    public int peek() {
        int i = full.length / 2;
        int level = maxLevel;
        if (full[i]) {
            return 32 * leaves.length;
        } else {
            int left;
            int right;
            while (level > 0) {
                left = getLeftChild(i, level);
                right = getRightChild(i, level);
                level--;
                if (full[left]) {
                    i = right;
                } else {
                    i = left;
                }
            }
            i = (i - 1) / 2; // The index into `leaves`
            int leaf = leaves[i];
            int bit = 0;
            while (bit < 32) {
                if ((leaf & (1 << bit)) == 0) {
                    return i * 32 + bit;
                }
                bit++;
            }
            throw new RuntimeException("SpaceTracker in unexpected state: no empty space in leaf.");
        }
    }

    public int next() {
        int n = peek();
        add(n);
        return n;
    }

    private static int getSibling(int i, int level) {
        return i ^ (1 << (level + 1));
    }

    private static int getParent(int i, int level) {
        return i & ~(1 << level) | (1 << (level + 1));
    }

    private static int getLeftChild(int i, int level) {
        return i & ~(1 << level) | (1 << (level - 1));
    }

    private static int getRightChild(int i, int level) {
        return i | (1 << (level - 1));
    }
}
