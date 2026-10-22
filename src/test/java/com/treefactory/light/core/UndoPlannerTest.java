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
}
