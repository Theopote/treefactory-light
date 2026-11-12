package com.treefactory.light.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.treefactory.light.core.tree.PreviewRevision;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JournaledPlacementTest {
    private static final class Harness {
        final PreviewFootprint<Integer,String> footprint=new PreviewFootprint<>();
        final Map<Integer,String> world=new HashMap<>();
        final Map<Integer,String> blocks=new LinkedHashMap<>();
        final List<CompletableFuture<Void>> journals=new ArrayList<>();
        final List<Map<Integer,String>> snapshots=new ArrayList<>();
        final List<Map<Integer,String>> originals=new ArrayList<>();
        final JournaledPlacement<Integer,String> placement;
        int writes;
        boolean accessible=true, failWrite;

        Harness(int size) {
            for (int i=0;i<size;i++) { world.put(i,"air"); blocks.put(i,"log"); }
            placement=new JournaledPlacement<>(footprint,blocks);
        }
        JournaledPlacement.Result tick(int budget) {
            return placement.tick(budget,2,pos -> accessible,world::get,"air"::equals,
                    (pos,state) -> {
                        writes++;
                        if (failWrite) return false;
                        world.put(pos,state);
                        return true;
                    }, () -> {
                        snapshots.add(Map.copyOf(footprint.placed));
                        originals.add(Map.copyOf(footprint.original));
                        var future=new CompletableFuture<Void>();
                        journals.add(future);
                        return future;
                    });
        }
        void rollback() {
            placement.forgetUnwritten();
            for (int pos : List.copyOf(footprint.placed.keySet()))
                footprint.restore(pos,p -> true,world::get,(p,state) -> { world.put(p,state); return true; });
        }
    }

    @Test void journalsBeforeWritesRespectsBudgetAndVerifiesBeforeCompletion() {
        var h=new Harness(3);
        assertEquals(2,h.tick(2).consumed());
        assertEquals(Map.of(0,"log",1,"log"),h.snapshots.getFirst());
        assertEquals(0,h.writes);
        assertEquals(JournaledPlacement.Status.WAITING,h.tick(2).status());
        h.journals.getFirst().complete(null);
        assertEquals(JournaledPlacement.Status.WAITING,h.tick(1).status());
        assertEquals(2,h.tick(2).consumed());
        assertEquals(2,h.writes);
        assertEquals(1,h.tick(2).consumed());
        h.journals.getLast().complete(null);
        assertEquals(1,h.tick(2).consumed());
        assertEquals(JournaledPlacement.Status.PROGRESS,h.tick(2).status());
        assertEquals(JournaledPlacement.Status.COMPLETE,h.tick(2).status());
    }

    @Test void cancellingBeforeAndAfterWritesKeepsOnlyActualWritesForRollback() {
        var h=new Harness(3);
        h.tick(2);
        h.journals.getFirst().complete(null);
        h.tick(2);
        h.tick(2);
        h.rollback();
        h.journals.getLast().complete(null);
        assertEquals(Map.of(0,"air",1,"air",2,"air"),h.world);
        assertTrue(h.footprint.placed.isEmpty());
    }

    @Test void storageFailureAndChangedWorldNeverStartBatchWrites() {
        var h=new Harness(2);
        h.tick(2);
        h.journals.getFirst().completeExceptionally(new IllegalStateException("disk full"));
        assertEquals(JournaledPlacement.Status.STORAGE_FAILED,h.tick(2).status());
        h.rollback();
        assertEquals(0,h.writes);

        var edited=new Harness(2);
        edited.tick(2);
        edited.world.put(0,"stone");
        edited.journals.getFirst().complete(null);
        assertEquals(JournaledPlacement.Status.FAILED,edited.tick(2).status());
        edited.rollback();
        assertEquals("stone",edited.world.get(0));
        assertEquals(0,edited.writes);
    }

    @Test void failedWriteAndPermissionChangeKeepPreviousWritesRecoverable() {
        var h=new Harness(3);
        h.tick(2);
        h.journals.getFirst().complete(null);
        h.tick(2);
        h.tick(2);
        h.journals.getLast().complete(null);
        h.failWrite=true;
        assertEquals(JournaledPlacement.Status.FAILED,h.tick(2).status());
        h.rollback();
        assertEquals(Map.of(0,"air",1,"air",2,"air"),h.world);

        var denied=new Harness(1);
        denied.tick(1);
        denied.journals.getFirst().complete(null);
        denied.accessible=false;
        assertEquals(JournaledPlacement.Status.FAILED,denied.tick(1).status());
        denied.rollback();
        assertEquals("air",denied.world.get(0));
    }

    @Test void verificationRejectsEditsMadeBetweenBatches() {
        var h=new Harness(3);
        h.tick(2); h.journals.getFirst().complete(null); h.tick(2);
        h.tick(2); h.journals.getLast().complete(null); h.tick(2);
        h.world.put(0,"stone");
        assertEquals(JournaledPlacement.Status.FAILED,h.tick(2).status());
        h.rollback();
        assertEquals("stone",h.world.get(0));
        assertEquals("air",h.world.get(1));
        assertEquals("air",h.world.get(2));
    }

    @Test void persistedFootprintRecoversACompletedBatchAfterCrash() {
        var h=new Harness(3);
        h.tick(2);
        h.journals.getFirst().complete(null);
        h.tick(2);
        var recovered=new PreviewFootprint<Integer,String>();
        recovered.placed.putAll(h.snapshots.getFirst());
        recovered.original.putAll(h.originals.getFirst());
        for (int pos : List.copyOf(recovered.placed.keySet()))
            recovered.restore(pos,p -> true,h.world::get,(p,state) -> { h.world.put(p,state); return true; });
        assertEquals(Map.of(0,"air",1,"air",2,"air"),h.world);
    }

    @Test void persistedFootprintRecoversOnlyWrittenCellsAfterInterruptedBatch() {
        var h=new Harness(2);
        h.tick(2);
        h.journals.getFirst().complete(null);
        var snapshot=h.snapshots.getFirst();
        var original=h.originals.getFirst();
        h.world.put(0,"log");
        var recovered=new PreviewFootprint<Integer,String>();
        recovered.placed.putAll(snapshot);
        recovered.original.putAll(original);
        assertFalse(recovered.restore(0,pos -> false,h.world::get,(pos,state) -> true));
        assertEquals("log",h.world.get(0));
        assertTrue(recovered.restore(0,pos -> true,h.world::get,(pos,state) -> { h.world.put(pos,state); return true; }));
        assertTrue(recovered.restore(1,pos -> true,h.world::get,(pos,state) -> { fail("unwritten cell"); return false; }));
        assertEquals(Map.of(0,"air",1,"air"),h.world);
    }

    @Test void replacementAfterPartialPlacementStartsFromRestoredWorld() {
        var h=new Harness(3);
        h.tick(2);
        h.journals.getFirst().complete(null);
        h.tick(2);
        h.tick(2);
        h.rollback();
        var replacement=new JournaledPlacement<>(h.footprint,Map.of(0,"leaves"));
        var result=replacement.tick(2,2,pos -> true,h.world::get,"air"::equals,
                (pos,state) -> { h.world.put(pos,state); return true; },
                () -> CompletableFuture.completedFuture(null));
        assertEquals(JournaledPlacement.Status.PROGRESS,result.status());
        replacement.tick(2,2,pos -> true,h.world::get,"air"::equals,
                (pos,state) -> { h.world.put(pos,state); return true; },
                () -> CompletableFuture.completedFuture(null));
        assertEquals("leaves",h.world.get(0));
        assertEquals("air",h.world.get(1));
    }

    @Test void commitRevisionIsNotReadyUntilAllWritesAndVerificationFinish() {
        var h=new Harness(3);
        var revisions=new PreviewRevision();
        var player=UUID.randomUUID();
        long revision=revisions.next(player);
        revisions.requestCommit(player,revision);
        h.tick(2);
        assertFalse(revisions.shouldCommit(player));
        h.journals.getFirst().complete(null); h.tick(2);
        h.tick(2); h.journals.getLast().complete(null); h.tick(2);
        assertFalse(revisions.shouldCommit(player));
        assertEquals(JournaledPlacement.Status.PROGRESS,h.tick(2).status());
        assertFalse(revisions.shouldCommit(player));
        assertEquals(JournaledPlacement.Status.COMPLETE,h.tick(2).status());
        revisions.markApplied(player,revision);
        assertTrue(revisions.shouldCommit(player));
    }

    @Test void largeBatchPreparationAndWritesRespectRequestedLimit() {
        var h=new Harness(513);
        var prepare=h.placement.tick(512,128,pos -> true,h.world::get,"air"::equals,
                (pos,state) -> { h.writes++; h.world.put(pos,state); return true; },
                () -> {
                    var future=new CompletableFuture<Void>();
                    h.journals.add(future);
                    return future;
                });
        assertEquals(128,prepare.consumed());
        assertEquals(128,h.footprint.placed.size());
        h.journals.getFirst().complete(null);
        assertEquals(JournaledPlacement.Status.WAITING,h.tick(127).status());
        assertEquals(0,h.writes);
        assertEquals(128,h.tick(128).consumed());
        assertEquals(128,h.writes);
    }

    @Test void placementChecksWorldInsteadOfTrustingWriteReturnValue() {
        var h=new Harness(1);
        h.tick(1);
        h.journals.getFirst().complete(null);
        var failed=h.placement.tick(1,1,pos -> true,h.world::get,"air"::equals,
                (pos,state) -> true,() -> CompletableFuture.completedFuture(null));
        assertEquals(JournaledPlacement.Status.FAILED,failed.status());
        h.rollback();
        assertTrue(h.footprint.placed.isEmpty());

        var successful=new Harness(1);
        successful.tick(1);
        successful.journals.getFirst().complete(null);
        var written=successful.placement.tick(1,1,pos -> true,successful.world::get,"air"::equals,
                (pos,state) -> { successful.world.put(pos,state); return false; },
                () -> CompletableFuture.completedFuture(null));
        assertEquals(JournaledPlacement.Status.PROGRESS,written.status());
        assertEquals("log",successful.world.get(0));
    }
}
