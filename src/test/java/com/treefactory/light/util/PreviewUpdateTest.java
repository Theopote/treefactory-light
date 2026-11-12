package com.treefactory.light.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewUpdateTest {
    @Test void unchangedPreviewNeedsNoJournalOrWorldWrites() {
        var old=new PreviewFootprint<String,String>();
        old.placed.putAll(Map.of("a","log","b","leaves"));
        old.original.putAll(Map.of("a","air","b","grass"));
        var current=new PreviewFootprint<String,String>();
        var world=Map.of("a","log","b","leaves");
        var batch=new JournaledPlacement<>(current,old,world);
        var result=batch.tick(2,2,pos -> true,world::get,s -> false,
                (pos,state) -> { fail("unchanged blocks must not be written"); return false; },
                () -> { fail("unchanged blocks must not require a journal"); return null; });
        assertEquals(2,result.consumed());
        assertEquals(world,current.placed);
        assertEquals(Map.of("a","air","b","grass"),current.original);
        assertTrue(old.placed.isEmpty());
        assertEquals(JournaledPlacement.Status.COMPLETE,batch.tick(2,2,pos -> true,world::get,s -> false,
                (pos,state) -> false,() -> null).status());
    }

    @Test void unchangedLargeUpdateAdvancesFourThousandBlocksWithoutDiskWait() {
        var old=new PreviewFootprint<Integer,String>();
        var next=new LinkedHashMap<Integer,String>();
        for (int i=0;i<5000;i++) {
            old.placed.put(i,"leaves");
            old.original.put(i,"air");
            next.put(i,"leaves");
        }
        var current=new PreviewFootprint<Integer,String>();
        var batch=new JournaledPlacement<>(current,old,next);
        var result=batch.tick(WorldBatchPaster.PLACE_BUDGET,WorldBatchPaster.PLACE_SESSION_BUDGET,
                pos -> true,pos -> "leaves",s -> false,
                (pos,state) -> { fail("unchanged blocks must not be written"); return false; },
                () -> { fail("unchanged blocks must not require a journal"); return null; });
        assertEquals(4096,result.consumed());
        assertEquals(4096,current.placed.size());
        assertEquals(904,old.placed.size());
    }

    @Test void overlapStaysVisibleAndChangedBlocksAreReplacedDirectly() {
        var old=new PreviewFootprint<String,String>();
        old.placed.putAll(Map.of("same","leaves","changed","old_log","removed","leaves"));
        old.original.putAll(Map.of("same","air","changed","grass","removed","air"));
        var world=new HashMap<>(Map.of("same","leaves","changed","old_log","removed","leaves","added","air"));
        var next=new LinkedHashMap<>(Map.of("same","leaves","changed","new_log","added","leaves"));
        var updated=new PreviewFootprint<String,String>();
        var batch=new JournaledPlacement<>(updated,old,next);
        var writes=new ArrayList<String>();
        var ready=new CompletableFuture<Void>();
        var prepare=batch.tick(100,100,pos -> true,world::get,"air"::equals,
                (pos,state) -> { writes.add(pos+":"+state); world.put(pos,state); return true; },() -> ready);
        assertEquals(JournaledPlacement.Status.PROGRESS,prepare.status());
        assertEquals("leaves",world.get("same"));
        assertEquals("old_log",world.get("changed"));
        assertTrue(writes.isEmpty());
        ready.complete(null);
        batch.tick(100,100,pos -> true,world::get,"air"::equals,
                (pos,state) -> { writes.add(pos+":"+state); world.put(pos,state); return true; },() -> ready);
        assertFalse(writes.stream().anyMatch(s -> s.startsWith("same:")));
        assertTrue(writes.contains("changed:new_log"));
        assertFalse(writes.contains("changed:grass"));
        assertEquals(Map.of("same","air","changed","grass","added","air"),updated.original);
        assertEquals(Map.of("removed","leaves"),old.placed);
        assertTrue(old.restore("removed",pos -> true,world::get,(pos,state) -> { world.put(pos,state); return true; }));
        assertEquals("air",world.get("removed"));
    }

    @Test void recoveryHandlesEitherSideOfAnOverlappingWrite() {
        for (String savedWorld : List.of("old","new")) {
            var old=new PreviewFootprint<String,String>();
            old.placed.put("pos","old"); old.original.put("pos","air");
            var current=new PreviewFootprint<String,String>();
            var world=new HashMap<>(Map.of("pos","old"));
            var batch=new JournaledPlacement<>(current,old,Map.of("pos","new"));
            batch.tick(1,1,pos -> true,world::get,s -> false,(pos,state) -> true,
                    () -> CompletableFuture.completedFuture(null));
            var savedOld=Map.copyOf(old.placed);
            var savedCurrent=Map.copyOf(current.placed);
            var savedOriginal=Map.copyOf(current.original);
            world.put("pos",savedWorld);
            var recoveredCurrent=new PreviewFootprint<String,String>();
            recoveredCurrent.placed.putAll(savedCurrent); recoveredCurrent.original.putAll(savedOriginal);
            var recoveredOld=new PreviewFootprint<String,String>();
            recoveredOld.placed.putAll(savedOld); recoveredOld.original.put("pos","air");
            for (var footprint : List.of(recoveredCurrent,recoveredOld))
                footprint.restore("pos",pos -> true,world::get,(pos,state) -> { world.put(pos,state); return true; });
            assertEquals("air",world.get("pos"),savedWorld);
        }
    }

    @Test void playerEditIsNotOverwrittenDuringUpdate() {
        var old=new PreviewFootprint<String,String>();
        old.placed.put("pos","old"); old.original.put("pos","air");
        var current=new PreviewFootprint<String,String>();
        var world=Map.of("pos","stone");
        var batch=new JournaledPlacement<>(current,old,Map.of("pos","new"));
        var result=batch.tick(1,1,pos -> true,world::get,s -> false,
                (pos,state) -> { fail("must not overwrite"); return false; },
                () -> CompletableFuture.completedFuture(null));
        assertEquals(JournaledPlacement.Status.FAILED,result.status());
        assertEquals(Map.of("pos","old"),old.placed);
    }

    @Test void failedJournalAfterUnchangedTransferStillRestoresBothFootprints() {
        var old=new PreviewFootprint<String,String>();
        old.placed.putAll(Map.of("same","leaves","change","old"));
        old.original.putAll(Map.of("same","air","change","grass"));
        var current=new PreviewFootprint<String,String>();
        var world=new HashMap<>(Map.of("same","leaves","change","old"));
        var targets=new LinkedHashMap<String,String>();
        targets.put("same","leaves"); targets.put("change","new");
        var journal=new CompletableFuture<Void>();
        var batch=new JournaledPlacement<>(current,old,targets);
        batch.tick(2,2,pos -> true,world::get,s -> false,(pos,state) -> true,() -> journal);
        journal.completeExceptionally(new IllegalStateException("disk full"));
        assertEquals(JournaledPlacement.Status.STORAGE_FAILED,batch.tick(2,2,pos -> true,world::get,s -> false,
                (pos,state) -> { fail("must not write"); return false; },() -> journal).status());
        batch.forgetUnwritten();
        for (var footprint : List.of(old,current))
            for (String pos : List.copyOf(footprint.placed.keySet()))
                footprint.restore(pos,p -> true,world::get,(p,state) -> { world.put(p,state); return true; });
        assertEquals(Map.of("same","air","change","grass"),world);
    }
}
