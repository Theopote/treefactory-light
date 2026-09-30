package com.treefactory.light.client.gui.render;

import com.treefactory.light.client.*;
import com.treefactory.light.client.gui.DrawerScreen;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;

public final class HologramRenderer {
    private record WorldPreview(PreviewSnapshot snapshot,BlockPos origin) {}
    private static final RenderStateDataKey<WorldPreview> KEY=RenderStateDataKey.create(()->"treefactory hologram");
    // Counterclockwise exterior faces; only exposed voxel faces are submitted.
    private static final float[][][] VERTICES={
            {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},{{1,0,1},{1,0,0},{1,1,0},{1,1,1}},
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}},{{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
            {{1,0,0},{0,0,0},{0,1,0},{1,1,0}},{{0,0,1},{1,0,1},{1,1,1},{0,1,1}}
    };
    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(context->{
            ClientTreeState state=ClientTreeState.INSTANCE;
            PreviewSnapshot snapshot=state.preview(); BlockPos origin=state.origin();
            context.levelState().setData(KEY,snapshot!=null && origin!=null && state.holdingWand()
                    && !(Minecraft.getInstance().gui.screen() instanceof DrawerScreen) ? new WorldPreview(snapshot,origin) : null);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            WorldPreview preview=context.levelState().getData(KEY);
            if(preview==null) return;
            var camera=context.levelState().cameraRenderState.pos;
            PoseStack pose=context.poseStack(); pose.pushPose();
            pose.translate(preview.origin.getX()-camera.x,preview.origin.getY()-camera.y,preview.origin.getZ()-camera.z);
            context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,buffer)->draw(preview.snapshot,matrix,buffer));
            pose.popPose();
        });
    }
    public static void draw(PreviewSnapshot snapshot,PoseStack.Pose pose,VertexConsumer buffer) {
        for(var face:snapshot.faces()) {
            int color=face.leaves()?0x7052E8BA:0xB079DBFF;
            for(float[] v:VERTICES[face.side()])
                buffer.addVertex(pose.pose(),face.cell().x()+v[0],face.cell().y()+v[1],face.cell().z()+v[2]).setColor(color);
        }
    }
}
