package com.treefactory.light.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public final class ConnectedBlockStates {
    private ConnectedBlockStates() {}

    public static boolean supported(BlockState state) {
        return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof HugeMushroomBlock;
    }

    public static BlockGetter view(BlockGetter bounds, Function<BlockPos,BlockState> states) {
        return new BlockGetter() {
            @Override public @Nullable BlockEntity getBlockEntity(@NonNull BlockPos pos) { return null; }
            @Override public @NonNull BlockState getBlockState(@NonNull BlockPos pos) { return states.apply(pos); }
            @Override public @NonNull FluidState getFluidState(@NonNull BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return bounds.getHeight(); }
            @Override public int getMinY() { return bounds.getMinY(); }
        };
    }

    public static BlockState resolve(BlockGetter view, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof FenceBlock fence) {
            for (var entry : CrossCollisionBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                Direction direction=entry.getKey();
                BlockPos neighborPos=pos.relative(direction);
                BlockState neighbor=view.getBlockState(neighborPos);
                Direction face=direction.getOpposite();
                state=state.setValue(entry.getValue(),
                        fence.connectsTo(neighbor,neighbor.isFaceSturdy(view,neighborPos,face),face));
            }
        } else if (state.getBlock() instanceof HugeMushroomBlock mushroom) {
            for (var entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet())
                state=state.setValue(entry.getValue(),!view.getBlockState(pos.relative(entry.getKey())).is(mushroom));
        }
        return state;
    }
}
