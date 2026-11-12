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
     * @return changed cells to restore, or {@code null} when an unloaded chunk blocks the undo
     */
    public static <P, S> Map<P, S> planRestorations(Map<P, S> placed, Map<P, S> original,
            Function<P, S> current, Predicate<P> chunkReady) {
        for (var cell : placed.entrySet()) {
            P pos = cell.getKey();
            S before = original.get(pos);
            if (before == null || Objects.equals(before, cell.getValue())) continue;
            if (chunkReady.test(pos)) continue;
            return null;
        }
        Map<P, S> result = new LinkedHashMap<>();
        for (var cell : placed.entrySet()) {
            P pos = cell.getKey();
            S before = original.get(pos);
            if (before == null || Objects.equals(before, cell.getValue())) continue;
            if (!chunkReady.test(pos)) continue;
            S live = current.apply(pos);
            if (!Objects.equals(live, cell.getValue())) continue;
            result.put(pos, before);
        }
        return result;
    }
}
