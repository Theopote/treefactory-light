package com.treefactory.light.util;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.tree.*;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.TreeConfig;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only pure generation runs in commonPool. Every world read/write happens on the server thread. */
public final class WorldBatchPaster {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // 2 | 16
    private final Map<UUID,AtomicBoolean> pending = new HashMap<>();
    private final Deque<Job> jobs = new ArrayDeque<>();
    private final UndoHistory<Edit> history = new UndoHistory<>();
    private final Map<UUID,Long> nextAction = new HashMap<>();
    private record Change(BlockPos pos, BlockState before, BlockState after) {}
    private record Edit(ResourceKey<Level> dimension, List<Change> changes) {}
    private static final class Job {
        final UUID player; final ServerLevel level; Iterator<Change> iterator; final boolean undo;
        Iterator<Map.Entry<BlockPos,BlockState>> validation;
        final List<Change> planned = new ArrayList<>();
        final List<Change> applied = new ArrayList<>();
        final List<Change> deferred = new ArrayList<>();
        Job(UUID player,ServerLevel level,List<Change> changes,boolean undo) {
            this.player=player; this.level=level; this.iterator=changes.iterator(); this.undo=undo;
        }
        Job(UUID player,ServerLevel level,Map<BlockPos,BlockState> blocks) {
            this(player,level,List.of(),false); this.validation=blocks.entrySet().iterator();
        }
    }
    public static boolean holdingWand(ServerPlayer p) {
        return p.getMainHandItem().is(TreefactoryLight.TREE_WAND) || p.getOffhandItem().is(TreefactoryLight.TREE_WAND);
    }
    public static BlockState palette(String id, boolean leaves) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalArgumentException("Unknown block: "+id));
        BlockState state = block.defaultBlockState();
        if (!state.is(leaves ? BlockTags.LEAVES : BlockTags.LOGS)) throw new IllegalArgumentException("Choose a "+(leaves?"leaves":"log")+" block");
        if (leaves && state.hasProperty(BlockStateProperties.PERSISTENT)) state = state.setValue(BlockStateProperties.PERSISTENT,true);
        return state;
    }
    public void build(ServerPlayer player, BlockPos origin, TreeConfig config) {
        if (!acceptAction(player)) return;
        if (!authorized(player)) return;
        UUID id = player.getUUID();
        if (pending.containsKey(id) || jobs.stream().anyMatch(j -> j.player.equals(id))) { message(player,"treefactory.busy"); return; }
        if (pending.size()+jobs.size()>=4) { message(player,"treefactory.server_busy"); return; }
        ServerLevel level = player.level();
        if (player.distanceToSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5)>64*64
                || !accessible(level,player,origin)) { message(player,"treefactory.invalid_target"); return; }
        final BlockState log, leaves;
        try { log=palette(config.logBlock(),false); leaves=palette(config.leavesBlock(),true); }
        catch (IllegalArgumentException e) { message(player,"treefactory.invalid_palette"); return; }
        AtomicBoolean cancel = new AtomicBoolean(); pending.put(id,cancel);
        MinecraftServer server = level.getServer();
        message(player,"treefactory.generating");
        CompletableFuture.supplyAsync(() -> {
            TreeBuildTask.Result result = new TreeBuildTask().build(config,cancel::get);
            Map<BlockPos,BlockState> blocks = new LinkedHashMap<>();
            result.blocks().forEach((p,m) -> blocks.put(origin.offset(p.x(),p.y(),p.z()),m==Voxelizer.Material.LOG ? log : leaves));
            return blocks;
        },ForkJoinPool.commonPool()).whenComplete((blocks,error) -> {
            if (cancel.get()) return;
            server.execute(() -> {
                if (pending.get(id)!=cancel) return;
                pending.remove(id);
                ServerPlayer current = server.getPlayerList().getPlayer(id);
                if (current==null || current.level()!=level || !authorized(current)) return;
                if (error!=null) { TreefactoryLight.LOGGER.warn("Tree generation failed",error); message(current,"treefactory.failed"); return; }
                jobs.addLast(new Job(id,level,blocks));
            });
        });
    }
    public void undo(ServerPlayer player) {
        if (!acceptAction(player)) return;
        if (!authorized(player)) return;
        UUID id=player.getUUID();
        if (pending.containsKey(id) || jobs.stream().anyMatch(j -> j.player.equals(id))) { message(player,"treefactory.busy"); return; }
        Edit edit=history.peek(id);
        if (edit==null) { message(player,"treefactory.no_undo"); return; }
        if (!edit.dimension.equals(player.level().dimension())) { message(player,"treefactory.wrong_dimension"); return; }
        for (Change c:edit.changes) if (!accessible(player.level(),player,c.pos)) { message(player,"treefactory.invalid_target"); return; }
        history.pop(id);
        List<Change> reversed=new ArrayList<>(edit.changes); Collections.reverse(reversed);
        jobs.addLast(new Job(id,player.level(),reversed,true));
    }
    public void tick(MinecraftServer server) {
        long deadline=System.nanoTime()+4_000_000;
        for (int budget=0; budget<1024 && !jobs.isEmpty() && System.nanoTime()<deadline; budget++) {
            Job job=jobs.removeFirst(); ServerPlayer player=server.getPlayerList().getPlayer(job.player);
            if (player==null || player.level()!=job.level || !player.isCreative()) {
                if (job.undo) job.iterator.forEachRemaining(job.deferred::add);
                finish(job,player); continue;
            }
            // Preflight reads share the tick budget with writes. No world access on worker threads,
            // no synchronous chunk generation, and no writes until the complete footprint passes.
            if (job.validation!=null) {
                if (job.validation.hasNext()) {
                    var entry=job.validation.next(); BlockPos pos=entry.getKey();
                    if (!accessible(job.level,player,pos)) { message(player,"treefactory.invalid_target"); continue; }
                    BlockState old=job.level.getBlockState(pos);
                    if (replaceable(old) && !old.equals(entry.getValue())) job.planned.add(new Change(pos,old,entry.getValue()));
                }
                if (!job.validation.hasNext()) { job.validation=null; job.iterator=job.planned.iterator(); }
                jobs.addLast(job); continue;
            }
            if (job.iterator.hasNext()) {
                Change c=job.iterator.next();
                if (accessible(job.level,player,c.pos)) {
                    BlockState expected=job.undo?c.after:c.before, target=job.undo?c.before:c.after;
                    if (job.level.getBlockState(c.pos).equals(expected) && job.level.getBlockEntity(c.pos)==null
                            && job.level.setBlock(c.pos,target,FLAGS)) job.applied.add(c);
                } else if (job.undo) job.deferred.add(c);
            }
            if (job.iterator.hasNext()) jobs.addLast(job); else finish(job,player);
        }
    }
    private void finish(Job job,ServerPlayer player) {
        if (!job.undo && !job.applied.isEmpty() && player!=null) history.push(job.player,new Edit(job.level.dimension(),List.copyOf(job.applied)));
        if (job.undo && !job.deferred.isEmpty() && player!=null) history.push(job.player,new Edit(job.level.dimension(),List.copyOf(job.deferred)));
        if (player!=null) player.sendSystemMessage(Component.translatable(job.undo?"treefactory.undone":"treefactory.built",job.applied.size()),false);
    }
    public void disconnect(UUID id) {
        AtomicBoolean flag=pending.remove(id); if(flag!=null) flag.set(true);
        jobs.removeIf(j -> j.player.equals(id)); history.remove(id); nextAction.remove(id);
    }
    public void stop() { pending.values().forEach(f -> f.set(true)); pending.clear(); jobs.clear(); history.clear(); nextAction.clear(); }
    private boolean acceptAction(ServerPlayer player) {
        long now=System.nanoTime();
        if(now<nextAction.getOrDefault(player.getUUID(),0L)) return false;
        nextAction.put(player.getUUID(),now+500_000_000L); return true;
    }
    private static boolean accessible(ServerLevel level,ServerPlayer player,BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.hasChunkAt(pos) && level.mayInteract(player,pos);
    }
    private static boolean replaceable(BlockState state) {
        return !state.hasBlockEntity() && (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES));
    }
    private static boolean authorized(ServerPlayer p) {
        if (!p.isCreative() || !holdingWand(p)) { message(p,"treefactory.creative_only"); return false; }
        return true;
    }
    private static void message(ServerPlayer p,String key) { p.sendOverlayMessage(Component.translatable(key)); }
}
