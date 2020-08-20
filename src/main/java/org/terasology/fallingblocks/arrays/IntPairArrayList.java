// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.arrays;

import org.terasology.fallingblocks.Pair;

/**
 * An extendable list of pairs of an int and an object.
 * Despite the name, this behaves differently from ArrayList when `set` is called outside the current range. This class will simply expand the range to match, as that's what's most useful here.
 */
public class IntPairArrayList<T> {
    private static final float EXPANSION_FACTOR = 1.5f;
    private int[] as;
    private Object[] bs;
    private int size;
    
    public IntPairArrayList() {
        as = new int[16];
        bs = new Object[16];
        size = 0;
    }

    public int getA(int i) {
        if (i < 0 || i >= size) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        return as[i];
    }

    public T getB(int i) {
        if (i < 0 || i >= size) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        return (T) bs[i];
    }

    public Pair<Integer, T> get(int i) {
        if (i < 0 || i >= size) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        return new Pair<>(as[i], (T) bs[i]);
    }

    public void set(int i, int a, T b) {
        if (i >= size) {
            expandToInclude(i);
        }
        if (i < 0) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        as[i] = a;
        bs[i] = b;
    }

    public void setA(int i, int a) {
        if (i >= size) {
            expandToInclude(i);
        }
        if (i < 0) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        as[i] = a;
    }

    public void setB(int i, T b) {
        if (i >= size) {
            expandToInclude(i);
        }
        if (i < 0) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        bs[i] = b;
    }

    public void set(int i, Pair<Integer, T> x) {
        if (i >= size) {
            expandToInclude(i);
        }
        if (i < 0) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        as[i] = x.a;
        bs[i] = x.b;
    }

    private void expandToInclude(int i) {
        size = i + 1;
        if (size > as.length) {
            int arraySize = (int) (size * EXPANSION_FACTOR);
            int[] oldAs = as;
            Object[] oldBs = bs;
            as = new int[arraySize];
            bs = new Object[arraySize];
            System.arraycopy(oldAs, 0, as, 0, oldAs.length);
            System.arraycopy(oldBs, 0, bs, 0, oldBs.length);
        }
    }
}
