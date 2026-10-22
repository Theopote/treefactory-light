package com.treefactory.light.core.tree;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Monotonic preview revisions so commit binds to the latest confirmed request. */
public final class PreviewRevision {
    private final Map<UUID, Long> latest = new HashMap<>();
    private final Map<UUID, Long> applied = new HashMap<>();
    private final Map<UUID, Long> commitTarget = new HashMap<>();

    public long next(UUID player) {
        return latest.merge(player, 0L, (a, b) -> a + 1);
    }

    public long latest(UUID player) { return latest.getOrDefault(player, 0L); }

    public long applied(UUID player) { return applied.getOrDefault(player, 0L); }

    public void markApplied(UUID player, long revision) { applied.put(player, revision); }

    public void requestCommit(UUID player, long revision) { commitTarget.put(player, revision); }

    public Long commitTarget(UUID player) { return commitTarget.get(player); }

    public Long clearCommitTarget(UUID player) { return commitTarget.remove(player); }

    public boolean shouldCommit(UUID player) {
        Long target = commitTarget.get(player);
        return target != null && target.equals(applied.get(player));
    }

    public boolean isStale(long revision, UUID player) { return revision < latest(player); }

    public void clear(UUID player) {
        latest.remove(player);
        applied.remove(player);
        commitTarget.remove(player);
    }

    public void clearCommitTargets() { commitTarget.clear(); }
}
