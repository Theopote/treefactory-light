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
import java.util.*;

public final class TreePictureRenderer extends PictureInPictureRenderer<TreePictureRenderer.State> {
    public record State(PreviewSnapshot snapshot,float angle,boolean hologram,int x0,int y0,int x1,int y1,float scale,
                        ScreenRectangle scissorArea) implements PictureInPictureRenderState {
        @Override public ScreenRectangle bounds() { return PictureInPictureRenderState.getBounds(x0,y0,x1,y1,scissorArea); }
    }
    @Override public Class<State> getRenderStateClass() { return State.class; }
    @Override protected String getTextureLabel() { return "Treefactory tree"; }
    @Override protected float getTranslateY(int height,int scale) { return height/2f; }
    @Override protected void renderToTexture(State state,PoseStack pose,SubmitNodeCollector collector) {
        pose.rotate(new Quaternionf().rotationZ((float)Math.PI).rotateX((float)Math.toRadians(30)).rotateY(state.angle()));
        pose.translate(0,-state.snapshot.centerY(),0);
        if(state.hologram) collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,buffer)->HologramRenderer.draw(state.snapshot,matrix,buffer));
        else submitBlocks(state.snapshot,pose,collector);
    }
    /** Uses the current resource pack's baked block models rather than placeholder thumbnail geometry. */
    public static void submitBlocks(PreviewSnapshot snapshot,PoseStack pose,SubmitNodeCollector collector) {
        Minecraft mc=Minecraft.getInstance();
        var log=WorldBatchPaster.palette(snapshot.config().logBlock(),false);
        var leaves=WorldBatchPaster.palette(snapshot.config().leavesBlock(),true);
        List<BlockStateModelPart> logParts=new ArrayList<>(),leafParts=new ArrayList<>();
        var models=mc.getModelManager().getBlockStateModelSet();
        models.get(log).collectParts(RandomSource.create(snapshot.config().seed()),logParts);
        models.get(leaves).collectParts(RandomSource.create(snapshot.config().seed()),leafParts);
        int[] tint=mc.getBlockColors().getTintSources(leaves).stream().mapToInt(source->source.color(leaves)).toArray();
        int[] logTint=mc.getBlockColors().getTintSources(log).stream().mapToInt(source->source.color(log)).toArray();
        for(var cell:snapshot.surface()) {
            boolean leaf=snapshot.tree().blocks().get(cell)==Voxelizer.Material.LEAVES;
            pose.pushPose(); pose.translate(cell.x(),cell.y(),cell.z());
            collector.submitBlockModel(pose,leaf?RenderTypes.cutoutMovingBlock():RenderTypes.solidMovingBlock(),
                    leaf?leafParts:logParts,leaf?tint:logTint,0xF000F0,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        }
    }
}
