package com.treefactory.light.core;

import com.treefactory.light.core.tree.UndoPlanner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoPlannerTest {
    @Test void abortsWhenUnloadedChunkBlocksRestoration() {
        Map<String, Integer> placed = Map.of("a", 1, "b", 2);
        Map<String, Integer> original = Map.of("a", -1, "b", -2);
        assertNull(UndoPlanner.planRestorations(placed, original, key -> key.equals("a") ? 1 : null, key -> !key.equals("b")));
    }

    @Test void skipsPlayerEditedCells() {
        Map<String, Integer> placed = Map.of("a", 1, "b", 2);
        Map<String, Integer> original = Map.of("a", -1, "b", -2);
        var plan = UndoPlanner.planRestorations(placed, original, key -> key.equals("a") ? 99 : 2, key -> true);
        assertEquals(Map.of("b", -2), plan);
    }

    @Test void restoresAllMatchingCellsWhenChunksReady() {
        Map<String, Integer> placed = Map.of("a", 1, "b", 2);
        Map<String, Integer> original = Map.of("a", -1, "b", -2);
        var plan = UndoPlanner.planRestorations(placed, original, placed::get, key -> true);
        assertEquals(original, plan);
    }

    @Test void excludesUnchangedCellsWhileRestoringOtherCells() {
        Map<String, Integer> placed = Map.of("overlap", 1, "changed", 2);
        Map<String, Integer> original = Map.of("overlap", 1, "changed", -2);
        assertEquals(Map.of("changed", -2),
                UndoPlanner.planRestorations(placed, original, placed::get, key -> true));
    }

    @Test void unchangedCellsDoNotRequireLoadedChunksOrWorldReads() {
        Map<String, Integer> placed = Map.of("overlap", 1);
        var plan = UndoPlanner.planRestorations(placed, placed,
                key -> { fail("unchanged cells should not be read"); return null; },
                key -> false);
        assertEquals(Map.of(), plan);
    }

    @Test void retrySkipsAlreadyRestoredAndUnchangedCells() {
        Map<String, Integer> placed = Map.of("overlap", 1, "restored", 2, "failed", 3);
        Map<String, Integer> original = Map.of("overlap", 1, "restored", -2, "failed", -3);
        Map<String, Integer> current = Map.of("overlap", 1, "restored", -2, "failed", 3);
        assertEquals(Map.of("failed", -3),
                UndoPlanner.planRestorations(placed, original, current::get, key -> true));
    }
}
