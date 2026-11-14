package com.treefactory.light.client.gui.render;

import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayeredModelPartsTest {
    @Test void separatesMixedLayersWithoutLosingFaceDirections() {
        BakedQuad solid=quad(ChunkSectionLayer.SOLID), cutout=quad(ChunkSectionLayer.CUTOUT),
                translucent=quad(ChunkSectionLayer.TRANSLUCENT);
        BlockStateModelPart source=new BlockStateModelPart() {
            @Override public List<BakedQuad> getQuads(Direction face) {
                if (face==Direction.NORTH) return List.of(solid,translucent);
                return face==null ? List.of(cutout,translucent) : List.of();
            }
            @Override public boolean useAmbientOcclusion() { return true; }
            @Override public Material.Baked particleMaterial() { return null; }
            @Override public int materialFlags() { return 1; }
        };
        var layers=LayeredModelParts.split(List.of(source));
        assertEquals(3,layers.size());
        var solidPart=layers.get(ChunkSectionLayer.SOLID).getFirst();
        var cutoutPart=layers.get(ChunkSectionLayer.CUTOUT).getFirst();
        var translucentPart=layers.get(ChunkSectionLayer.TRANSLUCENT).getFirst();
        assertEquals(List.of(solid),solidPart.getQuads(Direction.NORTH));
        assertTrue(solidPart.getQuads(null).isEmpty());
        assertEquals(List.of(cutout),cutoutPart.getQuads(null));
        assertTrue(cutoutPart.getQuads(Direction.NORTH).isEmpty());
        assertEquals(List.of(translucent),translucentPart.getQuads(Direction.NORTH));
        assertEquals(List.of(translucent),translucentPart.getQuads(null));
        assertTrue(translucentPart.getQuads(Direction.SOUTH).isEmpty());
    }

    private static BakedQuad quad(ChunkSectionLayer layer) {
        var point=new Vector3f();
        var material=new BakedQuad.MaterialInfo(null,layer,null,null,null,-1,null,0);
        return new BakedQuad(point,point,point,point,0,0,0,0,Direction.NORTH,material);
    }
}
