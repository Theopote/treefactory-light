package com.treefactory.light.core;

import com.treefactory.light.core.tree.PreviewRevision;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewRevisionTest {
    @Test void unappliedAndMissingRevisionsCannotCommit() {
        PreviewRevision revisions=new PreviewRevision();
        UUID player=UUID.randomUUID();
        assertFalse(revisions.isAppliedCurrent(player));
        assertFalse(revisions.readyToCommit(player));
        assertEquals(1,revisions.next(player));
        assertFalse(revisions.readyToCommit(player));
        revisions.markApplied(player,1);
        assertTrue(revisions.readyToCommit(player));
        revisions.clear(player);
        assertFalse(revisions.readyToCommit(player));
    }
    @Test void commitOnlyMatchesLatestAppliedRevision() {
        PreviewRevision revisions = new PreviewRevision();
        UUID player = UUID.randomUUID();
        long first = revisions.next(player);
        long second = revisions.next(player);
        revisions.markApplied(player, first);
        revisions.requestCommit(player, second);
        assertFalse(revisions.shouldCommit(player));
        revisions.markApplied(player, second);
        assertTrue(revisions.shouldCommit(player));
    }

    @Test void commitBlockedWhenLatestPreviewNotApplied() {
        PreviewRevision revisions = new PreviewRevision();
        UUID player = UUID.randomUUID();
        long first = revisions.next(player);
        revisions.markApplied(player, first);
        revisions.next(player);
        assertFalse(revisions.readyToCommit(player));
        revisions.requestCommit(player, first);
        assertFalse(revisions.readyToCommit(player));
    }

    @Test void commitAllowedWhenAppliedMatchesLatestWithoutTarget() {
        PreviewRevision revisions = new PreviewRevision();
        UUID player = UUID.randomUUID();
        long revision = revisions.next(player);
        revisions.markApplied(player, revision);
        assertTrue(revisions.readyToCommit(player));
    }

    @Test void staleRequestsAreDetected() {
        PreviewRevision revisions = new PreviewRevision();
        UUID player = UUID.randomUUID();
        long first = revisions.next(player);
        revisions.next(player);
        assertTrue(revisions.isStale(first, player));
        assertFalse(revisions.isStale(revisions.latest(player), player));
    }

    @Test void clearingOnePlayerDoesNotAffectOtherPlayers() {
        var revisions=new PreviewRevision();
        UUID first=UUID.randomUUID(), second=UUID.randomUUID();
        revisions.markApplied(first,revisions.next(first));
        revisions.markApplied(second,revisions.next(second));
        revisions.requestCommit(first,revisions.latest(first));
        revisions.clear(first);
        assertFalse(revisions.readyToCommit(first));
        assertNull(revisions.commitTarget(first));
        assertTrue(revisions.readyToCommit(second));
        revisions.clearAll();
        assertFalse(revisions.readyToCommit(second));
    }
}
