package com.treefactory.light.core.tree;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Plans an undo without mutating history. Aborts when unloaded chunks would block restoration. */
public final class UndoPlanner {
    private UndoPlanner() {}

    /**
     * @param placed blocks that were placed by the build
     * @param original blocks before the build
     * @param current returns the live block state at a position, or {@code null} if the chunk is unavailable
     * @param chunkReady whether the chunk at a position is loaded
     * @return restorations to apply, or {@code null} when an unloaded chunk blocks the undo
     */
    public static <P, S> Map<P, S> planRestorations(Map<P, S> placed, Map<P, S> original,
            Function<P, S> current, Predicate<P> chunkReady) {
        for (P pos : placed.keySet()) {
            if (chunkReady.test(pos)) continue;
            if (original.containsKey(pos)) return null;
        }
        Map<P, S> result = new LinkedHashMap<>();
        for (var cell : placed.entrySet()) {
            P pos = cell.getKey();
            if (!chunkReady.test(pos)) continue;
            S live = current.apply(pos);
            if (!Objects.equals(live, cell.getValue())) continue;
            S before = original.get(pos);
            if (before != null) result.put(pos, before);
        }
        return result;
    }
}
