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

        assertFalse(footprint.restore("old", pos -> false, world::get, world::put));
        footprint.original.put("new", "grass");
        footprint.replacePlaced(Map.of("new", "log"));
        var committed = footprint.takeCommitted();
        assertEquals(Map.of("new", "log"), committed.placed());

        assertTrue(footprint.restore("old", pos -> true, world::get, world::put));
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
        assertFalse(footprint.restore("old", pos -> false, world::get, world::put));
        footprint.replacePlaced(Map.of());
        world.put("old", "stone");

        assertTrue(footprint.restore("old", pos -> true, world::get, world::put));
        assertEquals("stone", world.get("old"));
        assertTrue(footprint.pendingRestore.isEmpty());
        assertTrue(footprint.placed.isEmpty());
    }

    @Test void movingPreviewRetainsDeferredFootprintAndOriginal() {
        var footprint = new PreviewFootprint<String, String>();
        footprint.placed.put("old", "leaves");
        footprint.original.put("old", "air");
        footprint.pendingRestore.add("old");
        footprint.original.put("new", "grass");

        footprint.replacePlaced(Map.of("new", "log"));

        assertEquals(Map.of("old", "leaves", "new", "log"), footprint.placed);
        assertEquals(Map.of("old", "air", "new", "grass"), footprint.original);
        assertEquals(Set.of("old"), footprint.pendingRestore);
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

    @Test void reusingDeferredPositionCancelsItsPendingRestore() {
        var footprint = new PreviewFootprint<String, String>();
        footprint.placed.put("overlap", "leaves");
        footprint.original.put("overlap", "air");
        footprint.pendingRestore.add("overlap");

        footprint.replacePlaced(Map.of("overlap", "log"));
        var committed = footprint.takeCommitted();

        assertEquals(Map.of("overlap", "log"), committed.placed());
        assertEquals(Map.of("overlap", "air"), committed.original());
        assertTrue(footprint.placed.isEmpty());
        assertTrue(footprint.pendingRestore.isEmpty());
    }
}
