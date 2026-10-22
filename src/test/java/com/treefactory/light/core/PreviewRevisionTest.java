package com.treefactory.light.core;

import com.treefactory.light.core.tree.PreviewRevision;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewRevisionTest {
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
}
