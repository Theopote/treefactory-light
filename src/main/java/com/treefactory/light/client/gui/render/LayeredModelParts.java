package com.treefactory.light.client.gui.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/** Groups each baked quad by its own material layer, including models with mixed layers. */
final class LayeredModelParts {
    private static final Direction[] DIRECTIONS=Direction.values();
    private LayeredModelParts() {}

    static Map<ChunkSectionLayer,List<BlockStateModelPart>> split(List<BlockStateModelPart> parts) {
        Map<ChunkSectionLayer,List<BlockStateModelPart>> result=new EnumMap<>(ChunkSectionLayer.class);
        for (BlockStateModelPart part : parts) {
            Map<ChunkSectionLayer,List<List<BakedQuad>>> byLayer=new EnumMap<>(ChunkSectionLayer.class);
            for (int i=0;i<=DIRECTIONS.length;i++) {
                Direction direction=i==DIRECTIONS.length ? null : DIRECTIONS[i];
                for (BakedQuad quad : part.getQuads(direction)) {
                    var faces=byLayer.computeIfAbsent(quad.materialInfo().layer(),layer -> emptyFaces());
                    faces.get(i).add(quad);
                }
            }
            byLayer.forEach((layer,faces) ->
                    result.computeIfAbsent(layer,key -> new ArrayList<>()).add(new LayerPart(part,faces)));
        }
        return result;
    }

    private static List<List<BakedQuad>> emptyFaces() {
        List<List<BakedQuad>> faces=new ArrayList<>(DIRECTIONS.length+1);
        for (int i=0;i<=DIRECTIONS.length;i++) faces.add(new ArrayList<>());
        return faces;
    }

    private record LayerPart(BlockStateModelPart source,List<List<BakedQuad>> faces) implements BlockStateModelPart {
        @Override public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return faces.get(direction==null ? DIRECTIONS.length : direction.ordinal());
        }
        @Override public boolean useAmbientOcclusion() { return source.useAmbientOcclusion(); }
        @Override public Material.Baked particleMaterial() { return source.particleMaterial(); }
        @Override public int materialFlags() { return source.materialFlags(); }
    }
}
