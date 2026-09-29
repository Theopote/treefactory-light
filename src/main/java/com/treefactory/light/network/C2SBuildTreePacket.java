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
        b.writeUtf(config.logBlock(),128); b.writeUtf(config.leavesBlock(),128);
    }
    private static C2SBuildTreePacket read(RegistryFriendlyByteBuf b) {
        return new C2SBuildTreePacket(b.readBlockPos(),new TreeConfig(b.readEnum(TreePreset.class),b.readEnum(TreeScale.class),
                b.readLong(),b.readDouble(),b.readDouble(),b.readDouble(),b.readUtf(128),b.readUtf(128)));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
