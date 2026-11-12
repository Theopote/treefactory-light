package com.treefactory.light.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewThroughputTest {
    @Test void fiveThousandBlocksAppearInTwoWriteBatches() {
        int size=5000;
        var blocks=new LinkedHashMap<Integer,String>();
        var world=new HashMap<Integer,String>();
        for (int i=0;i<size;i++) {
            blocks.put(i,"log");
            world.put(i,"air");
        }
        var footprint=new PreviewFootprint<Integer,String>();
        var placement=new JournaledPlacement<>(footprint,blocks);
        int[] writes={0};
        int writeTicks=0, ticks=0;
        JournaledPlacement.Status status;
        do {
            int before=writes[0];
            var result=placement.tick(WorldBatchPaster.PLACE_BUDGET,WorldBatchPaster.PLACE_SESSION_BUDGET,
                    pos -> true,world::get,"air"::equals,
                    (pos,state) -> { world.put(pos,state); writes[0]++; return true; },
                    () -> CompletableFuture.completedFuture(null));
            assertTrue(result.consumed()<=WorldBatchPaster.PLACE_SESSION_BUDGET);
            assertTrue(writes[0]-before<=WorldBatchPaster.PLACE_SESSION_BUDGET);
            if (writes[0]>before) writeTicks++;
            status=result.status();
            assertNotEquals(JournaledPlacement.Status.FAILED,status);
            assertNotEquals(JournaledPlacement.Status.STORAGE_FAILED,status);
            ticks++;
            assertTrue(ticks<=6,"placement must complete within six ticks when journaling is immediate");
        } while (status!=JournaledPlacement.Status.COMPLETE);
        assertEquals(5000,writes[0]);
        assertEquals(2,writeTicks);
        assertEquals(6,ticks);
    }

    @Test void fiveThousandPendingBlocksRestoreInTwoBatches() {
        var footprint=new PreviewFootprint<Integer,String>();
        Map<Integer,String> world=new HashMap<>();
        for (int i=0;i<5000;i++) {
            footprint.placed.put(i,"log");
            footprint.original.put(i,"air");
            footprint.pendingRestore.add(i);
            world.put(i,"log");
        }
        var first=footprint.retryPending(WorldBatchPaster.RESTORE_SESSION_BUDGET,pos -> true,
                pos -> footprint.restore(pos,p -> true,world::get,
                        (p,state) -> { world.put(p,state); return true; }));
        var second=footprint.retryPending(WorldBatchPaster.RESTORE_SESSION_BUDGET,pos -> true,
                pos -> footprint.restore(pos,p -> true,world::get,
                        (p,state) -> { world.put(p,state); return true; }));
        assertEquals(4096,first.restored());
        assertEquals(904,second.restored());
        assertTrue(footprint.pendingRestore.isEmpty());
        assertTrue(world.values().stream().allMatch("air"::equals));
        assertTrue(WorldBatchPaster.RESTORE_SESSION_BUDGET<=WorldBatchPaster.RESTORE_BUDGET);
    }
}
