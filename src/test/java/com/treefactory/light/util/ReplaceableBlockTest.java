package com.treefactory.light.util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplaceableBlockTest {
    @Test void refusesMissingOrInvalidSavedBlockState() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var saved=net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.OAK_LOG.defaultBlockState());
        assertEquals(Blocks.OAK_LOG.defaultBlockState(),
                WorldBatchPaster.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK,saved));
        saved.putString("id","minecraft:not_a_block");
        assertThrows(IllegalArgumentException.class,
                () -> WorldBatchPaster.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK,saved));
    }
    @Test void protectsFluidsAndNonSolidConstruction() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var block : new net.minecraft.world.level.block.Block[] {
                Blocks.WATER, Blocks.LAVA, Blocks.RAIL, Blocks.REDSTONE_WIRE, Blocks.LADDER,
                Blocks.STONE_BUTTON, Blocks.LEVER, Blocks.TORCH, Blocks.VINE}) {
            assertFalse(WorldBatchPaster.replaceable(block.defaultBlockState()), block.toString());
        }
        assertTrue(WorldBatchPaster.replaceable(Blocks.AIR.defaultBlockState()));
        assertTrue(WorldBatchPaster.replaceable(Blocks.OAK_LEAVES.defaultBlockState()));
        assertTrue(WorldBatchPaster.replaceable(Blocks.SHORT_GRASS.defaultBlockState()));
    }
}
