package com.treefactory.light.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Processes at most the allotted number of history entries, leaving history intact on failure. */
final class BatchedUndo<P, S> {
    enum Status { PROGRESS, COMPLETE, UNLOADED, PROTECTED, WRITE_FAILED }
    record Result(Status status, int consumed, int restored) {}

    private final Iterator<Map.Entry<P, S>> entries;
    private final Map<P, S> original;
    private int restored;

    BatchedUndo(Map<P, S> placed, Map<P, S> original) {
        this.entries = placed.entrySet().iterator();
        this.original = original;
    }

    Result tick(int budget, Predicate<P> loaded, Predicate<P> permitted,
                Function<P, S> current, BiPredicate<P, S> set) {
        int consumed = 0;
        while (consumed < budget && entries.hasNext()) {
            var entry = entries.next();
            consumed++;
            P pos = entry.getKey();
            S before = original.get(pos), expected = entry.getValue();
            if (before == null || Objects.equals(before, expected)) continue;
            if (!loaded.test(pos)) return new Result(Status.UNLOADED, consumed, restored);
            if (!Objects.equals(current.apply(pos), expected)) continue;
            if (!permitted.test(pos)) return new Result(Status.PROTECTED, consumed, restored);
            if (!set.test(pos, before)) return new Result(Status.WRITE_FAILED, consumed, restored);
            restored++;
        }
        return new Result(entries.hasNext() ? Status.PROGRESS : Status.COMPLETE, consumed, restored);
    }
}
