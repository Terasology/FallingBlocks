// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.arrays;

import java.util.*;
import org.terasology.fallingblocks.Pair;

/**
 * A set of sets of int-object-pairs, where each of the (outer) sets has a fixed int label.
 * I really ought to write some unit tests for this at some point.
 */
public class IntPairSetHeap<T> {
    private final IntPairArrayList<T> list = new IntPairArrayList<>();
    private final SpaceTracker spaceTracker = new SpaceTracker();
    private final int binSize;

    private final Map<Integer, IntPairSetIterator> busyKeys = new HashMap<>();
    private int maxSize; // For debugging purposes, check on the maximum size any of the sets have reached.

    public IntPairSetHeap(int binSize) {
        this.binSize = binSize;
    }

    public int allocate() {
        int key = spaceTracker.next();
        setSize(key, 0);
        setNext(key, -1);
        return key;
    }

    public void remove(int key) {
        busyKeys.remove(key);
        for (int binIndex = key; binIndex != -1; binIndex = getNext(binIndex)) {
            spaceTracker.remove(binIndex);
            for (int i = 0; i < binSize; i++) {
                list.set(binIndex * (binSize + 2) + i, 0, null);
            }
        }
    }

    public void remove(int key, int i) {
        int oldSize = getSize(key);
        int newSize = oldSize - 1;
        if (i != oldSize - 1) {
            set(key, i, get(key, oldSize - 1));
        }
        set(key, oldSize - 1, 0, null); // Make sure the objects can be garbage-collected.
        setSize(key, newSize);
        // The number of bins is 1 + (size - 1) / binSize.
        if ((newSize - 1) / binSize != (oldSize - 1) / binSize && newSize != 0) {
            int binIndex = key;
            for (int j = 0; j < (newSize - 1) / binSize; j++) {
                binIndex = getNext(binIndex);
            }
            spaceTracker.remove(getNext(binIndex));
            setNext(binIndex, -1);
        }
    }

    /**
     * Increase the size of one of the sets by n.
     * @return The new size
     */
    public int expand(int key, int n) {
        busyKeys.remove(key);
        int oldSize = getSize(key);
        int newSize = oldSize + n;
        maxSize = Math.max(newSize, maxSize);
        setSize(key, newSize);
        // The number of bins is 1 + (size - 1) / binSize.
        if ((newSize - 1) / binSize != (oldSize - 1) / binSize) {
            int binIndex = key;
            for (int i = 0; i < (newSize - 1) / binSize; i++) {
                int nextBin = getNext(binIndex);
                if (nextBin == -1) {
                    nextBin = allocate();
                    setNext(binIndex, nextBin);
                }
                binIndex = nextBin;
            }
        }
        return newSize;
    }

    public int getA(int key, int i) {
        int address = getAddress(key, i);
        return list.getA(address);
    }

    public T getB(int key, int i) {
        int address = getAddress(key, i);
        return list.getB(address);
    }

    public Pair<Integer, T> get(int key, int i) {
        int address = getAddress(key, i);
        return list.get(address);
    }

    public void set(int key, int i, int a, T b) {
        int address = getAddress(key, i);
        list.set(address, a, b);
    }

    public void setA(int key, int i, int a) {
        int address = getAddress(key, i);
        list.setA(address, a);
    }

    public void setB(int key, int i, T b) {
        int address = getAddress(key, i);
        list.setB(address, b);
    }

    public void set(int key, int i, Pair<Integer, T> x) {
        int address = getAddress(key, i);
        list.set(address, x);
    }

    public int getSize(int key) {
        return list.getA((key + 1) * (binSize + 2) - 1);
    }

    private void setSize(int key, int size) {
        list.setA((key + 1) * (binSize + 2) - 1, size);
    }

    private int getNext(int key) {
        return list.getA((key + 1) * (binSize + 2) - 2);
    }

    private void setNext(int key, int next) {
        list.setA((key + 1) * (binSize + 2) - 2, next);
    }

    private int getAddress(int key, int i) {
        busyKeys.remove(key);
        if (key == -1) {
            throw new NullPointerException();
        } else if (i < 0) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        int binIndex = key;
        int positionWithinBin = i;
        while (positionWithinBin >= binSize) {
            positionWithinBin -= binSize;
            binIndex = getNext(binIndex);
        }
        return binIndex * (binSize + 2) + positionWithinBin;
    }

    public IntPairSetIterator iterator(int key) {
        return this.new IntPairSetIterator(key);
    }

    protected class IntPairSetIterator implements Iterator<Pair<Integer, T>>, Iterable<Pair<Integer, T>> {
        private int key;
        private int currentAddress;
        private int remainingSize;

        IntPairSetIterator(int key) {
            this.key = key;
            if (key == -1) {
                remainingSize = 0;
            } else {
                busyKeys.put(key, this);
                currentAddress = key * (binSize + 2);
                remainingSize = getSize(key);
            }
        }

        @Override
        public boolean hasNext() {
            return remainingSize > 0;
        }

        @Override
        public Pair<Integer, T> next() {
            /*if (busyKeys.get(key) != this) {
                throw new ConcurrentModificationException();
            }*/
            Pair<Integer, T> result = list.get(currentAddress);
            currentAddress++;
            if (currentAddress % (binSize + 2) == binSize) {
                currentAddress = list.getA(currentAddress) * (binSize + 2);
            }
            remainingSize--;
            return result;
        }

        /**
         * This isn't really the intended use-case of Iterable, but it's useful to be able to use these in for loops and it would be pointless to make a separate object just to be the iterator, so this is both. It can still only be iterated over once.
         */
        @Override
        public Iterator<Pair<Integer, T>> iterator() {
            return this;
        }
    }
}
