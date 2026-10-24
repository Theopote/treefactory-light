package com.treefactory.light.util;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.tree.PlacementHistory;
import com.treefactory.light.core.tree.PreviewRevision;
import com.treefactory.light.core.tree.TreePlacementValidator;
import com.treefactory.light.core.tree.UndoPlanner;
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
import net.minecraft.world.level.EmptyBlockGetter;
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
import java.util.function.BiConsumer;

/**
 * Live tree preview. Each player has at most one preview tree placed in the world; moving or editing it
 * restores the previous footprint, and only an explicit commit makes it permanent.
 * Only pure generation runs in commonPool. Every world read/write happens on the server thread.
 */
public final class WorldBatchPaster {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // 2 | 16
    private static final int MAX_BUILDS = 4;
    private static final long THROTTLE_NS = 150_000_000L;
    private static final long PERSIST_THROTTLE_NS = 2_000_000_000L; // batches frequent preview-footprint writes
    /** Farthest distance, in blocks, at which a preview tree may be placed; the client ray uses the same limit. */
    public static final int PLACE_RANGE = 512;
    private record CommittedTree(String dimension, Map<BlockPos,BlockState> placed, Map<BlockPos,BlockState> original) {}
    private record PreviewSnapshot(UUID player, String dimension, boolean closing,
                                    Map<BlockPos,BlockState> placed, Map<BlockPos,BlockState> original) {}
    private static final class Session {
        final ServerLevel level;
        final Map<BlockPos,BlockState> original = new HashMap<>();
        final Map<BlockPos,BlockState> placed = new HashMap<>();
        final Set<BlockPos> pendingRestore = new HashSet<>();
        boolean closing;
        Session(ServerLevel level) { this.level=level; }
    }
    private record Request(BlockPos origin, TreeConfig config, long revision) {}
    private record DiskSnapshot(List<PreviewSnapshot> previews, Map<UUID,List<CommittedTree>> undo, Path target) {}
    private final Map<UUID,AtomicBoolean> pending = new HashMap<>();
    private final Map<UUID,Session> sessions = new HashMap<>();
    private final PreviewRevision revisions = new PreviewRevision();
    private final Map<UUID,Request> queued = new HashMap<>();
    private final Map<UUID,Long> lastPreview = new HashMap<>();
    private final Map<UUID,ArrayDeque<CommittedTree>> undo = new HashMap<>();
    // Single background thread so writes to the save file are always serialized, never concurrent.
    private final ExecutorService persistExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "treefactory-light-persist"); t.setDaemon(true); return t;
    });
    private final CoalescedPersistQueue persistQueue = new CoalescedPersistQueue();
    private MinecraftServer server;
    private boolean dirty;
    private long lastPersistNs;
    private BiConsumer<ServerPlayer, Boolean> commitNotifier = (player, success) -> {};

    public void setCommitNotifier(BiConsumer<ServerPlayer, Boolean> notifier) {
        this.commitNotifier = notifier != null ? notifier : (player, success) -> {};
    }

    private void notifyCommitResult(ServerPlayer player, boolean success) {
        commitNotifier.accept(player, success);
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
    /** Places (or moves/rebuilds) the player's preview tree. A newer request supersedes any unfinished one. */
    public void preview(ServerPlayer player, BlockPos origin, TreeConfig config) {
        if (!authorized(player)) return;
        UUID id = player.getUUID();
        cancelInFlight(id);
        long revision = revisions.next(id);
        queued.put(id, new Request(origin, config, revision));
        startQueued();
    }

    /** Stops an in-flight generation so a newer preview request can start immediately. */
    private void cancelInFlight(UUID id) {
        AtomicBoolean cancel = pending.get(id);
        if (cancel != null) cancel.set(true);
    }
    private void startQueued() {
        if (server==null) return;
        for (UUID id : new ArrayList<>(queued.keySet())) {
            if (pending.containsKey(id)) continue;
            if (pending.size()>=MAX_BUILDS) return;
            Long last=lastPreview.get(id);
            // A request pending commit must not be stuck behind the throttle: the player
            // already closed the UI and nothing else will ever flush it.
            if (revisions.commitTarget(id)==null && last!=null && System.nanoTime()-last<THROTTLE_NS) continue;
            Request request=queued.remove(id);
            if (request==null) continue;
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if (player==null || !authorized(player)) { revisions.clearCommitTarget(id); continue; }
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
            rejectPreview(player, revision, "treefactory.target_too_far"); return;
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
                if (revisions.isStale(revision, id)) { startQueued(); tryFinishCommit(current); return; }
                boolean applied=false;
                if (error!=null) {
                    TreefactoryLight.LOGGER.warn("Tree generation failed",error);
                    message(current,"treefactory.failed");
                } else {
                    var validation=validatePlacement(current, level, origin, result.blocks(), sessions.get(id));
                    if (!validation.ok()) message(current, validation.messageKey());
                    else {
                        Map<BlockPos,BlockState> blocks = new LinkedHashMap<>();
                        validation.placeable().forEach((cell, material) -> blocks.put(origin.offset(cell.x(),cell.y(),cell.z()),
                                orient(materials.get(material), result.axes().get(cell))));
                        apply(current, level, blocks);
                        revisions.markApplied(id, revision);
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

    /** Validation failed or generation superseded: never commit a stale preview. */
    private void rejectPreview(ServerPlayer player, long revision, String key) {
        message(player, key);
        if (Objects.equals(revisions.commitTarget(player.getUUID()), revision)) {
            revisions.clearCommitTarget(player.getUUID());
            message(player, "treefactory.commit_failed");
            notifyCommitResult(player, false);
        }
    }
    private void tryFinishCommit(ServerPlayer player) { tryFinishCommit(player, true); }
    private void tryFinishCommit(ServerPlayer player, boolean latestApplied) {
        UUID id=player.getUUID();
        if (pending.containsKey(id) || queued.containsKey(id)) return;
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
    public void commit(ServerPlayer player) {
        UUID id=player.getUUID();
        if (pending.containsKey(id) || queued.containsKey(id)) {
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
        ArrayDeque<CommittedTree> stack=undo.computeIfAbsent(id, k -> new ArrayDeque<>());
        stack.addLast(new CommittedTree(session.level.dimension().identifier().toString(),
                Map.copyOf(session.placed), Map.copyOf(session.original)));
        while (stack.size()>PlacementHistory.LIMIT) stack.removeFirst();
        revisions.clear(id);
        persist(true);
        player.sendSystemMessage(Component.translatable("treefactory.built",session.placed.size()),false);
        notifyCommitResult(player, true);
    }
    public void undo(ServerPlayer player) {
        if (!authorized(player)) return;
        UUID id=player.getUUID();
        ArrayDeque<CommittedTree> stack=undo.get(id);
        if (stack==null || stack.isEmpty()) { message(player,"treefactory.nothing_to_undo"); return; }
        CommittedTree tree=stack.peekLast();
        ServerLevel level=level(tree.dimension());
        if (level==null) { message(player,"treefactory.undo_dimension"); return; }
        Map<BlockPos,BlockState> restore=UndoPlanner.planRestorations(tree.placed(), tree.original(),
                pos -> chunkReady(level, pos) ? level.getBlockState(pos) : null,
                pos -> chunkReady(level, pos));
        if (restore==null) { message(player,"treefactory.undo_unloaded"); return; }
        if (restore.isEmpty()) {
            stack.removeLast();
            persist(true);
            message(player,"treefactory.undo_nothing_left");
            return;
        }
        int restored=0, failed=0;
        for (var cell : restore.entrySet()) {
            BlockPos pos=cell.getKey();
            if (!level.getBlockState(pos).equals(tree.placed().get(pos))) continue;
            if (level.setBlock(pos, cell.getValue(), FLAGS)) restored++;
            else failed++;
        }
        if (failed>0) {
            message(player, restored>0 ? "treefactory.undo_partial_failed" : "treefactory.undo_failed");
            dirty=true;
            persist(true);
            return;
        }
        stack.removeLast();
        persist(true);
        player.sendSystemMessage(Component.translatable("treefactory.undone",restored),false);
    }
    /** Removes the preview tree and restores the world. */
    public void discard(UUID id) {
        AtomicBoolean flag=pending.remove(id); if(flag!=null) flag.set(true);
        revisions.clearCommitTarget(id);
        queued.remove(id);
        Session session=sessions.get(id);
        if (session==null) return;
        session.closing=true;
        restoreAll(session);
        if (session.placed.isEmpty()) sessions.remove(id);
        dirty=true;
    }
    public void disconnect(UUID id) { discard(id); persist(true); }
    public void attach(MinecraftServer server) {
        this.server=server;
        load();
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            sessions.get(id).closing=true;
            restoreAll(sessions.get(id));
            if (sessions.get(id)!=null && sessions.get(id).placed.isEmpty()) sessions.remove(id);
        }
        persist(true);
    }
    public void stop() {
        pending.values().forEach(f -> f.set(true)); pending.clear(); revisions.clearCommitTargets(); queued.clear();
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Session session=sessions.get(id);
            if (session==null) continue;
            session.closing=true;
            restoreAll(session);
            if (session.placed.isEmpty()) sessions.remove(id);
        }
        // Last chance to write before the process exits: block until the save actually lands on disk.
        Future<?> finalWrite=persist(true);
        if (finalWrite!=null) {
            try { finalWrite.get(10, TimeUnit.SECONDS); }
            catch (Exception e) { TreefactoryLight.LOGGER.warn("Timed out saving preview state on shutdown", e); }
        }
        sessions.clear(); undo.clear(); server=null;
    }
    public void tick() {
        startQueued();
        if (dirty) persist(false);
    }
    public void chunkLoaded(ServerLevel level, ChunkPos chunk) {
        boolean changed=false;
        for (Session session : sessions.values()) {
            if (session.level!=level) continue;
            for (BlockPos pos : new ArrayList<>(session.pendingRestore))
                if (chunk.x()==SectionPos.blockToSectionCoord(pos.getX()) && chunk.z()==SectionPos.blockToSectionCoord(pos.getZ())
                        && restore(session, pos)) changed=true;
        }
        changed|=sessions.entrySet().removeIf(e -> e.getValue().closing && e.getValue().placed.isEmpty());
        if (changed) dirty=true;
    }
    static TreePlacementValidator.Result validatePlacement(ServerPlayer player, ServerLevel level, BlockPos origin,
            Map<Cell,Voxelizer.Material> blocks, Session session) {
        return TreePlacementValidator.validate(blocks,
                cell -> inaccessibleReason(level, origin.offset(cell.x(), cell.y(), cell.z())),
                cell -> level.mayInteract(player, origin.offset(cell.x(), cell.y(), cell.z())),
                cell -> {
                    BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
                    if (session != null && session.placed.containsKey(pos)) return true;
                    return replaceable(level.getBlockState(pos));
                });
    }
    private void apply(ServerPlayer player,ServerLevel level,Map<BlockPos,BlockState> blocks) {
        UUID id=player.getUUID();
        Session session=sessions.get(id);
        if (session!=null && session.level!=level) { session.closing=true; restoreAll(session); session=null; }
        if (session==null) { session=new Session(level); sessions.put(id,session); }
        session.closing=false;
        Iterator<Map.Entry<BlockPos,BlockState>> tracked=session.placed.entrySet().iterator();
        while (tracked.hasNext()) {
            var entry=tracked.next();
            if (chunkReady(level, entry.getKey()) && !level.getBlockState(entry.getKey()).equals(entry.getValue())) {
                session.original.remove(entry.getKey()); tracked.remove();
            }
        }
        Map<BlockPos,BlockState> next=new HashMap<>(blocks);
        for (BlockPos pos:new ArrayList<>(session.placed.keySet())) if (!next.containsKey(pos)) restore(session,pos);
        for (var entry:next.entrySet()) {
            BlockPos pos=entry.getKey();
            session.original.computeIfAbsent(pos,level::getBlockState);
            if (!level.getBlockState(pos).equals(entry.getValue())) level.setBlock(pos,entry.getValue(),FLAGS);
        }
        session.placed.clear(); session.placed.putAll(next);
        dirty=true;
    }
    private static boolean restore(Session session,BlockPos pos) {
        if (!chunkReady(session.level, pos)) { session.pendingRestore.add(pos); return false; }
        BlockState expected=session.placed.get(pos), before=session.original.get(pos);
        if (expected!=null && before!=null && session.level.getBlockState(pos).equals(expected))
            session.level.setBlock(pos,before,FLAGS);
        session.placed.remove(pos); session.original.remove(pos); session.pendingRestore.remove(pos);
        return true;
    }
    private static void restoreAll(Session session) {
        for (BlockPos pos:new ArrayList<>(session.placed.keySet())) restore(session,pos);
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
    private static boolean replaceable(BlockState state) {
        return !state.hasBlockEntity() && (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES)
                || (state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty() && state.getFluidState().isEmpty()));
    }
    private static boolean authorized(ServerPlayer p) {
        if (!p.isCreative() || !holdingWand(p)) { message(p,"treefactory.creative_only"); return false; }
        return true;
    }
    private static void message(ServerPlayer p,String key) { p.sendOverlayMessage(Component.translatable(key)); }
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
    private Future<?> persist(boolean immediate) {
        if (server==null) return null;
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
        List<PreviewSnapshot> previews=new ArrayList<>();
        sessions.forEach((id,session) -> previews.add(new PreviewSnapshot(id,
                session.level.dimension().identifier().toString(), session.closing,
                Map.copyOf(session.placed), Map.copyOf(session.original))));
        Map<UUID,List<CommittedTree>> undoSnapshot=new LinkedHashMap<>();
        undo.forEach((id,stack) -> undoSnapshot.put(id, List.copyOf(stack)));
        return new DiskSnapshot(previews, undoSnapshot, dataFile());
    }
    private static void writeToDisk(List<PreviewSnapshot> previews, Map<UUID,List<CommittedTree>> undoSnapshot, Path target) throws IOException {
        CompoundTag root=new CompoundTag();
        ListTag previewsTag=new ListTag();
        for (PreviewSnapshot snapshot : previews) {
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
    private void load() {
        if (server==null) return;
        Path path=dataFile();
        if (!Files.isRegularFile(path)) return;
        CompoundTag root;
        try { root=NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()); }
        catch (IOException e) { TreefactoryLight.LOGGER.warn("Could not load preview state", e); return; }
        sessions.clear(); undo.clear();
        var lookup=server.registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag previewsIn=list(root, "previews");
        for (Tag raw : previewsIn) {
            if (!(raw instanceof CompoundTag tag)) continue;
            UUID id=UUID.fromString(string(tag, "player"));
            ServerLevel world=level(string(tag, "dimension"));
            if (world==null) continue;
            Session session=new Session(world);
            session.closing=tag.getBooleanOr("closing", false);
            readFootprint(lookup, list(tag, "blocks"), session);
            sessions.put(id, session);
        }
        ListTag historyIn=list(root, "undo");
        for (Tag raw : historyIn) {
            if (!(raw instanceof CompoundTag tag)) continue;
            UUID id=UUID.fromString(string(tag, "player"));
            ArrayDeque<CommittedTree> stack=undo.computeIfAbsent(id, k -> new ArrayDeque<>());
            ListTag entries=list(tag, "entries");
            for (Tag itemRaw : entries) {
                if (!(itemRaw instanceof CompoundTag item)) continue;
                stack.addLast(new CommittedTree(string(item, "dimension"),
                        readStates(lookup, list(item, "placed")), readStates(lookup, list(item, "original"))));
                while (stack.size()>PlacementHistory.LIMIT) stack.removeFirst();
            }
        }
    }
    private ServerLevel level(String id) {
        Identifier identifier=Identifier.parse(id);
        for (ServerLevel level : server.getAllLevels())
            if (level.dimension().identifier().equals(identifier)) return level;
        return null;
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
            if (!(raw instanceof CompoundTag tag)) continue;
            BlockPos pos=new BlockPos(integer(tag,"x"), integer(tag,"y"), integer(tag,"z"));
            session.placed.put(pos, NbtUtils.readBlockState(lookup, compound(tag, "placed")));
            if (tag.contains("original")) session.original.put(pos, NbtUtils.readBlockState(lookup, compound(tag, "original")));
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
            if (!(raw instanceof CompoundTag tag)) continue;
            result.put(new BlockPos(integer(tag,"x"), integer(tag,"y"), integer(tag,"z")),
                    NbtUtils.readBlockState(lookup, compound(tag, "state")));
        }
        return result;
    }
    private static String string(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }
    private static int integer(CompoundTag tag, String key) {
        return tag.getIntOr(key, 0);
    }
    private static ListTag list(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key);
    }
    private static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }
}
