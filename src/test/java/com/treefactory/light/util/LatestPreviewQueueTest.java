package com.treefactory.light.util;

import com.treefactory.light.core.tree.PreviewRevision;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LatestPreviewQueueTest {
    @Test void continuousChangesWaitForCurrentWorkAndCoalesceToLatest() {
        var queue=new LatestPreviewQueue<String,Integer>();
        queue.put("player",1);
        assertEquals(1,queue.takeIfIdle("player",false));
        for (int revision=2; revision<=20; revision++) {
            queue.put("player",revision);
            assertNull(queue.takeIfIdle("player",true));
        }
        assertEquals(20,queue.takeIfIdle("player",false));
        assertFalse(queue.containsKey("player"));
    }

    @Test void cancelDiscardsWaitingRequestWithoutAffectingOtherPlayers() {
        var queue=new LatestPreviewQueue<String,Integer>();
        queue.put("a",1);
        queue.put("b",2);
        queue.remove("a");
        assertNull(queue.takeIfIdle("a",false));
        assertEquals(2,queue.takeIfIdle("b",false));
    }

    @Test void intermediateUpdatesCannotSatisfyFinalCommit() {
        var player=UUID.randomUUID();
        var revisions=new PreviewRevision();
        var queue=new LatestPreviewQueue<UUID,Long>();
        long first=revisions.next(player);
        queue.put(player,first);
        assertEquals(first,queue.takeIfIdle(player,false));
        long second=revisions.next(player);
        queue.put(player,second);
        long latest=revisions.next(player);
        queue.put(player,latest);
        revisions.requestCommit(player,latest);
        revisions.markApplied(player,first);
        assertFalse(revisions.shouldCommit(player));
        assertEquals(latest,queue.takeIfIdle(player,false));
        revisions.markApplied(player,latest);
        assertTrue(revisions.shouldCommit(player));
    }
}
