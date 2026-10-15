package com.treefactory.light.client;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeBuildTask;
import com.treefactory.light.network.*;
import com.treefactory.light.util.WorldBatchPaster;
import com.treefactory.light.client.gui.DrawerScreen;
import com.treefactory.light.client.gui.render.ThumbnailFBOMaker;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientTreeState {
    public static final ClientTreeState INSTANCE=new ClientTreeState();
    public final PresetStore store=new PresetStore(FabricLoader.getInstance().getConfigDir().resolve("treefactory_light/presets"));
    private TreeConfig config=TreeConfig.defaults();
    private PreviewSnapshot preview;
    private final AtomicLong revision=new AtomicLong();
    private long changedAt=System.nanoTime();
    private boolean dirty=true, generating;
    private BlockPos target;
    private boolean placeDirty, active;
    private long placeChangedAt;
    private static final List<PresetStore.Preset> BUILT_IN_PRESETS=Arrays.stream(TreePreset.values())
            .map(type->new PresetStore.Preset("builtin-"+type.id(),type.id(),"Treefactory Light",
                    TreeConfig.defaults().withPreset(type)))
            .toList();
    private List<PresetStore.Preset> presets=BUILT_IN_PRESETS;
    private String status="";
    public TreeConfig config() { return config; }
    public PreviewSnapshot preview() { return preview!=null && preview.config().equals(config)?preview:null; }
    public List<PresetStore.Preset> presets() { return presets; }
    public String status() { return status; }
    public boolean update(TreeConfig next) {
        if(config.equals(next)) return true;
        // Imported presets may reference missing blocks or blocks outside the allowed palette.
        try { WorldBatchPaster.palette(next); }
        catch(IllegalArgumentException e) { toast("treefactory.invalid_palette"); return false; }
        config=next; revision.incrementAndGet(); dirty=true; changedAt=System.nanoTime();
        placeDirty=true;
        return true;
    }
    public BlockPos target() { return target; }
    public boolean holdingWand() {
        var p=Minecraft.getInstance().player;
        return p!=null && p.getMainHandItem().is(TreefactoryLight.TREE_WAND);
    }
    /** Placement point for a ray that hit the world; only upward faces of ground blocks are valid. */
    private BlockPos origin(HitResult hit) {
        if(!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK
                || block.getDirection()!=Direction.UP || Minecraft.getInstance().level==null) return null;
        BlockPos clicked=block.getBlockPos();
        return Minecraft.getInstance().level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.above();
    }
    /** Places the tree where the cursor points; guiX/guiY are in scaled GUI coordinates. */
    public void placeAtCursor(double guiX,double guiY) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return;
        double ndcX=guiX/mc.getWindow().getGuiScaledWidth()*2-1, ndcY=1-guiY/mc.getWindow().getGuiScaledHeight()*2;
        double tan=Math.tan(Math.toRadians(mc.options.fov().get())/2);
        double aspect=(double)mc.getWindow().getWidth()/mc.getWindow().getHeight();
        Vec3 forward=mc.player.getViewVector(1f), right=forward.cross(new Vec3(0,1,0)).normalize();
        Vec3 up=right.cross(forward).normalize();
        Vec3 dir=forward.add(right.scale(ndcX*aspect*tan)).add(up.scale(ndcY*tan)).normalize();
        Vec3 eye=mc.player.getEyePosition();
        var hit=mc.level.clip(new ClipContext(eye,eye.add(dir.scale(256)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        BlockPos pos=origin(hit);
        if(pos==null) { toast("treefactory.aim_first"); return; }
        setTarget(pos);
    }
    private void setTarget(BlockPos pos) {
        target=pos; active=true; placeDirty=true; placeChangedAt=0;
    }
    public void open() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui.screen() instanceof DrawerScreen) return;
        target=null; active=false; placeDirty=false;
        BlockPos aimed=origin(mc.hitResult);
        mc.gui.setScreen(new DrawerScreen()); refreshLibrary();
        if(aimed!=null) setTarget(aimed);
    }
    public void tick() {
        Minecraft mc=Minecraft.getInstance();
        if(active && mc.level!=null && !(mc.gui.screen() instanceof DrawerScreen)) cancel();
        if(mc.level==null || (!holdingWand() && !(mc.gui.screen() instanceof DrawerScreen))) return;
        if(placeDirty && target!=null && System.nanoTime()-placeChangedAt>=80_000_000L) sendPreview();
        if(!dirty || generating || System.nanoTime()-changedAt<50_000_000L) return;
        generating=true; dirty=false;
        long token=revision.get(); TreeConfig snapshot=config; status=Component.translatable("treefactory.generating").getString();
        CompletableFuture.supplyAsync(()->PreviewSnapshot.create(snapshot,new TreeBuildTask().build(snapshot,()->revision.get()!=token)),ForkJoinPool.commonPool())
                .whenComplete((result,error)->mc.execute(()-> {
                    generating=false;
                    if(revision.get()!=token) return;
                    if(error!=null) { status=Component.translatable("treefactory.failed").getString(); TreefactoryLight.LOGGER.warn("Preview failed",error); }
                    else { preview=result; status=Component.translatable("treefactory.preview_count",
                            result.tree().blocks().size(),result.blockWidth(),result.blockHeight(),result.blockDepth()).getString(); }
                }));
    }
    private void sendPreview() {
        placeDirty=false; placeChangedAt=System.nanoTime();
        if(!ClientPlayNetworking.canSend(C2SPreviewTreePacket.TYPE)) { toast("treefactory.no_server"); return; }
        ClientPlayNetworking.send(new C2SPreviewTreePacket(target,config));
    }
    /** Makes the previewed tree permanent. Returns false when nothing has been placed yet. */
    public boolean finish() {
        if(!active || target==null) { toast("treefactory.aim_first"); return false; }
        if(placeDirty) sendPreview();
        if(ClientPlayNetworking.canSend(C2SFinishTreePacket.TYPE)) ClientPlayNetworking.send(new C2SFinishTreePacket(true));
        active=false; target=null; return true;
    }
    /** Removes the unfinished preview tree from the world. */
    public void cancel() {
        if(active && ClientPlayNetworking.canSend(C2SFinishTreePacket.TYPE)) ClientPlayNetworking.send(new C2SFinishTreePacket(false));
        active=false; target=null; placeDirty=false;
    }
    public void undo() {
        if(!ClientPlayNetworking.canSend(C2SUndoTreePacket.TYPE)) { toast("treefactory.no_server"); return; }
        ClientPlayNetworking.send(new C2SUndoTreePacket());
    }
    public void cycleScale(int direction) {
        update(config.withScale(config.scale().cycle(direction)));
        var player=Minecraft.getInstance().player;
        if(player!=null) player.sendOverlayMessage(Component.translatable("treefactory.size",String.format("%.1f",config.height())));
    }
    public void refreshLibrary() {
        CompletableFuture.supplyAsync(()-> {
            try { return store.loadAll(warning->TreefactoryLight.LOGGER.warn("Invalid preset: {}",warning)); }
            catch(Exception e) { throw new CompletionException(e); }
        }).whenComplete((list,error)->Minecraft.getInstance().execute(()-> {
            if(error==null) {
                List<PresetStore.Preset> combined=new ArrayList<>(BUILT_IN_PRESETS);
                combined.addAll(list); presets=List.copyOf(combined);
            }
            else { status=Component.translatable("treefactory.load_failed").getString(); TreefactoryLight.LOGGER.warn("Preset loading failed",error); }
            if(Minecraft.getInstance().gui.screen() instanceof DrawerScreen drawer) drawer.refresh();
        }));
    }
    public void save(String name) {
        PreviewSnapshot snapshot=preview();
        if(snapshot==null) { toast("treefactory.wait_preview"); return; }
        String author=Minecraft.getInstance().player==null ? "Player" : Minecraft.getInstance().player.getName().getString();
        CompletableFuture.supplyAsync(()-> {
            try { return store.save(name,author,snapshot.config()); }
            catch(Exception e) { throw new CompletionException(e); }
        }).whenComplete((preset,error)->Minecraft.getInstance().execute(()-> {
            if(error!=null) { toast("treefactory.save_failed"); TreefactoryLight.LOGGER.warn("Preset save failed",error); return; }
            ThumbnailFBOMaker.enqueue(preset,snapshot,store.thumbnail(preset.id()));
            toast("treefactory.saved"); refreshLibrary();
        }));
    }
    public void delete(PresetStore.Preset preset) {
        if(preset.id().startsWith("builtin-")) return;
        CompletableFuture.runAsync(()-> {
            try { store.delete(preset.id()); }
            catch(Exception e) { throw new CompletionException(e); }
        }).whenComplete((unused,error)->Minecraft.getInstance().execute(()-> {
            if(error!=null) { toast("treefactory.delete_failed"); TreefactoryLight.LOGGER.warn("Preset delete failed",error); }
            else toast("treefactory.deleted");
            refreshLibrary();
        }));
    }
    public void disconnect() {
        revision.incrementAndGet(); preview=null;         dirty=true; target=null; active=false; placeDirty=false; presets=BUILT_IN_PRESETS;
        ThumbnailFBOMaker.clear();
        sessionEpoch++;
    }
    public static volatile int sessionEpoch;
    public static void toast(String key) {
        if(Minecraft.getInstance().player!=null) Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(key));
    }
}
