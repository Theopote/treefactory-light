package com.treefactory.light.client.gui.render;

import com.treefactory.light.client.*;
import com.mojang.blaze3d.vertex.*;

public final class HologramRenderer {
    // Counterclockwise exterior faces; only exposed voxel faces are submitted.
    private static final float[][][] VERTICES={
            {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},{{1,0,1},{1,0,0},{1,1,0},{1,1,1}},
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}},{{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
            {{1,0,0},{0,0,0},{0,1,0},{1,1,0}},{{0,0,1},{1,0,1},{1,1,1},{0,1,1}}
    };
    public static void draw(PreviewSnapshot snapshot,PoseStack.Pose pose,VertexConsumer buffer) {
        for(var face:snapshot.faces()) {
            int color=switch(face.material()) {
                case MAIN_TRUNK -> 0xB079DBFF; case THICK_BRANCH -> 0xB06CBFE8; case THIN_BRANCH -> 0xB050A8CE;
                case PRIMARY_LEAF -> 0x7052E8BA; case SECONDARY_LEAF -> 0x70EF9CD1; case ACCENT -> 0x70EED981;
            };
            for(float[] v:VERTICES[face.side()])
                buffer.addVertex(pose.pose(),face.cell().x()+v[0],face.cell().y()+v[1],face.cell().z()+v[2]).setColor(color);
        }
    }
}
