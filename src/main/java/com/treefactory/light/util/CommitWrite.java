package com.treefactory.light.util;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** Tracks a commit snapshot until a write succeeds, with delayed retries after failures. */
final class CommitWrite {
    enum Status { WAITING, FAILED, SAVED }

    private CompletableFuture<Void> write;
    private long retryAfter;
    private Throwable error;

    CommitWrite(CompletableFuture<Void> write) { this.write=Objects.requireNonNull(write); }

    Status poll(long now, long retryDelay, Supplier<CompletableFuture<Void>> retry) {
        if (write==null) {
            if (now>=retryAfter) write=Objects.requireNonNull(retry.get());
            return Status.WAITING;
        }
        if (!write.isDone()) return Status.WAITING;
        try {
            write.join();
            return Status.SAVED;
        } catch (CompletionException | CancellationException e) {
            error=e;
            write=null;
            retryAfter=now+retryDelay;
            return Status.FAILED;
        }
    }

    Throwable error() { return error; }
}
