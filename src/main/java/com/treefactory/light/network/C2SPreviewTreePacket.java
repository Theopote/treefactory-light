package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.config.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

public record C2SPreviewTreePacket(BlockPos origin, TreeConfig config) implements CustomPacketPayload {
    public static final Type<C2SPreviewTreePacket> TYPE = new Type<>(TreefactoryLight.id("preview_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf,C2SPreviewTreePacket> CODEC = CustomPacketPayload.codec(C2SPreviewTreePacket::write,C2SPreviewTreePacket::read);
    private void write(RegistryFriendlyByteBuf b) {
        b.writeBlockPos(origin); writeEnum(b,config.preset()); writeEnum(b,config.scale()); b.writeDouble(config.height()); b.writeLong(config.seed());
        b.writeDouble(config.crookedness()); b.writeDouble(config.spread()); b.writeDouble(config.fullness());
        writeEnum(b,config.canopyShape()); b.writeDouble(config.canopyScale()); b.writeDouble(config.attractionDensity()); b.writeDouble(config.pipeExponent()); b.writeDouble(config.trunkThickness());
        b.writeDouble(config.crownStretch()); b.writeVarInt(config.crownLayers());
        b.writeDouble(config.branchAngleBias()); b.writeDouble(config.branchDroop()); b.writeDouble(config.apicalDominance());
        var p=config.blockPalette();
        b.writeUtf(p.mainTrunk(),128); b.writeUtf(p.thickBranch(),128); b.writeUtf(p.thinBranch(),128);
        b.writeUtf(p.primaryLeaf(),128); b.writeUtf(p.secondaryLeaf(),128); b.writeUtf(p.accentBlock(),128);
        b.writeDouble(p.primaryWeight()); b.writeDouble(p.secondaryWeight()); b.writeDouble(p.accentWeight());
    }
    private static C2SPreviewTreePacket read(RegistryFriendlyByteBuf b) {
        BlockPos origin=b.readBlockPos(); TreePreset preset=readEnum(b,TreePreset.class); TreeScale scale=readEnum(b,TreeScale.class); double height=b.readDouble();
        long seed=b.readLong(); double crooked=b.readDouble(), spread=b.readDouble(), fullness=b.readDouble();
        CanopyShape shape=readEnum(b,CanopyShape.class); double crownScale=b.readDouble(), density=b.readDouble(), exponent=b.readDouble(), thickness=b.readDouble();
        double stretch=b.readDouble(); int layers=b.readVarInt();
        double angleBias=b.readDouble(), droop=b.readDouble(), apical=b.readDouble();
        BlockPalette palette=new BlockPalette(b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readUtf(128),
                b.readDouble(),b.readDouble(),b.readDouble());
        return new C2SPreviewTreePacket(origin,new TreeConfig(preset,scale,seed,crooked,spread,fullness,shape,crownScale,density,exponent,palette,height,thickness,stretch,layers,angleBias,droop,apical));
    }
    private static <E extends Enum<E>> void writeEnum(RegistryFriendlyByteBuf b, E value) {
        b.writeById(Enum::ordinal, value);
    }
    private static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf b, Class<E> type) {
        E[] values = type.getEnumConstants();
        return b.readById(id -> values[id]);
    }
    @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
