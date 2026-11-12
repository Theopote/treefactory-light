package com.treefactory.light.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Serializes snapshots and completes each drain only after every snapshot enqueued in that drain is written. */
final class CoalescedPersistQueue {
    private record Entry(long generation, Object snapshot) {}

    private long generation, writtenGeneration;
    private Entry latestEntry;
    private CompletableFuture<Void> active;

    interface PersistWriter {
        void write(Object snapshot) throws Exception;
    }

    synchronized CompletableFuture<Void> enqueue(Object snapshot, PersistWriter writer, Executor executor) {
        latestEntry = new Entry(++generation, snapshot);
        if (active == null) {
            active = new CompletableFuture<>();
            CompletableFuture<Void> future = active;
            try {
                executor.execute(() -> runDrain(future, writer));
            } catch (RejectedExecutionException error) {
                active = null;
                future.completeExceptionally(error);
            }
            return future;
        }
        return active;
    }

    synchronized long pendingGenerations() { return generation - writtenGeneration; }
    synchronized long writtenGeneration() { return writtenGeneration; }

    private void runDrain(CompletableFuture<Void> future, PersistWriter writer) {
        try {
            while (true) {
                Entry entry;
                synchronized (this) {
                    if (writtenGeneration == generation) {
                        active = null;
                        future.complete(null);
                        return;
                    }
                    entry = latestEntry;
                }
                writer.write(entry.snapshot());
                synchronized (this) {
                    writtenGeneration = entry.generation();
                }
            }
        } catch (Exception error) {
            synchronized (this) {
                active = null;
                future.completeExceptionally(error);
            }
        }
    }
}
