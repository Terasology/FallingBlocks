// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.arrays;

import org.terasology.fallingblocks.Pair;
import org.terasology.fallingblocks.TreeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Check the behaviour of IntPairSetHeap against a simpler reimplementation. The allocation of keys depends on extra bins, so it isn't
 * checked.
 */
public class IntPairSetHeapTest<T> extends IntPairSetHeap<T> {
    private final IntPairSetHeap<T> internal;
    private final Map<Integer, ArrayList<Pair<Integer, T>>> records;

    public IntPairSetHeapTest(int binSize) {
        super(binSize); //None of the usual datastructures in this instance are used. It only subclasses IntPairSetHeap to get the
        // interface.
        internal = new IntPairSetHeap<>(binSize);
        records = new HashMap<>();
    }

    @Override
    public int allocate() {
        int key = internal.allocate();
        records.put(key, new ArrayList<>());
        return key;
    }

    @Override
    public void remove(int key) {
        internal.remove(key);
        records.remove(key);
    }

    @Override
    public void remove(int key, int i) {
        internal.remove(key, i);
        ArrayList<Pair<Integer, T>> record = records.get(key);
        record.set(i, record.get(record.size() - 1));
        record.remove(record.size() - 1);
    }

    @Override
    public int expand(int key, int n) {
        int internalSize = internal.expand(key, n);
        for (int i = 0; i < n; i++) {
            records.get(key).add(new Pair<>(0, null));
        }
        TreeUtils.assrt(internalSize == records.get(key).size());
        return records.get(key).size();
    }

    @Override
    public int getA(int key, int i) {
        int internalResult = internal.getA(key, i);
        int result = records.get(key).get(i).a;
        TreeUtils.assrt(internalResult == result);
        return result;
    }

    public T getB(int key, int i) {
        T internalResult = internal.getB(key, i);
        T result = records.get(key).get(i).b;
        TreeUtils.assrt(internalResult == result);
        return result;
    }

    public Pair<Integer, T> get(int key, int i) {
        Pair<Integer, T> internalResult = internal.get(key, i);
        Pair<Integer, T> result = records.get(key).get(i);
        TreeUtils.assrt(internalResult.equals(result));
        return result;
    }

    public void set(int key, int i, int a, T b) {
        internal.set(key, i, a, b);
        records.get(key).set(i, new Pair<>(a, b));
    }

    public void setA(int key, int i, int a) {
        internal.setA(key, i, a);
        ArrayList<Pair<Integer, T>> record = records.get(key);
        record.set(i, new Pair<>(a, record.get(i).b));
    }

    public void setB(int key, int i, T b) {
        internal.setB(key, i, b);
        ArrayList<Pair<Integer, T>> record = records.get(key);
        record.set(i, new Pair<>(record.get(i).a, b));
    }

    public void set(int key, int i, Pair<Integer, T> x) {
        internal.set(key, i, x);
        records.get(key).set(i, x);
    }

    public int getSize(int key) {
        int internalResult = internal.getSize(key);
        int result = records.get(key).size();
        TreeUtils.assrt(internalResult == result);
        return result;
    }

    public IntPairSetIterator iterator(int key) {
        return this.new IntPairSetIteratorTest(key);
    }

    private class IntPairSetIteratorTest extends IntPairSetIterator {
        IntPairSetIterator internalIterator;
        Iterator<Pair<Integer, T>> reference;

        IntPairSetIteratorTest(int key) {
            super(-1);
            internalIterator = internal.iterator(key);
            if (key == -1) {
                reference = Collections.EMPTY_LIST.iterator();
            } else {
                reference = records.get(key).iterator();
            }
        }

        @Override
        public boolean hasNext() {
            boolean result = internalIterator.hasNext();
            boolean expected = reference.hasNext();
            TreeUtils.assrt(result == expected);
            return result;
        }

        @Override
        public Pair<Integer, T> next() {
            Pair<Integer, T> result = internalIterator.next();
            Pair<Integer, T> expected = reference.next();
            TreeUtils.assrt(result.equals(expected));
            return result;
        }
    }
}
