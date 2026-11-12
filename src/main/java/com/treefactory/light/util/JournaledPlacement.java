package com.treefactory.light.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Server-thread placement with a saved footprint before each batch of writes. */
final class JournaledPlacement<P, S> {
    enum Status { PROGRESS, WAITING, COMPLETE, FAILED, STORAGE_FAILED }
    record Result(Status status, int consumed, Throwable error) {}

    private final PreviewFootprint<P, S> footprint;
    private final PreviewFootprint<P, S> previous;
    private final Iterator<Map.Entry<P, S>> remaining;
    private final Map<P, S> before = new LinkedHashMap<>();
    private CompletableFuture<Void> journal;
    private Iterator<Map.Entry<P, S>> verify;

    JournaledPlacement(PreviewFootprint<P, S> footprint, Map<P, S> blocks) {
        this(footprint, null, blocks);
    }

    JournaledPlacement(PreviewFootprint<P, S> footprint, PreviewFootprint<P, S> previous, Map<P, S> blocks) {
        this.footprint = footprint;
        this.previous = previous;
        this.remaining = blocks.entrySet().iterator();
    }

    Result tick(int budget, int sessionBudget, Predicate<P> accessible, Function<P, S> current,
            Predicate<S> replaceable, BiPredicate<P, S> write, Supplier<CompletableFuture<Void>> persist) {
        if (budget <= 0 || sessionBudget <= 0) return result(Status.WAITING, 0);
        if (verify != null) {
            int count = 0;
            while (count < Math.min(budget, sessionBudget) && verify.hasNext()) {
                var entry = verify.next();
                count++;
                if (!accessible.test(entry.getKey()) || !Objects.equals(current.apply(entry.getKey()), entry.getValue()))
                    return result(Status.FAILED, count);
            }
            return result(verify.hasNext() ? Status.PROGRESS : Status.COMPLETE, count);
        }
        if (journal != null) {
            if (!journal.isDone() || before.size() > budget) return result(Status.WAITING, 0);
            try {
                journal.join();
            } catch (CompletionException | CancellationException error) {
                return new Result(Status.STORAGE_FAILED, 0, error);
            }
            int count = 0;
            var prepared = before.entrySet().iterator();
            while (prepared.hasNext()) {
                var entry = prepared.next();
                P pos = entry.getKey();
                count++;
                if (!accessible.test(pos) || !Objects.equals(current.apply(pos), entry.getValue()))
                    return result(Status.FAILED, count);
                S target = footprint.placed.get(pos);
                if (!Objects.equals(entry.getValue(), target)) {
                    write.test(pos, target);
                    if (!Objects.equals(current.apply(pos), target)) return result(Status.FAILED, count);
                }
                if (previous != null) {
                    previous.placed.remove(pos);
                    previous.original.remove(pos);
                    previous.pendingRestore.remove(pos);
                }
                prepared.remove();
            }
            journal = null;
            if (!remaining.hasNext()) verify = footprint.placed.entrySet().iterator();
            return result(Status.PROGRESS, count);
        }
        int count = 0;
        while (count < Math.min(budget, sessionBudget) && remaining.hasNext()) {
            var entry = remaining.next();
            P pos = entry.getKey();
            count++;
            if (!accessible.test(pos)) return result(Status.FAILED, count);
            S state = current.apply(pos);
            S old = previous == null ? null : previous.placed.get(pos);
            if (old != null ? !Objects.equals(old, state) : !replaceable.test(state))
                return result(Status.FAILED, count);
            S original = old == null ? state : previous.original.get(pos);
            if (original == null) return result(Status.FAILED, count);
            if (old != null && Objects.equals(old, entry.getValue())) {
                footprint.original.put(pos, original);
                footprint.placed.put(pos, old);
                previous.placed.remove(pos);
                previous.original.remove(pos);
                previous.pendingRestore.remove(pos);
                continue;
            }
            before.put(pos, state);
            footprint.original.put(pos, original);
            footprint.placed.put(pos, entry.getValue());
        }
        if (before.isEmpty()) {
            if (!remaining.hasNext()) verify = footprint.placed.entrySet().iterator();
            return result(Status.PROGRESS, count);
        }
        journal = persist.get();
        return result(journal == null ? Status.STORAGE_FAILED : Status.PROGRESS, count);
    }

    void forgetUnwritten() {
        for (P pos : before.keySet()) {
            footprint.placed.remove(pos);
            footprint.original.remove(pos);
        }
        before.clear();
    }

    private static Result result(Status status, int consumed) {
        return new Result(status, consumed, null);
    }
}
