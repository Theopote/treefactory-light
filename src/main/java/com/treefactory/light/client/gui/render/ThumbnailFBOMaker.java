package com.treefactory.light.client.gui.render;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.client.PreviewSnapshot;
import com.treefactory.light.core.config.PresetStore;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** GPU work stays on the render thread; PNG reads/writes are asynchronous. */
public final class ThumbnailFBOMaker {
    private record Request(String id,PreviewSnapshot snapshot,Path path) {}
    private static final Queue<Request> PENDING=new ArrayDeque<>();
    private static final Map<String,Identifier> TEXTURES=new LinkedHashMap<>();
    private static final Set<String> LOADING=new HashSet<>();
    private static int epoch;
    public static void enqueue(PresetStore.Preset preset,PreviewSnapshot snapshot,Path path) {
        if(PENDING.size()<16) PENDING.add(new Request(preset.id(),snapshot,path));
    }
    public static Identifier texture(String id,Path path) {
        Identifier texture=TEXTURES.get(id);
        if(texture!=null || !LOADING.add(id)) return texture;
        int token=epoch;
        CompletableFuture.supplyAsync(()-> {
            try { return Files.isRegularFile(path) && Files.size(path)<2_000_000 ? NativeImage.read(Files.readAllBytes(path)) : null; }
            catch(Exception e) { TreefactoryLight.LOGGER.warn("Thumbnail read failed: {}",path,e); return null; }
        }).thenAccept(image->Minecraft.getInstance().execute(()-> {
            if(image==null) return;
            if(token!=epoch) { image.close(); return; }
            register(id,image);
        }));
        return null;
    }
    public static void renderPending() {
        Request request=PENDING.poll(); if(request==null) return;
        int token=epoch;
        TextureTarget target=new TextureTarget("Treefactory thumbnail",128,128,GpuFormat.RGBA8_UNORM,GpuFormat.D32_FLOAT);
        var oldProjection=RenderSystem.getProjectionMatrixBuffer(); var oldType=RenderSystem.getProjectionType();
        var oldFog=RenderSystem.getShaderFog();
        ProjectionMatrixBuffer projectionBuffer=new ProjectionMatrixBuffer("Treefactory thumbnail projection");
        RenderSystem.getModelViewStack().pushMatrix().identity();
        try (FogRenderer fog=new FogRenderer()) {
            RenderSystem.setShaderFog(fog.getBuffer(FogRenderer.FogMode.NONE));
            Projection projection=new Projection(); projection.setupOrtho(-1000,1000,128,128,true);
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection),ProjectionType.ORTHOGRAPHIC);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(),new org.joml.Vector4f(.04f,.08f,.07f,1),target.getDepthTexture(),0);
            PoseStack pose=new PoseStack(); pose.translate(64,64,0);
            float scale=(float)(112/request.snapshot.extent()); pose.scale(scale,scale,-scale);
            pose.rotate(new Quaternionf().rotationZ((float)Math.PI).rotateX((float)Math.toRadians(45)).rotateY((float)Math.toRadians(45)));
            pose.translate(0,-request.snapshot.centerY(),0);
            SubmitNodeStorage collector=new SubmitNodeStorage();
            TreePictureRenderer.submitBlocks(request.snapshot,pose,collector);
            try(var frame=Minecraft.getInstance().gameRenderer.featureRenderDispatcher().prepareFrame(collector);
                var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Treefactory thumbnail",target.getColorTextureView(),Optional.empty(),target.getDepthTextureView(),OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass); FeatureRenderDispatcher.renderAllFeatures(pass,frame);
            }
            Screenshot.takeScreenshot(target,image->{
                target.destroyBuffers();
                CompletableFuture.runAsync(()-> {
                    try { image.writeToFile(request.path); }
                    catch(Exception e) { TreefactoryLight.LOGGER.warn("Thumbnail write failed",e); }
                }).whenComplete((unused,error)->Minecraft.getInstance().execute(()-> {
                    if(token!=epoch) image.close(); else register(request.id,image);
                }));
            });
        } catch(Exception e) { target.destroyBuffers(); TreefactoryLight.LOGGER.warn("Thumbnail rendering failed",e); }
        finally {
            RenderSystem.getModelViewStack().popMatrix();
            if(oldProjection!=null) RenderSystem.setProjectionMatrix(oldProjection,oldType);
            RenderSystem.setShaderFog(oldFog);
            projectionBuffer.close();
        }
    }
    private static void register(String id,NativeImage image) {
        Identifier identifier=TreefactoryLight.id("preset/"+UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Minecraft.getInstance().getTextureManager().register(identifier,new DynamicTexture(()->"Tree preset "+id,image));
        TEXTURES.put(id,identifier);
        while(TEXTURES.size()>64) {
            String oldest=TEXTURES.keySet().iterator().next();
            Minecraft.getInstance().getTextureManager().release(TEXTURES.remove(oldest)); LOADING.remove(oldest);
        }
    }
    public static void clear() {
        epoch++; PENDING.clear(); LOADING.clear();
        TEXTURES.values().forEach(id->Minecraft.getInstance().getTextureManager().release(id)); TEXTURES.clear();
    }
}
