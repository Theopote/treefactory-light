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
    private BlockPos lockedOrigin;
    private static final List<PresetStore.Preset> BUILT_IN_PRESETS=Arrays.stream(TreePreset.values())
            .map(type->new PresetStore.Preset("builtin-"+type.id(),type.id(),"Treefactory Light",
                    TreeConfig.defaults().withPreset(type)))
            .toList();
    private List<PresetStore.Preset> presets=BUILT_IN_PRESETS;
    private String status="";
    private long lastAction;
    private boolean hasAction;
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
        return true;
    }
    public boolean holdingWand() {
        var p=Minecraft.getInstance().player;
        return p!=null && p.getMainHandItem().is(TreefactoryLight.TREE_WAND);
    }
    public BlockPos origin() {
        if(Minecraft.getInstance().gui.screen() instanceof DrawerScreen) return lockedOrigin;
        var hit=Minecraft.getInstance().hitResult;
        if(!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK
                || block.getDirection()!=Direction.UP || Minecraft.getInstance().level==null) return null;
        BlockPos clicked=block.getBlockPos();
        return Minecraft.getInstance().level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.above();
    }
    public void open() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui.screen() instanceof DrawerScreen) { mc.gui.setScreen(null); return; }
        lockedOrigin=origin(); mc.gui.setScreen(new DrawerScreen()); refreshLibrary();
    }
    public void tick() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || (!holdingWand() && !(mc.gui.screen() instanceof DrawerScreen))) return;
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
    public void build() {
        if(!throttle()) return;
        BlockPos origin=origin();
        if(origin==null) { toast("treefactory.aim_first"); return; }
        if(!ClientPlayNetworking.canSend(C2SBuildTreePacket.TYPE)) { toast("treefactory.no_server"); return; }
        ClientPlayNetworking.send(new C2SBuildTreePacket(origin,config));
    }
    public void undo() {
        if(!throttle()) return;
        if(ClientPlayNetworking.canSend(C2SUndoPacket.TYPE)) ClientPlayNetworking.send(new C2SUndoPacket());
        else toast("treefactory.no_server");
    }
    private boolean throttle() {
        long now=System.nanoTime();
        if(hasAction && now-lastAction<250_000_000L) return false;
        lastAction=now; hasAction=true; return true;
    }
    public void cycleScale(int direction) {
        update(config.withScale(config.scale().cycle(direction)));
        var player=Minecraft.getInstance().player;
        if(player!=null) player.sendOverlayMessage(Component.translatable("treefactory.size",Component.translatable("treefactory.scale."+config.scale().id())));
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
    public void disconnect() {
        revision.incrementAndGet(); preview=null; dirty=true; lockedOrigin=null; presets=BUILT_IN_PRESETS;
        hasAction=false;
        ThumbnailFBOMaker.clear();
    }
    public static void toast(String key) {
        if(Minecraft.getInstance().player!=null) Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(key));
    }
}
