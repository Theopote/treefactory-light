package com.treefactory.light.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Merges rapid persist requests: the worker always writes the latest snapshot and loops until
 * no newer generation arrived during the last write.
 */
final class CoalescedPersistQueue {
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicBoolean draining = new AtomicBoolean();
    private volatile Supplier<?> latestSnapshot;
    private volatile PersistWriter writer;
    private volatile CompletableFuture<Void> active = CompletableFuture.completedFuture(null);

    interface PersistWriter {
        void write(Object snapshot) throws Exception;
    }

    CompletableFuture<Void> enqueue(Supplier<?> snapshot, PersistWriter writer, Executor executor) {
        latestSnapshot = snapshot;
        this.writer = writer;
        generation.incrementAndGet();
        if (draining.compareAndSet(false, true)) {
            CompletableFuture<Void> drainFuture = new CompletableFuture<>();
            active = drainFuture;
            executor.execute(() -> runDrain(drainFuture, executor));
            return drainFuture;
        }
        return active;
    }

    long pendingGenerations() { return generation.get() - written.get(); }

    private void runDrain(CompletableFuture<Void> future, Executor executor) {
        try {
            do {
                Object snapshot = latestSnapshot.get();
                if (snapshot != null) writer.write(snapshot);
                written.set(generation.get());
            } while (written.get() < generation.get());
            future.complete(null);
        } catch (Exception error) {
            future.completeExceptionally(error);
        } finally {
            draining.set(false);
            if (written.get() < generation.get() && draining.compareAndSet(false, true)) {
                CompletableFuture<Void> next = new CompletableFuture<>();
                active = next;
                executor.execute(() -> runDrain(next, executor));
            }
        }
    }
}
