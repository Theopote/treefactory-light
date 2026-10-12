package com.treefactory.light.util;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.tree.*;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.TreeConfig;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    private static final class Session {
        final ServerLevel level;
        final Map<BlockPos,BlockState> original = new HashMap<>();
        final Map<BlockPos,BlockState> placed = new HashMap<>();
        Session(ServerLevel level) { this.level=level; }
    }
    private final Map<UUID,AtomicBoolean> pending = new HashMap<>();
    private final Map<UUID,Session> sessions = new HashMap<>();
    private final Set<UUID> commitWhenDone = new HashSet<>();
    private final Map<UUID,Runnable> queued = new HashMap<>();
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
        result.put(Voxelizer.Material.THIN_BRANCH,choice(p.thinBranch(),BlockTags.PLANKS));
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
    private static BlockState choice(String id,net.minecraft.tags.TagKey<Block> tag) {
        BlockState state=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id))
                .orElseThrow(()->new IllegalArgumentException("Unknown block: "+id)).defaultBlockState();
        if(!state.is(tag) && !state.is(BlockTags.FENCES)) throw new IllegalArgumentException("Choose planks or a fence");
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
        // Let the in-flight generation finish so continuous slider drags still show intermediate trees.
        if (pending.containsKey(id)) { queued.put(id,() -> preview(player,origin,config)); return; }
        ServerLevel level = player.level();
        if(player.distanceToSqr(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5)>264*264) {
            message(player,"treefactory.target_too_far"); return;
        }
        String originProblem=inaccessibleReason(level,player,origin);
        if(originProblem!=null) { message(player,originProblem); return; }
        final EnumMap<Voxelizer.Material,BlockState> materials;
        try { materials=palette(config); }
        catch (IllegalArgumentException e) { message(player,"treefactory.invalid_palette"); return; }
        AtomicBoolean cancel = new AtomicBoolean();
        pending.put(id,cancel);
        var server = level.getServer();
        CompletableFuture.supplyAsync(() -> {
            TreeBuildTask.Result result = new TreeBuildTask().build(config,cancel::get);
            Map<BlockPos,BlockState> blocks = new LinkedHashMap<>();
            result.blocks().forEach((p,m) -> blocks.put(origin.offset(p.x(),p.y(),p.z()),
                    orient(materials.get(m),result.axes().get(p))));
            return blocks;
        },ForkJoinPool.commonPool()).whenComplete((blocks,error) -> {
            if (cancel.get()) return;
            server.execute(() -> {
                if (pending.get(id)!=cancel) return;
                pending.remove(id);
                ServerPlayer current = server.getPlayerList().getPlayer(id);
                if (current==null || current.level()!=level || !authorized(current)) { discard(id); return; }
                if (error!=null) { TreefactoryLight.LOGGER.warn("Tree generation failed",error); message(current,"treefactory.failed"); }
                else apply(current,level,blocks);
                Runnable next = queued.remove(id);
                if (next!=null) next.run();
                else if (commitWhenDone.remove(id)) commit(current);
            });
        });
    }
    /** Keeps the current preview tree permanently (after any in-flight rebuild finishes). */
    public void commit(ServerPlayer player) {
        UUID id=player.getUUID();
        if (pending.containsKey(id)) { commitWhenDone.add(id); return; }
        Session session=sessions.remove(id);
        if (session!=null) player.sendSystemMessage(Component.translatable("treefactory.built",session.placed.size()),false);
    }
    /** Removes the preview tree and restores the world. */
    public void discard(UUID id) {
        AtomicBoolean flag=pending.remove(id); if(flag!=null) flag.set(true);
        commitWhenDone.remove(id); queued.remove(id);
        Session session=sessions.remove(id);
        if (session!=null) restoreAll(session);
    }
    public void disconnect(UUID id) { discard(id); }
    public void stop() {
        pending.values().forEach(f -> f.set(true)); pending.clear(); commitWhenDone.clear(); queued.clear();
        sessions.values().forEach(WorldBatchPaster::restoreAll); sessions.clear();
    }
    private void apply(ServerPlayer player,ServerLevel level,Map<BlockPos,BlockState> blocks) {
        UUID id=player.getUUID();
        Session session=sessions.get(id);
        if (session!=null && session.level!=level) { restoreAll(session); session=null; }
        if (session==null) { session=new Session(level); sessions.put(id,session); }
        // Positions the player changed since the last preview are no longer ours to restore.
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
            if (inaccessibleReason(level,player,pos)!=null) continue;
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
    }
    private static void restore(Session session,BlockPos pos) {
        BlockState expected=session.placed.remove(pos), before=session.original.remove(pos);
        if (expected!=null && before!=null && session.level.hasChunkAt(pos) && session.level.getBlockState(pos).equals(expected))
            session.level.setBlock(pos,before,FLAGS);
    }
    private static void restoreAll(Session session) {
        for (BlockPos pos:new ArrayList<>(session.placed.keySet())) restore(session,pos);
    }
    private static String inaccessibleReason(ServerLevel level,ServerPlayer player,BlockPos pos) {
        if(level.isOutsideBuildHeight(pos)) return "treefactory.target_height";
        if(!level.getWorldBorder().isWithinBounds(pos)) return "treefactory.target_border";
        if(!level.hasChunkAt(pos)) return "treefactory.target_unloaded";
        if(!level.mayInteract(player,pos)) return "treefactory.target_protected";
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
}
