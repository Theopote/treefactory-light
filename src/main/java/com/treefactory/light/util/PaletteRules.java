package com.treefactory.light.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.*;

/** Single source of truth for which blocks each tree role accepts; used by the server check and the client picker. */
public final class PaletteRules {
    public enum Role {
        TRUNK, THICK_BRANCH, THIN_BRANCH, PRIMARY_LEAF, SECONDARY_LEAF, ACCENT;
        public boolean leaves() { return this==PRIMARY_LEAF || this==SECONDARY_LEAF; }
    }
    private static final Set<Block> ACCENT_BLOCKS=Set.of(
            Blocks.MOSS_BLOCK,Blocks.OCHRE_FROGLIGHT,Blocks.PEARLESCENT_FROGLIGHT,Blocks.VERDANT_FROGLIGHT,
            Blocks.SHROOMLIGHT,Blocks.GLOWSTONE,Blocks.SEA_LANTERN,Blocks.HAY_BLOCK,Blocks.TARGET,
            Blocks.RED_MUSHROOM_BLOCK,Blocks.BROWN_MUSHROOM_BLOCK);
    private PaletteRules() {}

    public static boolean allowed(Role role, BlockState state) {
        return switch(role) {
            case TRUNK, THICK_BRANCH -> state.is(BlockTags.LOGS);
            case THIN_BRANCH -> state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.FENCES);
            case PRIMARY_LEAF, SECONDARY_LEAF -> state.is(BlockTags.LEAVES);
            case ACCENT -> state.is(BlockTags.LEAVES) || ACCENT_BLOCKS.contains(state.getBlock());
        };
    }
    /** Resolves and validates an identifier for a role; leaves are made persistent so they never decay. */
    public static BlockState resolve(Role role, String id) {
        Block block=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id))
                .orElseThrow(()->new IllegalArgumentException("Unknown block: "+id));
        BlockState state=block.defaultBlockState();
        if(!allowed(role,state)) throw new IllegalArgumentException("Block not allowed for "+role+": "+id);
        if(state.is(BlockTags.LEAVES) && state.hasProperty(BlockStateProperties.PERSISTENT))
            state=state.setValue(BlockStateProperties.PERSISTENT,true);
        return state;
    }
    /** All registry blocks the role accepts, sorted by identifier; includes modded blocks that carry the right tags. */
    public static List<Block> candidates(Role role) {
        List<Block> result=new ArrayList<>();
        for(Block block:BuiltInRegistries.BLOCK) if(allowed(role,block.defaultBlockState())) result.add(block);
        result.sort(Comparator.comparing(b->BuiltInRegistries.BLOCK.getKey(b).toString()));
        return List.copyOf(result);
    }
    public static String id(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }
}
