package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.config.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SBuildTreePacket(BlockPos origin, TreeConfig config) implements CustomPacketPayload {
    public static final Type<C2SBuildTreePacket> TYPE = new Type<>(TreefactoryLight.id("build_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf,C2SBuildTreePacket> CODEC = CustomPacketPayload.codec(C2SBuildTreePacket::write,C2SBuildTreePacket::read);
    private void write(RegistryFriendlyByteBuf b) {
        b.writeBlockPos(origin); b.writeEnum(config.preset()); b.writeEnum(config.scale()); b.writeLong(config.seed());
        b.writeDouble(config.crookedness()); b.writeDouble(config.spread()); b.writeDouble(config.fullness());
        b.writeEnum(config.canopyShape()); b.writeDouble(config.canopyScale()); b.writeDouble(config.attractionDensity()); b.writeDouble(config.pipeExponent());
        var p=config.blockPalette();
        b.writeUtf(p.mainTrunk(),128); b.writeUtf(p.thickBranch(),128); b.writeUtf(p.thinBranch(),128);
        b.writeUtf(p.primaryLeaf(),128); b.writeUtf(p.secondaryLeaf(),128); b.writeUtf(p.accentBlock(),128);
        b.writeDouble(p.primaryWeight()); b.writeDouble(p.secondaryWeight()); b.writeDouble(p.accentWeight());
    }
    private static C2SBuildTreePacket read(RegistryFriendlyByteBuf b) {
        BlockPos origin=b.readBlockPos(); TreePreset preset=b.readEnum(TreePreset.class); TreeScale scale=b.readEnum(TreeScale.class);
        long seed=b.readLong(); double crooked=b.readDouble(), spread=b.readDouble(), fullness=b.readDouble();
        CanopyShape shape=b.readEnum(CanopyShape.class); double crownScale=b.readDouble(), density=b.readDouble(), exponent=b.readDouble();
        BlockPalette palette=new BlockPalette(b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readUtf(128),
                b.readDouble(),b.readDouble(),b.readDouble());
        return new C2SBuildTreePacket(origin,new TreeConfig(preset,scale,seed,crooked,spread,fullness,shape,crownScale,density,exponent,palette));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
