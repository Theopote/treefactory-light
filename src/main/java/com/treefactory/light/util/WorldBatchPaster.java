package com.treefactory.light.util;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.tree.PreviewRevision;
import com.treefactory.light.core.tree.TreePlacementValidator;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.tree.Cell;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live tree preview. Each player has at most one preview tree placed in the world; moving or editing it
 * restores the previous footprint, and only an explicit commit makes it permanent.
 * Only pure generation runs in commonPool. Every world read/write happens on the server thread.
 */
public final class WorldBatchPaster {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // 2 | 16
    private static final int MAX_BUILDS = 4;
    static final int PLACE_BUDGET = 8192;
    static final int PLACE_SESSION_BUDGET = 4096;
    static final int RESTORE_BUDGET = 8192;
    static final int RESTORE_SESSION_BUDGET = 4096;
    static final int UNDO_BUDGET = 8192;
    static final int UNDO_SESSION_BUDGET = 4096;
    private static final int UNDO_LIMIT = 5;
    private static final long THROTTLE_NS = 80_000_000L;
    private static final long PERSIST_THROTTLE_NS = 2_000_000_000L; // batches frequent preview-footprint writes
    /** Farthest distance, in blocks, at which a preview tree may be placed; the client ray uses the same limit. */
    public static final int PLACE_RANGE = 512;
    private record CommittedTree(String dimension, Map<BlockPos,BlockState> placed, Map<BlockPos,BlockState> original) {}
    private record PersistedPreview(UUID player, String dimension, boolean closing,
                                    Map<BlockPos,BlockState> placed, Map<BlockPos,BlockState> original) {}
    private static final class Session extends PreviewFootprint<BlockPos,BlockState> {
        final ServerLevel level;
        boolean closing;
        Session(ServerLevel level) { this.level=level; }
    }
    private record DeferredSession(UUID player, Session session) {}
    private record PendingChunk(ServerLevel level, ChunkPos pos) {}
    private record Request(BlockPos origin, TreeConfig config, long revision) {}
    private record UndoJob(ServerLevel level, CommittedTree tree, BatchedUndo<BlockPos,BlockState> batch) {}
    private static final class Placement {
        final Session session;
        final long revision;
        final JournaledPlacement<BlockPos,BlockState> batch;
        final Map<BlockPos,BlockState> blocks;
        final Session previous;
        final Iterator<BlockPos> restore;
        boolean checkedDeferred;
        Placement(Session session, Session previous, long revision, Map<BlockPos,BlockState> blocks) {
            this.session=session;
            this.previous=previous;
            this.revision=revision;
            this.blocks=blocks;
            this.restore=previous==null ? Collections.emptyIterator()
                    : previous.placed.keySet().stream()
                            .filter(pos -> previous.level!=session.level || !blocks.containsKey(pos)).toList().iterator();
            this.batch=new JournaledPlacement<>(session,previous!=null && previous.level==session.level ? previous : null,blocks);
        }
    }
    private record DiskSnapshot(List<PersistedPreview> previews, Map<UUID,List<CommittedTree>> undo, Path target) {}
    private final Map<UUID,AtomicBoolean> pending = new HashMap<>();
    private final Map<UUID,Placement> placements = new LinkedHashMap<>();
    private final Map<UUID,Session> sessions = new HashMap<>();
    private final List<DeferredSession> deferredSessions = new ArrayList<>();
    private final Set<PendingChunk> pendingChunks = new LinkedHashSet<>();
    private final Set<PendingChunk> failedRestoreChunks = new HashSet<>();
    private final PreviewRevision revisions = new PreviewRevision();
    private final LatestPreviewQueue<UUID,Request> queued = new LatestPreviewQueue<>();
    private final Map<UUID,Long> lastPreview = new HashMap<>();
    private final Map<UUID,ArrayDeque<CommittedTree>> undo = new HashMap<>();
    private final Map<UUID,UndoJob> undoJobs = new LinkedHashMap<>();
    // Single background thread so writes to the save file are always serialized, never concurrent.
    private final ExecutorService persistExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "treefactory-light-persist"); t.setDaemon(true); return t;
    });
    private final CoalescedPersistQueue persistQueue = new CoalescedPersistQueue();
    private MinecraftServer server;
    private boolean storageUnavailable;
    private volatile boolean dirty;
    private long lastPersistNs;
    private int restoreCursor;
    private int restoreBudgetRemaining=RESTORE_BUDGET;
    @FunctionalInterface
    public interface CommitNotifier { void notify(ServerPlayer player, long requestId, boolean success); }
    private CommitNotifier commitNotifier = (player, requestId, success) -> {};
    private final Map<UUID,Long> commitRequests = new HashMap<>();

    public void setCommitNotifier(CommitNotifier notifier) {
        this.commitNotifier = Objects.requireNonNull(notifier);
    }

    private void notifyCommitResult(ServerPlayer player, boolean success) {
        Long requestId=commitRequests.remove(player.getUUID());
        if (requestId!=null) commitNotifier.notify(player, requestId, success);
    }
    public static boolean holdingWand(ServerPlayer p) {
        return p.getMainHandItem().is(TreefactoryLight.TREE_WAND);
    }
    public static EnumMap<Voxelizer.Material,BlockState> palette(TreeConfig config) {
        var p=config.blockPalette();
        EnumMap<Voxelizer.Material,BlockState> result=new EnumMap<>(Voxelizer.Material.class);
        result.put(Voxelizer.Material.MAIN_TRUNK,PaletteRules.resolve(PaletteRules.Role.TRUNK,p.mainTrunk()));
        result.put(Voxelizer.Material.THICK_BRANCH,PaletteRules.resolve(PaletteRules.Role.THICK_BRANCH,p.thickBranch()));
        result.put(Voxelizer.Material.THIN_BRANCH,PaletteRules.resolve(PaletteRules.Role.THIN_BRANCH,p.thinBranch()));
        result.put(Voxelizer.Material.PRIMARY_LEAF,PaletteRules.resolve(PaletteRules.Role.PRIMARY_LEAF,p.primaryLeaf()));
        result.put(Voxelizer.Material.SECONDARY_LEAF,PaletteRules.resolve(PaletteRules.Role.SECONDARY_LEAF,p.secondaryLeaf()));
        result.put(Voxelizer.Material.ACCENT,PaletteRules.resolve(PaletteRules.Role.ACCENT,p.accentBlock()));
        return result;
    }
    public static BlockState orient(BlockState state,Voxelizer.Axis axis) {
        if(axis==null || !state.hasProperty(BlockStateProperties.AXIS)) return state;
        Direction.Axis direction=switch(axis) {
            case X -> Direction.Axis.X; case Y -> Direction.Axis.Y; case Z -> Direction.Axis.Z;
        };
        return state.setValue(BlockStateProperties.AXIS,direction);
    }
    /** Keeps the current update running while coalescing newer requests to the latest one. */
    public void preview(ServerPlayer player, BlockPos origin, TreeConfig config) {
        if (storageUnavailable) { message(player,"treefactory.storage_unavailable"); return; }
        if (!authorized(player)) return;
        UUID id = player.getUUID();
        if (undoJobs.containsKey(id)) { message(player,"treefactory.undo_busy"); return; }
        long revision = revisions.next(id);
        queued.put(id, new Request(origin, config, revision));
        startQueued();
    }

    private void startQueued() {
        if (server==null) return;
        for (UUID id : queued.keys()) {
            if (pending.containsKey(id) || placements.containsKey(id)) continue;
            if (hasLoadedRestorations(id)) continue;
            if (pending.size()>=MAX_BUILDS) return;
            Long last=lastPreview.get(id);
            // A request pending commit must not be stuck behind the throttle: the player
            // already closed the UI and nothing else will ever flush it.
            if (revisions.commitTarget(id)==null && last!=null && System.nanoTime()-last<THROTTLE_NS) continue;
            Request request=queued.takeIfIdle(id,pending.containsKey(id) || placements.containsKey(id));
            if (request==null) continue;
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if (player==null) { revisions.clearCommitTarget(id); commitRequests.remove(id); continue; }
            if (!authorized(player)) {
                if (revisions.clearCommitTarget(id)!=null) notifyCommitResult(player, false);
                continue;
            }
            beginPreview(player, request);
        }
    }
    private void beginPreview(ServerPlayer player, Request request) {
        UUID id = player.getUUID();
        BlockPos origin = request.origin();
        TreeConfig config = request.config();
        long revision = request.revision();
        ServerLevel level = player.level();
        if(player.distanceToSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5)>PLACE_RANGE*PLACE_RANGE) {
            rejectPreview(player, revision, "treefactory.target_too_far", PLACE_RANGE); return;
        }
        String originProblem=inaccessibleReason(level,origin);
        if(originProblem!=null) { rejectPreview(player, revision, originProblem); return; }
        if(!level.mayInteract(player,origin)) { rejectPreview(player, revision, "treefactory.target_protected"); return; }
        final EnumMap<Voxelizer.Material,BlockState> materials;
        try { materials=palette(config); }
        catch (IllegalArgumentException e) { rejectPreview(player, revision, "treefactory.invalid_palette"); return; }
        AtomicBoolean cancel = new AtomicBoolean();
        pending.put(id,cancel);
        lastPreview.put(id, System.nanoTime());
        var running = level.getServer();
        CompletableFuture.supplyAsync(() -> {
            var result = new com.treefactory.light.core.tree.TreeBuildTask().build(config,cancel::get);
            return result;
        },ForkJoinPool.commonPool()).whenComplete((result,error) -> {
            if (cancel.get()) {
                running.execute(() -> finishCancelledBuild(id, cancel));
                return;
            }
            running.execute(() -> {
                if (pending.get(id)!=cancel) return;
                pending.remove(id);
                ServerPlayer current = running.getPlayerList().getPlayer(id);
                if (current==null || current.level()!=level || !authorized(current)) { discard(id); startQueued(); return; }
                boolean applied=false;
                if (error!=null) {
                    TreefactoryLight.LOGGER.warn("Tree generation failed",error);
                    message(current,"treefactory.failed");
                } else {
                    restoreLoadedChunks();
                    var validation=validatePlacement(current, level, origin, result.blocks(), sessions.get(id));
                    if (!validation.ok()) message(current, validation.messageKey());
                    else {
                        Map<BlockPos,BlockState> blocks = new LinkedHashMap<>();
                        validation.placeable().forEach((cell, material) -> blocks.put(origin.offset(cell.x(),cell.y(),cell.z()),
                                orient(materials.get(material), result.axes().get(cell))));
                        resolveConnections(level,blocks,sessions.get(id));
                        beginPlacement(current, level, blocks, revision);
                        applied=true;
                    }
                }
                startQueued();
                tryFinishCommit(current, applied);
            });
        });
    }

    private void finishCancelledBuild(UUID id, AtomicBoolean cancel) {
        if (pending.get(id) == cancel) pending.remove(id);
        startQueued();
    }

    /** Reject a request that failed before generation and report any matching pending commit. */
    private void rejectPreview(ServerPlayer player, long revision, String key, Object... args) {
        message(player, key, args);
        if (Objects.equals(revisions.commitTarget(player.getUUID()), revision)) {
            revisions.clearCommitTarget(player.getUUID());
            message(player, "treefactory.commit_failed");
            notifyCommitResult(player, false);
        }
    }
    private void tryFinishCommit(ServerPlayer player) { tryFinishCommit(player, true); }
    private void tryFinishCommit(ServerPlayer player, boolean latestApplied) {
        UUID id=player.getUUID();
        if (pending.containsKey(id) || queued.containsKey(id) || placements.containsKey(id)) return;
        if (!revisions.shouldCommit(id)) {
            if (revisions.commitTarget(id)!=null && !latestApplied) {
                revisions.clearCommitTarget(id);
                message(player, "treefactory.commit_failed");
                notifyCommitResult(player, false);
            }
            return;
        }
        revisions.clearCommitTarget(id);
        commit(player);
    }
    /** Keeps the current preview tree permanently (after any in-flight rebuild finishes). */
    public void requestCommit(ServerPlayer player, long requestId) {
        commitRequests.put(player.getUUID(), requestId);
        if (undoJobs.containsKey(player.getUUID())) {
            message(player,"treefactory.undo_busy");
            notifyCommitResult(player,false);
            return;
        }
        if (storageUnavailable) {
            message(player,"treefactory.storage_unavailable");
            notifyCommitResult(player,false);
            return;
        }
        if (!authorized(player)) {
            revisions.clearCommitTarget(player.getUUID());
            notifyCommitResult(player,false);
            return;
        }
        commit(player);
    }
    private void commit(ServerPlayer player) {
        UUID id=player.getUUID();
        Session current=sessions.get(id);
        if (!authorized(player) || (current!=null && current.level!=player.level())) {
            revisions.clearCommitTarget(id);
            message(player,"treefactory.commit_failed");
            notifyCommitResult(player,false);
            return;
        }
        if (pending.containsKey(id) || queued.containsKey(id) || placements.containsKey(id)) {
            revisions.requestCommit(id, revisions.latest(id));
            startQueued();
            return;
        }
        if (!revisions.readyToCommit(id)) {
            revisions.clearCommitTarget(id);
            message(player, "treefactory.commit_failed");
            notifyCommitResult(player, false);
            return;
        }
        revisions.clearCommitTarget(id);
        Session session=sessions.remove(id);
        if (session==null) {
            notifyCommitResult(player, false);
            return;
        }
        var committed=session.takeCommitted();
        defer(id, session);
        ArrayDeque<CommittedTree> stack=undo.computeIfAbsent(id, k -> new ArrayDeque<>());
        stack.addLast(new CommittedTree(session.level.dimension().identifier().toString(),
                committed.placed(), committed.original()));
        trimUndoHistory(stack);
        revisions.clear(id);
        persist(true);
        player.sendSystemMessage(Component.translatable("treefactory.built",committed.placed().size()),false);
        notifyCommitResult(player, true);
    }
    public void undo(ServerPlayer player) {
        if (storageUnavailable) { message(player,"treefactory.storage_unavailable"); return; }
        if (!authorized(player)) return;
        UUID id=player.getUUID();
        if (undoJobs.containsKey(id) || sessions.containsKey(id) || placements.containsKey(id)
                || pending.containsKey(id) || queued.containsKey(id)) {
            message(player,"treefactory.undo_busy");
            return;
        }
        ArrayDeque<CommittedTree> stack=undo.get(id);
        if (stack==null || stack.isEmpty()) { message(player,"treefactory.nothing_to_undo"); return; }
        CommittedTree tree=stack.peekLast();
        ServerLevel level=level(tree.dimension());
        if (level==null) { message(player,"treefactory.undo_dimension"); return; }
        undoJobs.put(id,new UndoJob(level,tree,new BatchedUndo<>(tree.placed(),tree.original())));
        message(player,"treefactory.undo_started");
    }

    private void tickUndos() {
        int budget=UNDO_BUDGET;
        for (UUID id : new ArrayList<>(undoJobs.keySet())) {
            if (budget<=0) break;
            UndoJob job=undoJobs.remove(id);
            if (job==null) continue;
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if (player==null) continue;
            if (!authorized(player)) continue;
            var result=job.batch().tick(Math.min(budget,UNDO_SESSION_BUDGET),
                    pos -> chunkReady(job.level(),pos), pos -> job.level().mayInteract(player,pos),
                    job.level()::getBlockState, (pos,state) -> job.level().setBlock(pos,state,FLAGS));
            budget-=result.consumed();
            switch (result.status()) {
                case PROGRESS -> undoJobs.put(id,job);
                case COMPLETE -> {
                    ArrayDeque<CommittedTree> stack=undo.get(id);
                    if (stack==null || stack.peekLast()!=job.tree()) {
                        TreefactoryLight.LOGGER.error("Undo history changed during undo for {}",id);
                        message(player,"treefactory.undo_failed");
                        break;
                    }
                    stack.removeLast();
                    persist(true);
                    if (result.restored()==0) message(player,"treefactory.undo_nothing_left");
                    else player.sendSystemMessage(Component.translatable("treefactory.undone",result.restored()),false);
                }
                case UNLOADED -> message(player,"treefactory.undo_unloaded");
                case PROTECTED -> message(player,"treefactory.target_protected");
                case WRITE_FAILED -> message(player,result.restored()>0 ? "treefactory.undo_partial_failed" : "treefactory.undo_failed");
            }
        }
    }
    /** Removes the preview tree and restores the world. */
    public void discard(UUID id) {
        cancelPlacement(id);
        AtomicBoolean flag=pending.remove(id); if(flag!=null) flag.set(true);
        commitRequests.remove(id);
        revisions.clearCommitTarget(id);
        queued.remove(id);
        Session session=sessions.remove(id);
        if (session==null) return;
        scheduleRestoration(session);
        defer(id,session);
        dirty=true;
    }
    public void disconnect(UUID id) { undoJobs.remove(id); discard(id); persist(true); }
    public void attach(MinecraftServer server) {
        this.server=server;
        storageUnavailable=!load();
        if (storageUnavailable) return;
        for (DeferredSession deferred : deferredSessions) restoreAll(deferred.session());
        deferredSessions.removeIf(deferred -> deferred.session().placed.isEmpty());
        persist(true);
    }
    public void stop() {
        pending.values().forEach(f -> f.set(true)); pending.clear(); revisions.clearCommitTargets(); queued.clear();
        for (UUID id : new ArrayList<>(placements.keySet())) cancelPlacement(id);
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Session session=sessions.get(id);
            if (session==null) continue;
            session.closing=true;
            restoreAll(session);
            if (session.placed.isEmpty()) sessions.remove(id);
        }
        for (DeferredSession deferred : deferredSessions) restoreAll(deferred.session());
        deferredSessions.removeIf(deferred -> deferred.session().placed.isEmpty());
        // Last chance to write before the process exits: block until the save actually lands on disk.
        Future<?> finalWrite=persist(true);
        if (finalWrite!=null) {
            try { finalWrite.get(10, TimeUnit.SECONDS); }
            catch (Exception e) { TreefactoryLight.LOGGER.warn("Timed out saving preview state on shutdown", e); }
        }
        sessions.clear(); deferredSessions.clear(); pendingChunks.clear(); failedRestoreChunks.clear(); commitRequests.clear(); undoJobs.clear(); undo.clear(); server=null;
    }
    public void tick() {
        restoreBudgetRemaining=RESTORE_BUDGET;
        restoreLoadedChunks();
        tickUndos();
        startQueued();
        tickPlacements();
        if (dirty) persist(false);
    }
    public void chunkLoaded(ServerLevel level, ChunkPos chunk) {
        if (!sessions.isEmpty() || !deferredSessions.isEmpty())
            pendingChunks.add(new PendingChunk(level, chunk));
    }
    private void restoreLoadedChunks() {
        int[] budget={restoreBudgetRemaining};
        for (PendingChunk chunk : new ArrayList<>(pendingChunks)) {
            if (budget[0] == 0) break;
            pendingChunks.remove(chunk);
            if (chunk.level().getChunk(chunk.pos().x(), chunk.pos().z(), ChunkStatus.FULL, false)==null) {
                failedRestoreChunks.remove(chunk);
                continue;
            }
            if (restoreChunk(chunk.level(), chunk.pos(), budget)) pendingChunks.add(chunk);
            else failedRestoreChunks.remove(chunk);
        }
        restoreBudgetRemaining=budget[0];
    }
    private boolean restoreChunk(ServerLevel level, ChunkPos chunk, int[] budget) {
        boolean changed=false;
        List<Session> all=new ArrayList<>(sessions.values());
        for (DeferredSession deferred : deferredSessions) all.add(deferred.session());
        if (!all.isEmpty()) {
            int start=Math.floorMod(restoreCursor++, all.size());
            for (int i=0; i<all.size() && budget[0]>0; i++)
                changed|=restoreChunk(all.get((start+i)%all.size()), level, chunk, budget);
        }
        changed|=sessions.entrySet().removeIf(e -> e.getValue().closing && e.getValue().placed.isEmpty());
        changed|=deferredSessions.removeIf(deferred -> deferred.session().placed.isEmpty());
        if (changed) dirty=true;
        for (Session session : sessions.values()) if (hasPendingChunk(session, level, chunk)) return true;
        for (DeferredSession deferred : deferredSessions)
            if (hasPendingChunk(deferred.session(), level, chunk)) return true;
        return false;
    }
    private static boolean hasPendingChunk(Session session, ServerLevel level, ChunkPos chunk) {
        if (session.level!=level) return false;
        for (BlockPos pos : session.pendingRestore)
            if (chunk.x()==SectionPos.blockToSectionCoord(pos.getX()) && chunk.z()==SectionPos.blockToSectionCoord(pos.getZ()))
                return true;
        return false;
    }
    private boolean restoreChunk(Session session, ServerLevel level, ChunkPos chunk, int[] budget) {
        if (session.level!=level || budget[0]==0) return false;
        var result=session.retryPending(Math.min(budget[0], RESTORE_SESSION_BUDGET),
                pos -> chunk.x()==SectionPos.blockToSectionCoord(pos.getX())
                        && chunk.z()==SectionPos.blockToSectionCoord(pos.getZ()),
                pos -> restore(session, pos));
        budget[0]-=result.attempted();
        return result.restored()>0;
    }
    private void defer(UUID id, Session session) {
        if (session.placed.isEmpty()) return;
        session.closing=true;
        deferredSessions.add(new DeferredSession(id, session));
    }
    static TreePlacementValidator.Result validatePlacement(ServerPlayer player, ServerLevel level, BlockPos origin,
            Map<Cell,Voxelizer.Material> blocks, Session session) {
        return TreePlacementValidator.validate(blocks,
                cell -> inaccessibleReason(level, origin.offset(cell.x(), cell.y(), cell.z())),
                cell -> level.mayInteract(player, origin.offset(cell.x(), cell.y(), cell.z())),
                cell -> {
                    BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
                    if (session != null && session.level==level && session.placed.containsKey(pos)) return true;
                    return replaceable(level.getBlockState(pos));
                });
    }

    private static void resolveConnections(ServerLevel level, Map<BlockPos,BlockState> blocks, Session previous) {
        if (blocks.values().stream().noneMatch(ConnectedBlockStates::supported)) return;
        var view=ConnectedBlockStates.view(level,pos -> {
            BlockState target=blocks.get(pos);
            if (target!=null) return target;
            if (level.isOutsideBuildHeight(pos) || !chunkReady(level,pos)) return Blocks.AIR.defaultBlockState();
            BlockState current=level.getBlockState(pos);
            if (previous!=null && previous.level==level && current.equals(previous.placed.get(pos)))
                return Objects.requireNonNull(previous.original.get(pos),"Missing preview original state");
            return current;
        });
        blocks.replaceAll((pos,state) -> ConnectedBlockStates.resolve(view,pos,state));
    }

    private void beginPlacement(ServerPlayer player,ServerLevel level,Map<BlockPos,BlockState> blocks,long revision) {
        UUID id=player.getUUID();
        Session old=sessions.remove(id);
        if (old!=null) {
            defer(id,old);
            dirty=true;
        }
        Session session=new Session(level);
        sessions.put(id,session);
        placements.put(id,new Placement(session,old,revision,blocks));
    }

    private void cancelPlacement(UUID id) {
        Placement placement=placements.remove(id);
        if (placement!=null) abortPlacement(id,placement);
    }

    private void abortPlacement(UUID id, Placement placement) {
        Session session=placement.session;
        placement.batch.forgetUnwritten();
        if (placement.previous!=null) scheduleRestoration(placement.previous);
        sessions.remove(id,session);
        scheduleRestoration(session);
        defer(id,session);
        dirty=true;
    }

    private void failPlacement(UUID id, Placement placement, ServerPlayer player, String reason) {
        placements.remove(id);
        abortPlacement(id,placement);
        if (player!=null) {
            message(player,reason);
            tryFinishCommit(player,false);
        } else {
            commitRequests.remove(id);
            revisions.clearCommitTarget(id);
        }
    }

    private void tickPlacements() {
        int budget=PLACE_BUDGET;
        for (UUID id : new ArrayList<>(placements.keySet())) {
            if (budget<=0) break;
            Placement placement=placements.get(id);
            if (placement==null) continue;
            placements.remove(id);
            placements.put(id,placement);
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if (player==null || player.level()!=placement.session.level || !authorized(player)) {
                failPlacement(id,placement,player,"treefactory.preview_placement_failed");
                continue;
            }
            int sessionBudget=PLACE_SESSION_BUDGET;
            if (placement.restore.hasNext()) {
                int count=Math.min(budget,sessionBudget);
                while (count-- > 0 && placement.restore.hasNext()) {
                    restore(placement.previous,placement.restore.next());
                    budget--;
                    sessionBudget--;
                }
                dirty=true;
                if (placement.restore.hasNext() || budget==0 || sessionBudget==0) continue;
            }
            if (!placement.checkedDeferred) {
                boolean overlap=false;
                for (DeferredSession deferred : deferredSessions) {
                    if (deferred.session()!=placement.previous && deferred.session().level==placement.session.level
                            && !Collections.disjoint(deferred.session().placed.keySet(),placement.blocks.keySet())) {
                        overlap=true;
                        break;
                    }
                }
                if (overlap) {
                    failPlacement(id,placement,player,"treefactory.preview_placement_failed");
                    continue;
                }
                placement.checkedDeferred=true;
                deferredSessions.removeIf(deferred -> deferred.session().placed.isEmpty());
            }
            ServerLevel level=placement.session.level;
            var result=placement.batch.tick(budget,sessionBudget,
                    pos -> inaccessibleReason(level,pos)==null && level.mayInteract(player,pos)
                            && !hasDeferredFootprint(level,pos,placement.previous),
                    level::getBlockState,WorldBatchPaster::replaceable,
                    (pos,state) -> level.setBlock(pos,state,FLAGS),() -> persist(true));
            budget-=result.consumed();
            switch (result.status()) {
                case FAILED -> failPlacement(id,placement,player,"treefactory.preview_placement_failed");
                case STORAGE_FAILED -> {
                    if (result.error()!=null)
                        TreefactoryLight.LOGGER.error("Could not journal tree preview placement for {}",id,result.error());
                    failPlacement(id,placement,player,"treefactory.storage_write_failed");
                }
                case COMPLETE -> {
                    placements.remove(id);
                    revisions.markApplied(id,placement.revision);
                    tryFinishCommit(player);
                }
                case WAITING, PROGRESS -> { }
            }
        }
    }
    private boolean restore(Session session,BlockPos pos) {
        boolean restored=session.restore(pos, p -> chunkReady(session.level, p), session.level::getBlockState,
                (p, state) -> session.level.setBlock(p,state,FLAGS));
        if (!restored && chunkReady(session.level, pos)) {
            PendingChunk chunk=new PendingChunk(session.level, new ChunkPos(
                    SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
            pendingChunks.add(chunk);
            if (failedRestoreChunks.add(chunk)) TreefactoryLight.LOGGER.warn("Could not restore preview block at {} in {}; will retry",
                    pos, session.level.dimension().identifier());
        }
        return restored;
    }
    private void restoreAll(Session session) {
        for (BlockPos pos:new ArrayList<>(session.placed.keySet())) restore(session,pos);
    }
    private void scheduleRestoration(Session session) {
        for (BlockPos pos : session.placed.keySet()) {
            session.pendingRestore.add(pos);
            pendingChunks.add(new PendingChunk(session.level, new ChunkPos(
                    SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))));
        }
    }
    private boolean hasLoadedRestorations(UUID id) {
        for (DeferredSession deferred : deferredSessions) {
            if (!deferred.player().equals(id)) continue;
            Session session=deferred.session();
            for (BlockPos pos : session.pendingRestore)
                if (chunkReady(session.level,pos)) return true;
        }
        return false;
    }
    private boolean hasDeferredFootprint(ServerLevel level, BlockPos pos, Session previous) {
        for (DeferredSession deferred : deferredSessions)
            if (deferred.session()!=previous && deferred.session().level==level
                    && deferred.session().placed.containsKey(pos)) return true;
        return false;
    }
    private static String inaccessibleReason(ServerLevel level,BlockPos pos) {
        if(level.isOutsideBuildHeight(pos)) return "treefactory.target_height";
        if(!level.getWorldBorder().isWithinBounds(pos)) return "treefactory.target_border";
        if(!chunkReady(level, pos)) return "treefactory.target_unloaded";
        return null;
    }
    /** Same check as deprecated {@code hasChunkAt}, without loading or generating chunks. */
    static boolean chunkReady(ServerLevel level, BlockPos pos) {
        return level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()),
                ChunkStatus.FULL, false) != null;
    }
    static boolean replaceable(BlockState state) {
        return !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof VineBlock) && !state.is(BlockTags.CLIMBABLE)
                && (state.isAir() || state.canBeReplaced() || state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LEAVES));
    }
    private static boolean authorized(ServerPlayer p) {
        if (!p.isCreative() || !holdingWand(p)) { message(p,"treefactory.creative_only"); return false; }
        return true;
    }
    private static void message(ServerPlayer p,String key,Object... args) { p.sendOverlayMessage(Component.translatable(key,args)); }
    private Path dataFile() {
        return server.getWorldPath(LevelResource.ROOT).resolve("treefactory_light.dat");
    }
    /**
     * Snapshots preview/undo state on the caller's thread (plain map copies only, no NBT/gzip work) and then
     * does the expensive NBT build + gzip + file write off-thread on a single-threaded executor, so writes are
     * never concurrent with each other. {@code immediate} bypasses the throttle (used by commit/undo/shutdown,
     * which must not wait behind a routine preview-footprint save); {@link #tick()} always passes {@code false}
     * so dragging a preview around does not serialize/gzip the whole file on every changed tick.
     * Returns the scheduled write, or {@code null} if nothing was scheduled.
     */
    private CompletableFuture<Void> persist(boolean immediate) {
        if (server==null || storageUnavailable) return null;
        if (!immediate) {
            long now=System.nanoTime();
            if (now-lastPersistNs<PERSIST_THROTTLE_NS) { dirty=true; return null; }
        }
        dirty=false;
        lastPersistNs=System.nanoTime();
        DiskSnapshot snapshot=captureDiskSnapshot();
        return persistQueue.enqueue(snapshot, payload -> {
            DiskSnapshot disk=(DiskSnapshot)payload;
            writeToDisk(disk.previews(), disk.undo(), disk.target());
        }, persistExecutor).whenComplete((ok,error) -> {
            if (error!=null) {
                TreefactoryLight.LOGGER.warn("Could not save preview state", error);
                dirty=true;
            }
        });
    }
    private DiskSnapshot captureDiskSnapshot() {
        List<PersistedPreview> previews=new ArrayList<>();
        sessions.forEach((id,session) -> previews.add(new PersistedPreview(id,
                session.level.dimension().identifier().toString(), session.closing,
                Map.copyOf(session.placed), Map.copyOf(session.original))));
        deferredSessions.forEach(deferred -> {
            Session session=deferred.session();
            previews.add(new PersistedPreview(deferred.player(), session.level.dimension().identifier().toString(), true,
                    Map.copyOf(session.placed), Map.copyOf(session.original)));
        });
        Map<UUID,List<CommittedTree>> undoSnapshot=new LinkedHashMap<>();
        undo.forEach((id,stack) -> undoSnapshot.put(id, List.copyOf(stack)));
        return new DiskSnapshot(previews, undoSnapshot, dataFile());
    }
    private static void writeToDisk(List<PersistedPreview> previews, Map<UUID,List<CommittedTree>> undoSnapshot, Path target) throws IOException {
        CompoundTag root=new CompoundTag();
        ListTag previewsTag=new ListTag();
        for (PersistedPreview snapshot : previews) {
            CompoundTag tag=new CompoundTag();
            tag.putString("player", snapshot.player().toString());
            tag.putString("dimension", snapshot.dimension());
            tag.putBoolean("closing", snapshot.closing());
            tag.put("blocks", footprint(snapshot.placed(), snapshot.original()));
            previewsTag.add(tag);
        }
        root.put("previews", previewsTag);
        ListTag history=new ListTag();
        undoSnapshot.forEach((id,stack) -> {
            CompoundTag tag=new CompoundTag();
            tag.putString("player", id.toString());
            ListTag entries=new ListTag();
            for (CommittedTree tree : stack) {
                CompoundTag item=new CompoundTag();
                item.putString("dimension", tree.dimension());
                item.put("placed", states(tree.placed()));
                item.put("original", states(tree.original()));
                entries.add(item);
            }
            tag.put("entries", entries);
            history.add(tag);
        });
        root.put("undo", history);
        // Write to a temp file and move it into place so a crash or power loss mid-write cannot leave
        // a corrupted save (which would otherwise lose both the preview footprint and the undo history).
        Path tmp=target.resolveSibling(target.getFileName().toString()+".tmp");
        NbtIo.writeCompressed(root, tmp);
        try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private boolean load() {
        Path path=dataFile();
        if (!Files.isRegularFile(path)) return true;
        try {
            readState(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
            return true;
        } catch (IOException | RuntimeException e) {
            sessions.clear(); deferredSessions.clear(); undo.clear();
            TreefactoryLight.LOGGER.error("Could not load preview state from {}; tree previews and undo are disabled to preserve the file",path,e);
            return false;
        }
    }
    private void readState(CompoundTag root) {
        sessions.clear(); deferredSessions.clear(); undo.clear();
        var lookup=server.registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag previewsIn=list(root, "previews");
        for (Tag raw : previewsIn) {
            if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid preview entry");
            UUID id=UUID.fromString(string(tag, "player"));
            ServerLevel world=level(string(tag, "dimension"));
            if (world==null) throw new IllegalArgumentException("Preview dimension is unavailable");
            Session session=new Session(world);
            session.closing=true;
            readFootprint(lookup, list(tag, "blocks"), session);
            defer(id, session);
        }
        ListTag historyIn=list(root, "undo");
        for (Tag raw : historyIn) {
            if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid undo entry");
            UUID id=UUID.fromString(string(tag, "player"));
            ArrayDeque<CommittedTree> stack=undo.computeIfAbsent(id, k -> new ArrayDeque<>());
            ListTag entries=list(tag, "entries");
            for (Tag itemRaw : entries) {
                if (!(itemRaw instanceof CompoundTag item)) throw new IllegalArgumentException("Invalid undo history item");
                stack.addLast(new CommittedTree(string(item, "dimension"),
                        readStates(lookup, list(item, "placed")), readStates(lookup, list(item, "original"))));
                trimUndoHistory(stack);
            }
        }
    }
    private ServerLevel level(String id) {
        Identifier identifier=Identifier.parse(id);
        for (ServerLevel level : server.getAllLevels())
            if (level.dimension().identifier().equals(identifier)) return level;
        return null;
    }
    static void trimUndoHistory(Deque<?> stack) {
        while (stack.size()>UNDO_LIMIT) stack.removeFirst();
    }
    private static ListTag footprint(Map<BlockPos,BlockState> placed, Map<BlockPos,BlockState> original) {
        ListTag list=new ListTag();
        placed.forEach((pos, state) -> {
            CompoundTag tag=new CompoundTag();
            tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
            tag.put("placed", NbtUtils.writeBlockState(state));
            BlockState before=original.get(pos);
            if (before!=null) tag.put("original", NbtUtils.writeBlockState(before));
            list.add(tag);
        });
        return list;
    }
    private static void readFootprint(HolderGetter<Block> lookup, ListTag list, Session session) {
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid preview block");
            BlockPos pos=new BlockPos(integer(tag,"x"), integer(tag,"y"), integer(tag,"z"));
            session.placed.put(pos, readBlockState(lookup, compound(tag, "placed")));
            if (tag.contains("original")) session.original.put(pos, readBlockState(lookup, compound(tag, "original")));
        }
    }
    private static ListTag states(Map<BlockPos,BlockState> blocks) {
        ListTag list=new ListTag();
        blocks.forEach((pos, state) -> {
            CompoundTag tag=new CompoundTag();
            tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
            tag.put("state", NbtUtils.writeBlockState(state));
            list.add(tag);
        });
        return list;
    }
    private static Map<BlockPos,BlockState> readStates(HolderGetter<Block> lookup, ListTag list) {
        Map<BlockPos,BlockState> result=new LinkedHashMap<>();
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid undo block");
            result.put(new BlockPos(integer(tag,"x"), integer(tag,"y"), integer(tag,"z")),
                    readBlockState(lookup, compound(tag, "state")));
        }
        return result;
    }
    private static String string(CompoundTag tag, String key) {
        return tag.getString(key).orElseThrow(() -> new IllegalArgumentException("Missing string: "+key));
    }
    private static int integer(CompoundTag tag, String key) {
        return tag.getInt(key).orElseThrow(() -> new IllegalArgumentException("Missing integer: "+key));
    }
    private static ListTag list(CompoundTag tag, String key) {
        return tag.getList(key).orElseThrow(() -> new IllegalArgumentException("Missing list: "+key));
    }
    private static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompound(key).orElseThrow(() -> new IllegalArgumentException("Missing compound: "+key));
    }
    static BlockState readBlockState(HolderGetter<Block> lookup, CompoundTag tag) {
        BlockState state=NbtUtils.readBlockState(lookup,tag);
        if (!NbtUtils.writeBlockState(state).equals(tag))
            throw new IllegalArgumentException("Invalid or unavailable saved block state: "+tag);
        return state;
    }
}
