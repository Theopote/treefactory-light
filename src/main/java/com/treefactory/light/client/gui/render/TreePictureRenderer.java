package com.treefactory.light.client.gui.render;

import com.treefactory.light.client.PreviewSnapshot;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.util.WorldBatchPaster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class TreePictureRenderer extends PictureInPictureRenderer<TreePictureRenderer.State> {
    public record State(PreviewSnapshot snapshot,float angle,boolean hologram,int x0,int y0,int x1,int y1,float scale,
                        ScreenRectangle scissorArea) implements PictureInPictureRenderState {
        @Override public ScreenRectangle bounds() { return PictureInPictureRenderState.getBounds(x0,y0,x1,y1,scissorArea); }
    }
    @Override public @NonNull Class<State> getRenderStateClass() { return State.class; }
    @Override protected @NonNull String getTextureLabel() { return "Treefactory tree"; }
    @Override protected float getTranslateY(int height,int scale) { return height/2f; }
    @Override protected void renderToTexture(State state, PoseStack pose, @NonNull SubmitNodeCollector collector) {
        pose.rotate(new Quaternionf().rotationZ((float)Math.PI).rotateX((float)Math.toRadians(30)).rotateY(state.angle()));
        pose.translate(0,-state.snapshot.centerY(),0);
        if(state.hologram) collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,buffer)->HologramRenderer.draw(state.snapshot,matrix,buffer));
        else submitBlocks(state.snapshot,pose,collector);
    }
    /** Uses the current resource pack's baked block models rather than placeholder thumbnail geometry. */
    public static void submitBlocks(PreviewSnapshot snapshot,PoseStack pose,SubmitNodeCollector collector) {
        Minecraft mc=Minecraft.getInstance();
        var palette=WorldBatchPaster.palette(snapshot.config());
        EnumMap<Voxelizer.Material,List<BlockStateModelPart>> parts=new EnumMap<>(Voxelizer.Material.class);
        EnumMap<Voxelizer.Material,int[]> tints=new EnumMap<>(Voxelizer.Material.class);
        var models=mc.getModelManager().getBlockStateModelSet();
        for(var material:Voxelizer.Material.values()) {
            var block=palette.get(material);
            List<BlockStateModelPart> modelParts=new ArrayList<>();
            models.get(block).collectParts(RandomSource.create(snapshot.config().seed()),modelParts);
            parts.put(material,modelParts);
            tints.put(material,mc.getBlockColors().getTintSources(block).stream().mapToInt(source->source.color(block)).toArray());
        }
        for(var cell:snapshot.surface()) {
            var material=snapshot.tree().blocks().get(cell);
            pose.pushPose(); pose.translate(cell.x(),cell.y(),cell.z());
            collector.submitBlockModel(pose,material.woody()?RenderTypes.solidMovingBlock():RenderTypes.cutoutMovingBlock(),
                    parts.get(material),tints.get(material),0xF000F0,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        }
    }
}
