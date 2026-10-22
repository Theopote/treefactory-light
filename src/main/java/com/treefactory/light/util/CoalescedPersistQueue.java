package com.treefactory.light.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Merges rapid persist requests. Each enqueue supplies an already-captured snapshot;
 * the worker always writes the latest snapshot and only marks the generation it actually persisted.
 */
final class CoalescedPersistQueue {
    private record Entry(long generation, Object snapshot) {}

    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong writtenGeneration = new AtomicLong();
    private final AtomicBoolean draining = new AtomicBoolean();
    private volatile Entry latestEntry;
    private volatile CompletableFuture<Void> active = CompletableFuture.completedFuture(null);

    interface PersistWriter {
        void write(Object snapshot) throws Exception;
    }

    /** @param snapshot immutable payload captured on the caller thread */
    CompletableFuture<Void> enqueue(Object snapshot, PersistWriter writer, Executor executor) {
        long gen = generation.incrementAndGet();
        latestEntry = new Entry(gen, snapshot);
        if (draining.compareAndSet(false, true)) {
            CompletableFuture<Void> drainFuture = new CompletableFuture<>();
            active = drainFuture;
            executor.execute(() -> runDrain(drainFuture, writer, executor));
            return drainFuture;
        }
        return active;
    }

    long pendingGenerations() { return generation.get() - writtenGeneration.get(); }

    long writtenGeneration() { return writtenGeneration.get(); }

    private void runDrain(CompletableFuture<Void> future, PersistWriter writer, Executor executor) {
        try {
            while (writtenGeneration.get() < generation.get()) {
                Entry entry = latestEntry;
                if (entry == null) throw new IllegalStateException("Missing persist snapshot");
                long targetGen = entry.generation();
                writer.write(entry.snapshot());
                writtenGeneration.set(targetGen);
            }
            future.complete(null);
        } catch (Exception error) {
            future.completeExceptionally(error);
            return;
        } finally {
            draining.set(false);
        }
        if (writtenGeneration.get() < generation.get() && draining.compareAndSet(false, true)) {
            CompletableFuture<Void> next = new CompletableFuture<>();
            active = next;
            executor.execute(() -> runDrain(next, writer, executor));
        }
    }
}
