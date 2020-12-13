// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.updates;

import org.joml.Vector3i;
import org.terasology.fallingblocks.Chain;
import org.terasology.fallingblocks.Tree;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class UpdateThread extends Thread{
    private final BlockingQueue<Update> in;
    private final BlockingQueue<Set<Vector3i>> out;
    private final Object updatingFinishedMonitor;

    private Tree tree;
    private long previousUpdatedTime;
    private Set<Chain> updatedChains;

    public UpdateThread(BlockingQueue<Update> in, BlockingQueue<Set<Vector3i>> out, Object updatingFinishedMonitor) {
        this.in = in;
        this.out = out;
        this.updatingFinishedMonitor = updatingFinishedMonitor;
        tree = new Tree();
        updatedChains = new HashSet<>();
        setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run() {
        previousUpdatedTime = System.currentTimeMillis();
        try {
            while (!isInterrupted()) {
                Update update = in.poll(100,TimeUnit.MILLISECONDS);
                long startTime = System.currentTimeMillis();
                if (update != null) {
                    updatedChains.addAll(update.execute(tree));
                    long finishedTime = System.currentTimeMillis();
                    if (finishedTime > startTime - 10) {
                        sleep(finishedTime - startTime);
                    }
                }
                if (startTime > previousUpdatedTime + 90 && in.isEmpty()) {
                    previousUpdatedTime = startTime;
                    for (Chain chain : updatedChains) {
                        while (chain.parent != null) { // Just in case the root node has expanded since this chain was added to the set.
                            chain = chain.parent;
                        }
                        if (chain.isActive() && !chain.supported && !chain.isTouchingAnySide()) {
                            out.add(chain.getPositions(tree.rootNodePos));
                        }
                    }
                    updatedChains.clear();
                }
                synchronized (updatingFinishedMonitor) { // I can't find convenient monitors separate from locks, and Java requires that the lock be acquired before the monitor is usable.
                    updatingFinishedMonitor.notifyAll();
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }
}
