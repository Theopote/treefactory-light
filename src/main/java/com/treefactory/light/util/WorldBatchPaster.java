package com.treefactory.light.util;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.tree.PlacementHistory;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.TreeConfig;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final long THROTTLE_NS = 500_000_000L;
    private static final int PLACE_RANGE = 128;
    private record CommittedTree(String dimension, Map<BlockPos,BlockState> placed, Map<BlockPos,BlockState> original) {}
    private static final class Session {
        final ServerLevel level;
        final Map<BlockPos,BlockState> original = new HashMap<>();
        final Map<BlockPos,BlockState> placed = new HashMap<>();
        final Set<BlockPos> pendingRestore = new HashSet<>();
        boolean closing;
        Session(ServerLevel level) { this.level=level; }
    }
    private record Request(BlockPos origin, TreeConfig config) {}
    private final Map<UUID,AtomicBoolean> pending = new HashMap<>();
    private final Map<UUID,Session> sessions = new HashMap<>();
    private final Set<UUID> commitWhenDone = new HashSet<>();
    private final Map<UUID,Request> queued = new HashMap<>();
    private final Map<UUID,Long> lastPreview = new HashMap<>();
    private final Map<UUID,ArrayDeque<CommittedTree>> undo = new HashMap<>();
    private MinecraftServer server;
    private boolean dirty;
    public static boolean holdingWand(ServerPlayer p) {
        return p.getMainHandItem().is(TreefactoryLight.TREE_WAND);
    }
    public static BlockState palette(String id, boolean leaves) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalArgumentException("Unknown block: "+id));
        BlockState state = block.defaultBlockState();
        if (!state.is(leaves ? BlockTags.LEAVES : BlockTags.LOGS)) throw new IllegalArgumentException("Choose a "+(leaves?"leaves":"log")+" block");
        if (leaves && state.hasProperty(BlockStateProperties.PERSISTENT)) state = state.setValue(BlockStateProperties.PERSISTENT,true);
        return state;
    }
    public static EnumMap<Voxelizer.Material,BlockState> palette(TreeConfig config) {
        var p=config.blockPalette();
        EnumMap<Voxelizer.Material,BlockState> result=new EnumMap<>(Voxelizer.Material.class);
        result.put(Voxelizer.Material.MAIN_TRUNK,palette(p.mainTrunk(),false));
        result.put(Voxelizer.Material.THICK_BRANCH,palette(p.thickBranch(),false));
        result.put(Voxelizer.Material.THIN_BRANCH,wood(p.thinBranch()));
        result.put(Voxelizer.Material.PRIMARY_LEAF,palette(p.primaryLeaf(),true));
        result.put(Voxelizer.Material.SECONDARY_LEAF,palette(p.secondaryLeaf(),true));
        result.put(Voxelizer.Material.ACCENT,accent(p.accentBlock()));
        return result;
    }
    public static BlockState orient(BlockState state,Voxelizer.Axis axis) {
        if(axis==null || !state.hasProperty(BlockStateProperties.AXIS)) return state;
        Direction.Axis direction=switch(axis) {
            case X -> Direction.Axis.X; case Y -> Direction.Axis.Y; case Z -> Direction.Axis.Z;
        };
        return state.setValue(BlockStateProperties.AXIS,direction);
    }
    private static BlockState wood(String id) {
        BlockState state=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id))
                .orElseThrow(()->new IllegalArgumentException("Unknown block: "+id)).defaultBlockState();
        if(!state.is(BlockTags.LOGS) && !state.is(BlockTags.PLANKS) && !state.is(BlockTags.FENCES))
            throw new IllegalArgumentException("Choose a log, planks, or a fence");
        return state;
    }
    private static BlockState accent(String id) {
        BlockState state=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id))
                .orElseThrow(()->new IllegalArgumentException("Unknown block: "+id)).defaultBlockState();
        if(state.getBlock()!=Blocks.MOSS_BLOCK && state.getBlock()!=Blocks.OCHRE_FROGLIGHT
                && state.getBlock()!=Blocks.PEARLESCENT_FROGLIGHT && state.getBlock()!=Blocks.VERDANT_FROGLIGHT)
            throw new IllegalArgumentException("Choose moss or froglight for accent");
        return state;
    }
    /** Places (or moves/rebuilds) the player's preview tree. A newer request supersedes any unfinished one. */
    public void preview(ServerPlayer player, BlockPos origin, TreeConfig config) {
        if (!authorized(player)) return;
        UUID id = player.getUUID();
        queued.put(id, new Request(origin, config));
        startQueued();
    }
    private void startQueued() {
        if (server==null) return;
        for (UUID id : new ArrayList<>(queued.keySet())) {
            if (pending.containsKey(id)) continue;
            if (pending.size()>=MAX_BUILDS) return;
            Long last=lastPreview.get(id);
            if (last!=null && System.nanoTime()-last<THROTTLE_NS) continue;
            Request request=queued.remove(id);
            if (request==null) continue;
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if (player==null || !authorized(player)) continue;
            beginPreview(player, request.origin(), request.config());
        }
    }
    private void beginPreview(ServerPlayer player, BlockPos origin, TreeConfig config) {
        UUID id = player.getUUID();
        ServerLevel level = player.level();
        if(player.distanceToSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5)>PLACE_RANGE*PLACE_RANGE) {
            message(player,"treefactory.target_too_far"); return;
        }
        String originProblem=inaccessibleReason(level,origin);
        if(originProblem!=null) { message(player,originProblem); return; }
        if(!level.mayInteract(player,origin)) { message(player,"treefactory.target_protected"); return; }
        final EnumMap<Voxelizer.Material,BlockState> materials;
        try { materials=palette(config); }
        catch (IllegalArgumentException e) { message(player,"treefactory.invalid_palette"); return; }
        AtomicBoolean cancel = new AtomicBoolean();
        pending.put(id,cancel);
        lastPreview.put(id, System.nanoTime());
        var running = level.getServer();
        CompletableFuture.supplyAsync(() -> {
            var result = new com.treefactory.light.core.tree.TreeBuildTask().build(config,cancel::get);
            Map<BlockPos,BlockState> blocks = new LinkedHashMap<>();
            result.blocks().forEach((p,m) -> blocks.put(origin.offset(p.x(),p.y(),p.z()),
                    orient(materials.get(m),result.axes().get(p))));
            return blocks;
        },ForkJoinPool.commonPool()).whenComplete((blocks,error) -> {
            if (cancel.get()) return;
            running.execute(() -> {
                if (pending.get(id)!=cancel) return;
                pending.remove(id);
                ServerPlayer current = running.getPlayerList().getPlayer(id);
                if (current==null || current.level()!=level || !authorized(current)) { discard(id); startQueued(); return; }
                if (error!=null) { TreefactoryLight.LOGGER.warn("Tree generation failed",error); message(current,"treefactory.failed"); }
                else apply(current,level,blocks);
                if (commitWhenDone.remove(id) && error==null) commit(current);
                startQueued();
            });
        });
    }
    /** Keeps the current preview tree permanently (after any in-flight rebuild finishes). */
    public void commit(ServerPlayer player) {
        UUID id=player.getUUID();
        if (pending.containsKey(id)) { commitWhenDone.add(id); return; }
        Session session=sessions.remove(id);
        if (session==null) return;
        ArrayDeque<CommittedTree> stack=undo.computeIfAbsent(id, k -> new ArrayDeque<>());
        stack.addLast(new CommittedTree(session.level.dimension().identifier().toString(),
                Map.copyOf(session.placed), Map.copyOf(session.original)));
        while (stack.size()>PlacementHistory.LIMIT) stack.removeFirst();
        dirty=true; persist();
        player.sendSystemMessage(Component.translatable("treefactory.built",session.placed.size()),false);
    }
    public void undo(ServerPlayer player) {
        if (!authorized(player)) return;
        UUID id=player.getUUID();
        ArrayDeque<CommittedTree> stack=undo.get(id);
        if (stack==null || stack.isEmpty()) { message(player,"treefactory.nothing_to_undo"); return; }
        CommittedTree tree=stack.removeLast();
        ServerLevel level=level(tree.dimension());
        if (level==null) { message(player,"treefactory.nothing_to_undo"); return; }
        var entry=new PlacementHistory.Entry<>(tree.placed(), tree.original());
        Map<BlockPos,BlockState> restore=entry.restorations(pos -> level.hasChunkAt(pos)?level.getBlockState(pos):null);
        int count=0;
        for (var cell : restore.entrySet()) {
            BlockPos pos=cell.getKey();
            if (!level.getBlockState(pos).equals(tree.placed().get(pos))) continue;
            level.setBlock(pos, cell.getValue(), FLAGS);
            count++;
        }
        dirty=true; persist();
        player.sendSystemMessage(Component.translatable("treefactory.undone",count),false);
    }
    /** Removes the preview tree and restores the world. */
    public void discard(UUID id) {
        AtomicBoolean flag=pending.remove(id); if(flag!=null) flag.set(true);
        commitWhenDone.remove(id); queued.remove(id);
        Session session=sessions.get(id);
        if (session==null) return;
        session.closing=true;
        restoreAll(session);
        if (session.placed.isEmpty()) sessions.remove(id);
        dirty=true;
    }
    public void disconnect(UUID id) { discard(id); persist(); }
    public void attach(MinecraftServer server) {
        this.server=server;
        load();
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            sessions.get(id).closing=true;
            restoreAll(sessions.get(id));
            if (sessions.get(id)!=null && sessions.get(id).placed.isEmpty()) sessions.remove(id);
        }
        persist();
    }
    public void stop() {
        pending.values().forEach(f -> f.set(true)); pending.clear(); commitWhenDone.clear(); queued.clear();
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Session session=sessions.get(id);
            if (session==null) continue;
            session.closing=true;
            restoreAll(session);
            if (session.placed.isEmpty()) sessions.remove(id);
        }
        persist();
        sessions.clear(); undo.clear(); server=null;
    }
    public void tick() {
        startQueued();
        if (dirty) persist();
    }
    public void chunkLoaded(ServerLevel level, ChunkPos chunk) {
        boolean changed=false;
        for (Session session : sessions.values()) {
            if (session.level!=level) continue;
            for (BlockPos pos : new ArrayList<>(session.pendingRestore))
                if (chunk.x()==pos.getX()>>4 && chunk.z()==pos.getZ()>>4 && restore(session, pos)) changed=true;
        }
        changed|=sessions.entrySet().removeIf(e -> e.getValue().closing && e.getValue().placed.isEmpty());
        if (changed) dirty=true;
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
            if (level.hasChunkAt(entry.getKey()) && !level.getBlockState(entry.getKey()).equals(entry.getValue())) {
                session.original.remove(entry.getKey()); tracked.remove();
            }
        }
        Map<BlockPos,BlockState> next=new HashMap<>();
        for (var entry:blocks.entrySet()) {
            BlockPos pos=entry.getKey();
            if (inaccessibleReason(level,pos)!=null || !level.mayInteract(player,pos)) continue;
            if (!session.original.containsKey(pos) && !replaceable(level.getBlockState(pos))) continue;
            next.put(pos,entry.getValue());
        }
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
        if (!session.level.hasChunkAt(pos)) { session.pendingRestore.add(pos); return false; }
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
        if(!level.hasChunkAt(pos)) return "treefactory.target_unloaded";
        return null;
    }
    private static boolean replaceable(BlockState state) {
        return !state.hasBlockEntity() && (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES));
    }
    private static boolean authorized(ServerPlayer p) {
        if (!p.isCreative() || !holdingWand(p)) { message(p,"treefactory.creative_only"); return false; }
        return true;
    }
    private static void message(ServerPlayer p,String key) { p.sendOverlayMessage(Component.translatable(key)); }
    private Path dataFile() {
        return server.getWorldPath(LevelResource.ROOT).resolve("treefactory_light.dat");
    }
    private void persist() {
        if (server==null) return;
        dirty=false;
        CompoundTag root=new CompoundTag();
        ListTag previews=new ListTag();
        sessions.forEach((id,session) -> {
            CompoundTag tag=new CompoundTag();
            tag.putString("player", id.toString());
            tag.putString("dimension", session.level.dimension().identifier().toString());
            tag.putBoolean("closing", session.closing);
            tag.put("blocks", footprint(session.placed, session.original));
            previews.add(tag);
        });
        root.put("previews", previews);
        ListTag history=new ListTag();
        undo.forEach((id,stack) -> {
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
        try { NbtIo.writeCompressed(root, dataFile()); }
        catch (IOException e) { TreefactoryLight.LOGGER.warn("Could not save preview state", e); }
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
