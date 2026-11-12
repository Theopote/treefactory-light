package com.treefactory.light.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewFootprintTest {
    @Test void unloadedOldPreviewIsRestoredAfterMovingAndCommitting() {
        var footprint = new PreviewFootprint<String, String>();
        var world = new HashMap<>(Map.of("old", "leaves", "new", "log"));
        footprint.placed.put("old", "leaves");
        footprint.original.put("old", "air");

        assertFalse(footprint.restore("old", pos -> false, world::get, (pos, state) -> { world.put(pos, state); return true; }));
        footprint.original.put("new", "grass");
        footprint.placed.put("new", "log");
        var committed = footprint.takeCommitted();
        assertEquals(Map.of("new", "log"), committed.placed());

        assertTrue(footprint.restore("old", pos -> true, world::get, (pos, state) -> { world.put(pos, state); return true; }));
        assertEquals(Map.of("old", "air", "new", "log"), world);
        assertTrue(footprint.placed.isEmpty());
        assertTrue(footprint.original.isEmpty());
        assertTrue(footprint.pendingRestore.isEmpty());
    }

    @Test void deferredRestorationDoesNotOverwritePlayerEdits() {
        var footprint = new PreviewFootprint<String, String>();
        var world = new HashMap<>(Map.of("old", "leaves"));
        footprint.placed.put("old", "leaves");
        footprint.original.put("old", "air");
        assertFalse(footprint.restore("old", pos -> false, world::get, (pos, state) -> { world.put(pos, state); return true; }));
        world.put("old", "stone");

        assertTrue(footprint.restore("old", pos -> true, world::get, (pos, state) -> { world.put(pos, state); return true; }));
        assertEquals("stone", world.get("old"));
        assertTrue(footprint.pendingRestore.isEmpty());
        assertTrue(footprint.placed.isEmpty());
    }

    @Test void committingKeepsDeferredCellsOutOfUndoHistory() {
        var footprint = new PreviewFootprint<String, String>();
        footprint.placed.putAll(Map.of("old", "leaves", "new", "log"));
        footprint.original.putAll(Map.of("old", "air", "new", "grass"));
        footprint.pendingRestore.add("old");

        var committed = footprint.takeCommitted();

        assertEquals(Map.of("new", "log"), committed.placed());
        assertEquals(Map.of("new", "grass"), committed.original());
        assertEquals(Map.of("old", "leaves"), footprint.placed);
        assertEquals(Map.of("old", "air"), footprint.original);
        assertEquals(Set.of("old"), footprint.pendingRestore);
    }

    @Test void failedWriteRetainsFootprintUntilRetrySucceeds() {
        var footprint = new PreviewFootprint<String, String>();
        var world = new HashMap<>(Map.of("pos", "log"));
        footprint.placed.put("pos", "log");
        footprint.original.put("pos", "air");

        assertFalse(footprint.restore("pos", pos -> true, world::get, (pos, state) -> false));
        assertEquals(Map.of("pos", "log"), footprint.placed);
        assertEquals(Map.of("pos", "air"), footprint.original);
        assertEquals(Set.of("pos"), footprint.pendingRestore);

        assertTrue(footprint.restore("pos", pos -> true, world::get, (pos, state) -> {
            world.put(pos, state);
            return true;
        }));
        assertEquals("air", world.get("pos"));
        assertTrue(footprint.placed.isEmpty());
        assertTrue(footprint.original.isEmpty());
        assertTrue(footprint.pendingRestore.isEmpty());
    }

    @Test void failedWriteFollowedByPlayerEditDoesNotOverwriteEdit() {
        var footprint = new PreviewFootprint<String, String>();
        var world = new HashMap<>(Map.of("pos", "log"));
        footprint.placed.put("pos", "log");
        footprint.original.put("pos", "air");
        assertFalse(footprint.restore("pos", pos -> true, world::get, (pos, state) -> false));
        world.put("pos", "stone");

        assertTrue(footprint.restore("pos", pos -> true, world::get,
                (pos, state) -> { fail("must not overwrite player edits"); return false; }));
        assertEquals("stone", world.get("pos"));
        assertTrue(footprint.placed.isEmpty());
    }

    @Test void unchangedOriginalDoesNotRequireWrite() {
        var footprint = new PreviewFootprint<String, String>();
        footprint.placed.put("pos", "leaves");
        footprint.original.put("pos", "leaves");

        assertTrue(footprint.restore("pos", pos -> true, pos -> "leaves",
                (pos, state) -> { fail("unchanged blocks should not be written"); return false; }));
        assertTrue(footprint.placed.isEmpty());
    }

    @Test void retriesRespectBudgetAndSelectOnlyMatchingPositions() {
        var footprint = new PreviewFootprint<String, String>();
        footprint.pendingRestore.addAll(java.util.List.of("a1", "b1", "a2", "a3"));
        var visited = new java.util.ArrayList<String>();
        var result = footprint.retryPending(2, pos -> pos.startsWith("a"), pos -> {
            visited.add(pos);
            footprint.pendingRestore.remove(pos);
            return true;
        });
        assertEquals(2, result.attempted());
        assertEquals(2, result.restored());
        assertEquals(java.util.List.of("a1", "a2"), visited);
        assertEquals(Set.of("b1", "a3"), footprint.pendingRestore);
        assertEquals(0, footprint.retryPending(0, pos -> true, pos -> {
            fail("zero budget must not restore anything");
            return true;
        }).attempted());
    }

    @Test void failedRetryMovesBehindOtherPendingPositions() {
        var footprint = new PreviewFootprint<String, String>();
        footprint.pendingRestore.addAll(java.util.List.of("a", "b"));
        var first = footprint.retryPending(1, pos -> true, pos -> false);
        assertEquals(1, first.attempted());
        assertEquals(0, first.restored());
        var visited = new java.util.ArrayList<String>();
        footprint.retryPending(1, pos -> true, pos -> {
            visited.add(pos);
            footprint.pendingRestore.remove(pos);
            return true;
        });
        assertEquals(java.util.List.of("b"), visited);
        assertEquals(Set.of("a"), footprint.pendingRestore);
    }
}
