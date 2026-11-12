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

    boolean place(P pos, S next, Predicate<S> replaceable, Function<P, S> current, BiPredicate<P, S> set) {
        S before = current.apply(pos);
        S previous = placed.get(pos);
        if (previous != null && !Objects.equals(previous, before)) {
            placed.remove(pos);
            original.remove(pos);
            pendingRestore.remove(pos);
            previous = null;
        }
        if (previous == null && !replaceable.test(before)) return false;
        boolean newlyTracked = previous == null;
        if (newlyTracked) original.put(pos, before);
        if (!Objects.equals(before, next)) {
            set.test(pos, next);
            if (!Objects.equals(current.apply(pos), next)) {
                if (newlyTracked) original.remove(pos);
                return false;
            }
        }
        placed.put(pos, next);
        pendingRestore.remove(pos);
        return true;
    }

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

    void replacePlaced(Map<P, S> next) {
        placed.keySet().removeIf(pos -> !pendingRestore.contains(pos));
        placed.putAll(next);
        pendingRestore.removeAll(next.keySet());
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
