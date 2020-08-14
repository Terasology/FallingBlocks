// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.arrays;

import java.util.*;
import org.terasology.fallingblocks.Pair;
import org.terasology.fallingblocks.TreeUtils;

/**
 * Check the behaviour of IntPairSetHeap against a simpler reimplementation. The allocation of keys depends on extra bins, so it isn't checked.
 */
public class IntPairSetHeapTest<T> extends IntPairSetHeap<T> {
    private final Map<Integer, ArrayList<Pair<Integer, T>>> records;

    public IntPairSetHeapTest(int binSize) {
        super(binSize);
        records = new HashMap<>();
    }

    @Override
    public int allocate() {
        int key = super.allocate();
        records.put(key, new ArrayList<>());
        return key;
    }

    @Override
    public void remove(int key) {
        super.remove(key);
        records.remove(key);
    }

    @Override
    public void remove(int key, int i) {
        super.remove(key, i);
        ArrayList<Pair<Integer, T>> record = records.get(key);
        record.set(i, record.get(record.size() - 1));
        record.remove(record.size() - 1);
    }

    @Override
    public int expand(int key, int n) {
        int internalSize = super.expand(key, n);
        for (int i = 0; i < n; i++) {
            records.get(key).add(new Pair<>(0, null));
        }
        TreeUtils.assrt(internalSize == records.get(key).size());
        return records.get(key).size();
    }

    @Override
    public int getA(int key, int i) {
        int internalResult = super.getA(key, i);
        int result = records.get(key).get(i).a;
        TreeUtils.assrt(internalResult == result);
        return result;
    }

    public T getB(int key, int i) {
        T internalResult = super.getB(key, i);
        T result = records.get(key).get(i).b;
        TreeUtils.assrt(internalResult == result);
        return result;
    }

    public Pair<Integer, T> get(int key, int i) {
        Pair<Integer, T> internalResult = super.get(key, i);
        Pair<Integer, T> result = records.get(key).get(i);
        TreeUtils.assrt(internalResult.equals(result));
        return result;
    }

    public void set(int key, int i, int a, T b) {
        super.set(key, i, a, b);
        records.get(key).set(i, new Pair<>(a, b));
    }

    public void setA(int key, int i, int a) {
        super.setA(key, i, a);
        ArrayList<Pair<Integer, T>> record = records.get(key);
        record.set(i, new Pair<>(a, record.get(i).b));
    }

    public void setB(int key, int i, T b) {
        super.setB(key, i, b);
        ArrayList<Pair<Integer, T>> record = records.get(key);
        record.set(i, new Pair<>(record.get(i).a, b));
    }

    public void set(int key, int i, Pair<Integer, T> x) {
        super.set(key, i, x);
        records.get(key).set(i, x);
    }

    public int getSize(int key) {
        int internalResult = super.getSize(key);
        int result = records.get(key).size();
        TreeUtils.assrt(internalResult == result);
        return result;
    }
}
