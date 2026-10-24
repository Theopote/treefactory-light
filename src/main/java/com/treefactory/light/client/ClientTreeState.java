package com.treefactory.light.client;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.Cell;
import com.treefactory.light.core.tree.TreeBuildTask;
import com.treefactory.light.network.*;
import com.treefactory.light.util.WorldBatchPaster;
import com.treefactory.light.client.gui.DrawerScreen;
import com.treefactory.light.client.gui.render.ThumbnailFBOMaker;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
    private BlockPos target, previewOrigin;
    private boolean placeDirty, active;
    private long placeChangedAt;
    private static final List<PresetStore.Preset> BUILT_IN_PRESETS=BuiltInPresets.all();
    private List<PresetStore.Preset> presets=BUILT_IN_PRESETS;
    private String status="";
    public enum FinishPhase { NONE, PREPARING, FINISHING }
    private FinishPhase finishPhase=FinishPhase.NONE;
    private Runnable finishCallback;
    public TreeConfig config() { return config; }
    public PreviewSnapshot preview() { return preview!=null && preview.config().equals(config)?preview:null; }
    public List<PresetStore.Preset> presets() { return presets; }
    public String status() { return status; }
    public FinishPhase finishPhase() { return finishPhase; }
    public boolean isGenerating() { return generating; }
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
    /**
     * Placement point for a ray that hit the world. Any block can be the support: the tree starts on top of the
     * clicked block. Non-solid decoration (flowers, torches, ...) is looked through to the support below it.
     */
    private BlockPos origin(HitResult hit) {
        var level=Minecraft.getInstance().level;
        if(!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK || level==null) return null;
        BlockPos clicked=block.getBlockPos();
        if(level.getBlockState(clicked).canBeReplaced()) return clicked;
        BlockPos pos=clicked;
        for(int i=0;i<10 && isDecoration(level,pos);i++) pos=pos.below();
        return isDecoration(level,pos) ? clicked.above() : pos.above();
    }
    private static boolean isDecoration(net.minecraft.world.level.BlockGetter level,BlockPos pos) {
        var state=level.getBlockState(pos);
        return !state.hasBlockEntity() && state.getCollisionShape(level,pos).isEmpty() && state.getFluidState().isEmpty();
    }
    /** World-space direction of the ray through a GUI point, from the camera's real projection (includes dynamic FOV). */
    private static Vec3 cursorRay(Minecraft mc,double guiX,double guiY) {
        double ndcX=guiX/mc.getWindow().getGuiScaledWidth()*2-1, ndcY=1-guiY/mc.getWindow().getGuiScaledHeight()*2;
        var camera=mc.gameRenderer.mainCamera();
        var inverse=camera.getViewRotationProjectionMatrix(new org.joml.Matrix4f()).invert();
        var near=new org.joml.Vector4f((float)ndcX,(float)ndcY,-1f,1f).mul(inverse);
        var far=new org.joml.Vector4f((float)ndcX,(float)ndcY,1f,1f).mul(inverse);
        near.div(near.w); far.div(far.w);
        Vec3 dir=new Vec3(far.x-near.x,far.y-near.y,far.z-near.z).normalize();
        var forward=camera.rotation().transform(new org.joml.Vector3f(0,0,-1));
        // Depth conventions differ between projections; the ray must always point into the screen.
        return dir.x*forward.x+dir.y*forward.y+dir.z*forward.z<0 ? dir.scale(-1) : dir;
    }
    /** Places the tree where the cursor points; guiX/guiY are in scaled GUI coordinates. */
    public void placeAtCursor(double guiX,double guiY) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return;
        Vec3 dir=cursorRay(mc,guiX,guiY), eye=mc.gameRenderer.mainCamera().position();
        BlockPos pos=origin(clipIgnoringPreview(mc,eye,dir));
        if(pos==null) { toast("treefactory.aim_first"); return; }
        setTarget(pos);
    }
    /**
     * The preview tree is made of real blocks, so a plain ray would hit the tree (trunk, canopy) instead of the
     * ground behind it and the new tree would be placed one block above the preview. Preview blocks are skipped.
     */
    private HitResult clipIgnoringPreview(Minecraft mc,Vec3 eye,Vec3 dir) {
        Set<BlockPos> own=new HashSet<>();
        if(previewOrigin!=null && preview!=null)
            for(Cell cell:preview.tree().blocks().keySet()) own.add(previewOrigin.offset(cell.x(),cell.y(),cell.z()));
        Vec3 end=eye.add(dir.scale(WorldBatchPaster.PLACE_RANGE)), start=eye;
        for(int i=0;i<256;i++) {
            var hit=mc.level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.BLOCK || !own.contains(hit.getBlockPos())) return hit;
            BlockPos skipped=hit.getBlockPos();
            start=hit.getLocation();
            // Step out of the skipped block so the next clip cannot hit it again from the inside.
            for(int s=0;s<40 && BlockPos.containing(start).equals(skipped);s++) start=start.add(dir.scale(.1));
            if(BlockPos.containing(start).equals(skipped)) break;
        }
        return mc.level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
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
        if(finishPhase==FinishPhase.PREPARING && !generating && !dirty && !placeDirty) sendFinishRequest();
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
        previewOrigin=target;
        ClientPlayNetworking.send(new C2SPreviewTreePacket(target,config));
    }
    /** Requests permanent placement; {@code onSuccess} runs only after the server confirms. */
    public void requestFinish(Runnable onSuccess) {
        if(finishPhase!=FinishPhase.NONE) return;
        if(!active || target==null) { toast("treefactory.aim_first"); return; }
        if(!ClientPlayNetworking.canSend(C2SFinishTreePacket.TYPE)) { toast("treefactory.no_server"); return; }
        finishCallback=onSuccess;
        if(generating || dirty || placeDirty) {
            finishPhase=FinishPhase.PREPARING;
            if(placeDirty) sendPreview();
            return;
        }
        sendFinishRequest();
    }

    private void sendFinishRequest() {
        finishPhase=FinishPhase.FINISHING;
        ClientPlayNetworking.send(new C2SFinishTreePacket(true));
    }

    public void onCommitResult(boolean success) {
        if(finishPhase==FinishPhase.NONE) return;
        finishPhase=FinishPhase.NONE;
        Runnable callback=finishCallback;
        finishCallback=null;
        if(success) {
            active=false; target=null; placeDirty=false; previewOrigin=null;
            if(callback!=null) callback.run();
        } else toast("treefactory.commit_failed");
    }
    /** Removes the unfinished preview tree from the world. */
    public void cancel() {
        if(active && ClientPlayNetworking.canSend(C2SFinishTreePacket.TYPE)) ClientPlayNetworking.send(new C2SFinishTreePacket(false));
        active=false; target=null; placeDirty=false; previewOrigin=null;
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
        revision.incrementAndGet(); preview=null; dirty=true; target=null; previewOrigin=null; active=false; placeDirty=false;
        finishPhase=FinishPhase.NONE; finishCallback=null; presets=BUILT_IN_PRESETS;
        ThumbnailFBOMaker.clear();
        sessionEpoch++;
    }
    public static volatile int sessionEpoch;
    public static void toast(String key) {
        if(Minecraft.getInstance().player!=null) Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(key));
    }
}
