package com.treefactory.light.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchedUndoTest {
    @Test void largeUndoRespectsBudgetAndRestoresEveryBlock() {
        Map<Integer,Integer> placed=new LinkedHashMap<>(), original=new LinkedHashMap<>(), world=new LinkedHashMap<>();
        for (int i=0;i<80_000;i++) { placed.put(i,1); original.put(i,0); world.put(i,1); }
        var undo=new BatchedUndo<>(placed,original);
        BatchedUndo.Result result;
        int ticks=0;
        do {
            int[] writes={0};
            result=undo.tick(4096, pos -> true, pos -> true, world::get,
                    (pos,state) -> { writes[0]++; world.put(pos,state); return true; });
            assertTrue(result.consumed()<=4096);
            assertTrue(writes[0]<=4096);
            ticks++;
        } while (result.status()==BatchedUndo.Status.PROGRESS);
        assertEquals(BatchedUndo.Status.COMPLETE,result.status());
        assertEquals(80_000,result.restored());
        assertEquals(20,ticks);
        assertEquals(original,world);
    }

    @Test void changedBlocksAreNotOverwrittenAndFailuresCanBeRetried() {
        Map<Integer,Integer> placed=Map.of(1,1,2,1,3,1), original=Map.of(1,0,2,0,3,0);
        Map<Integer,Integer> world=new LinkedHashMap<>(placed);
        var first=new BatchedUndo<>(placed,original);
        var partial=first.tick(1, p -> true, p -> true, world::get, (p,s) -> { world.put(p,s); return true; });
        assertEquals(BatchedUndo.Status.PROGRESS,partial.status());
        int modified=world.entrySet().stream().filter(e -> e.getValue()==1).findFirst().orElseThrow().getKey();
        world.put(modified,9);
        var failed=first.tick(10, p -> true, p -> false, world::get, (p,s) -> fail("protected"));
        assertEquals(BatchedUndo.Status.PROTECTED,failed.status());
        var retry=new BatchedUndo<>(placed,original);
        var done=retry.tick(10, p -> true, p -> true, world::get, (p,s) -> { world.put(p,s); return true; });
        assertEquals(BatchedUndo.Status.COMPLETE,done.status());
        assertEquals(9,world.get(modified));
        assertEquals(2,world.values().stream().filter(v -> v==0).count());
    }

    @Test void unloadedAndWriteFailureRetainRemainingHistoryForRetry() {
        Map<Integer,Integer> placed=Map.of(1,1), original=Map.of(1,0);
        assertEquals(BatchedUndo.Status.UNLOADED,
                new BatchedUndo<>(placed,original).tick(1, p -> false, p -> true, p -> 1, (p,s) -> true).status());
        assertEquals(BatchedUndo.Status.WRITE_FAILED,
                new BatchedUndo<>(placed,original).tick(1, p -> true, p -> true, p -> 1, (p,s) -> false).status());
    }
}
