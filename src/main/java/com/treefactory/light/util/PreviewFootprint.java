package com.treefactory.light.util;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

class PreviewFootprint<P, S> {
    final Map<P, S> original = new HashMap<>();
    final Map<P, S> placed = new HashMap<>();
    final Set<P> pendingRestore = new LinkedHashSet<>();

    record Committed<P, S>(Map<P, S> placed, Map<P, S> original) {}
    record RetryResult(int attempted, int restored) {}

    RetryResult retryPending(int limit, Predicate<P> selected, Predicate<P> restore) {
        if (limit <= 0) return new RetryResult(0, 0);
        List<P> batch = new ArrayList<>();
        for (P pos : pendingRestore) {
            if (selected.test(pos)) {
                batch.add(pos);
                if (batch.size() == limit) break;
            }
        }
        int restored = 0;
        for (P pos : batch) {
            if (restore.test(pos)) restored++;
            else if (pendingRestore.remove(pos)) pendingRestore.add(pos);
        }
        return new RetryResult(batch.size(), restored);
    }

    boolean restore(P pos, Predicate<P> ready, Function<P, S> current, BiPredicate<P, S> set) {
        if (!ready.test(pos)) {
            pendingRestore.add(pos);
            return false;
        }
        S expected = placed.get(pos), before = original.get(pos);
        if (expected != null && before != null && !Objects.equals(expected, before)
                && Objects.equals(current.apply(pos), expected) && !set.test(pos, before)) {
            pendingRestore.add(pos);
            return false;
        }
        placed.remove(pos);
        original.remove(pos);
        pendingRestore.remove(pos);
        return true;
    }

    Committed<P, S> takeCommitted() {
        Map<P, S> committedPlaced = new HashMap<>();
        Map<P, S> committedOriginal = new HashMap<>();
        var entries = placed.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            P pos = entry.getKey();
            if (pendingRestore.contains(pos)) continue;
            committedPlaced.put(pos, entry.getValue());
            S before = original.remove(pos);
            if (before != null) committedOriginal.put(pos, before);
            entries.remove();
        }
        return new Committed<>(Map.copyOf(committedPlaced), Map.copyOf(committedOriginal));
    }
}
