package com.treefactory.light.util;

import com.treefactory.light.core.config.BuiltInPresets;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class FenceConnectionsTest {
    private static Map<TagKey<Block>,List<Holder<Block>>> originalTags;

    @BeforeAll static void loadFenceTags() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry=BuiltInRegistries.BLOCK;
        originalTags=new HashMap<>();
        registry.getTags().forEach(tag -> originalTags.put(tag.key(),tag.stream().toList()));
        var tags=new HashMap<>(originalTags);
        var woodFences=List.of(Blocks.OAK_FENCE,Blocks.SPRUCE_FENCE,Blocks.BIRCH_FENCE,
                Blocks.CHERRY_FENCE,Blocks.ACACIA_FENCE,Blocks.DARK_OAK_FENCE,Blocks.JUNGLE_FENCE,
                Blocks.MANGROVE_FENCE,Blocks.PALE_OAK_FENCE).stream().map(registry::wrapAsHolder).toList();
        var fences=new java.util.ArrayList<>(woodFences);
        fences.add(registry.wrapAsHolder(Blocks.NETHER_BRICK_FENCE));
        tags.put(BlockTags.FENCES,fences);
        tags.put(BlockTags.WOODEN_FENCES,woodFences);
        registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(),tags)).apply();
    }

    @Test void allBuiltInDefaultFencesResolveForThinBranches() {
        for (var preset : BuiltInPresets.all()) {
            String id=preset.config().blockPalette().thinBranch();
            assertTrue(PaletteRules.resolve(PaletteRules.Role.THIN_BRANCH,id).getBlock()
                    instanceof net.minecraft.world.level.block.FenceBlock,preset.id());
        }
    }

    @AfterAll static void restoreTags() {
        var registry=BuiltInRegistries.BLOCK;
        registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(),originalTags)).apply();
    }

    @Test void followsVanillaRulesForFencesAndSolidNeighbors() {
        var pos=BlockPos.ZERO;
        var blocks=new HashMap<BlockPos,BlockState>();
        blocks.put(pos,Blocks.OAK_FENCE.defaultBlockState());
        blocks.put(pos.north(),Blocks.BIRCH_FENCE.defaultBlockState());
        blocks.put(pos.east(),Blocks.OAK_LOG.defaultBlockState());
        blocks.put(pos.south(),Blocks.NETHER_BRICK_FENCE.defaultBlockState());
        blocks.put(pos.west(),Blocks.OAK_LEAVES.defaultBlockState());
        var view=ConnectedBlockStates.view(EmptyBlockGetter.INSTANCE,
                p -> blocks.getOrDefault(p,Blocks.AIR.defaultBlockState()));
        var resolved=ConnectedBlockStates.resolve(view,pos,blocks.get(pos));
        assertTrue(resolved.getValue(CrossCollisionBlock.NORTH));
        assertTrue(resolved.getValue(CrossCollisionBlock.EAST));
        assertFalse(resolved.getValue(CrossCollisionBlock.SOUTH));
        assertFalse(resolved.getValue(CrossCollisionBlock.WEST));
        assertEquals(blocks.get(pos.east()),ConnectedBlockStates.resolve(view,pos.east(),blocks.get(pos.east())));

        blocks.remove(pos.north());
        assertFalse(ConnectedBlockStates.resolve(view,pos,resolved).getValue(CrossCollisionBlock.NORTH));
        assertTrue(ConnectedBlockStates.resolve(view,pos,resolved).getValue(CrossCollisionBlock.EAST));
    }

    @Test void connectedStatesAreJournaledAndRestoredAcrossBatches() {
        var a=BlockPos.ZERO;
        var b=a.east();
        var fence=Blocks.OAK_FENCE.defaultBlockState();
        var targets=new HashMap<BlockPos,BlockState>(Map.of(a,fence,b,fence));
        var view=ConnectedBlockStates.view(EmptyBlockGetter.INSTANCE,
                p -> targets.getOrDefault(p,Blocks.AIR.defaultBlockState()));
        targets.replaceAll((pos,state) -> ConnectedBlockStates.resolve(view,pos,state));
        assertTrue(targets.get(a).getValue(CrossCollisionBlock.EAST));
        assertTrue(targets.get(b).getValue(CrossCollisionBlock.WEST));

        var air=Blocks.AIR.defaultBlockState();
        var world=new HashMap<BlockPos,BlockState>(Map.of(a,air,b,air));
        var footprint=new PreviewFootprint<BlockPos,BlockState>();
        var placement=new JournaledPlacement<>(footprint,targets);
        JournaledPlacement.Result result;
        do {
            result=placement.tick(1,1,p -> true,world::get,BlockState::isAir,
                    (p,state) -> { world.put(p,state); return true; },
                    () -> CompletableFuture.completedFuture(null));
            assertNotEquals(JournaledPlacement.Status.FAILED,result.status());
            assertNotEquals(JournaledPlacement.Status.STORAGE_FAILED,result.status());
        } while (result.status()!=JournaledPlacement.Status.COMPLETE);
        assertEquals(targets,world);
        assertEquals(targets,footprint.placed);
        for (var pos : targets.keySet())
            assertTrue(footprint.restore(pos,p -> true,world::get,(p,state) -> { world.put(p,state); return true; }));
        assertEquals(Map.of(a,air,b,air),world);
    }

    @Test void mushroomFacesFollowAllSixNeighborsAndBlockType() {
        var pos=BlockPos.ZERO;
        var mushroom=Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
        var blocks=new HashMap<BlockPos,BlockState>();
        blocks.put(pos,mushroom);
        blocks.put(pos.above(),mushroom);
        blocks.put(pos.north(),mushroom);
        blocks.put(pos.east(),Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState());
        var view=ConnectedBlockStates.view(EmptyBlockGetter.INSTANCE,
                p -> blocks.getOrDefault(p,Blocks.AIR.defaultBlockState()));
        var resolved=ConnectedBlockStates.resolve(view,pos,mushroom);
        assertFalse(resolved.getValue(HugeMushroomBlock.UP));
        assertFalse(resolved.getValue(HugeMushroomBlock.NORTH));
        assertTrue(resolved.getValue(HugeMushroomBlock.DOWN));
        assertTrue(resolved.getValue(HugeMushroomBlock.EAST));
        blocks.remove(pos.above());
        assertTrue(ConnectedBlockStates.resolve(view,pos,resolved).getValue(HugeMushroomBlock.UP));
    }
}
