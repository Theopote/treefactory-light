package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.core.config.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record C2SPreviewTreePacket(BlockPos origin, @Nullable TreeConfig config, @Nullable String validationError) implements CustomPacketPayload {
    public C2SPreviewTreePacket(BlockPos origin, TreeConfig config) {
        this(origin,java.util.Objects.requireNonNull(config),null);
    }
    public C2SPreviewTreePacket {
        java.util.Objects.requireNonNull(origin);
        if ((config == null) == (validationError == null))
            throw new IllegalArgumentException("Expected either a preview configuration or a validation error");
    }
    public static final Type<C2SPreviewTreePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TreefactoryLight.MOD_ID, "preview_tree_v7"));
    public static final StreamCodec<RegistryFriendlyByteBuf,C2SPreviewTreePacket> CODEC = CustomPacketPayload.codec(C2SPreviewTreePacket::write,C2SPreviewTreePacket::read);
    private void write(RegistryFriendlyByteBuf b) {
        if (config==null) throw new IllegalStateException("Cannot send an invalid preview");
        b.writeBlockPos(origin); writeEnum(b,config.treeType()); b.writeDouble(config.trunkHeight()); b.writeLong(config.seed());
        b.writeDouble(config.crookedness()); b.writeDouble(config.spread()); b.writeDouble(config.fullness());
        writeEnum(b,config.canopyShape()); b.writeDouble(config.canopyScale()); b.writeDouble(config.attractionDensity()); b.writeDouble(config.taper()); b.writeDouble(config.trunkThickness());
        b.writeDouble(config.crownStretch()); b.writeVarInt(config.crownLayers());
        b.writeDouble(config.branchAngleBias()); b.writeDouble(config.branchDroop()); b.writeDouble(config.apicalDominance());
        b.writeDouble(config.gravityInfluence());
        var p=config.blockPalette();
        b.writeUtf(p.mainTrunk(),128); b.writeUtf(p.thickBranch(),128); b.writeUtf(p.thinBranch(),128);
        b.writeUtf(p.primaryLeaf(),128); b.writeUtf(p.secondaryLeaf(),128); b.writeUtf(p.accentBlock(),128);
        b.writeDouble(p.primaryWeight()); b.writeDouble(p.secondaryWeight()); b.writeDouble(p.accentWeight());
    }
    private static C2SPreviewTreePacket read(RegistryFriendlyByteBuf b) {
        BlockPos origin=b.readBlockPos(); TreeType preset=readEnum(b,TreeType.class); double height=b.readDouble();
        long seed=b.readLong(); double crooked=b.readDouble(), spread=b.readDouble(), fullness=b.readDouble();
        CanopyShape shape=readEnum(b,CanopyShape.class); double crownScale=b.readDouble(), density=b.readDouble(), taper=b.readDouble(), thickness=b.readDouble();
        double stretch=b.readDouble(); int layers=b.readVarInt();
        double angleBias=b.readDouble(), droop=b.readDouble(), apical=b.readDouble(), gravity=b.readDouble();
        String main=b.readUtf(128), thick=b.readUtf(128), thin=b.readUtf(128);
        String primary=b.readUtf(128), secondary=b.readUtf(128), accent=b.readUtf(128);
        double primaryWeight=b.readDouble(), secondaryWeight=b.readDouble(), accentWeight=b.readDouble();
        try {
            BlockPalette palette=new BlockPalette(main,thick,thin,primary,secondary,accent,
                    primaryWeight,secondaryWeight,accentWeight);
            return new C2SPreviewTreePacket(origin,new TreeConfig(preset,seed,crooked,spread,fullness,shape,crownScale,density,taper,palette,height,thickness,stretch,layers,angleBias,droop,apical,gravity));
        } catch (IllegalArgumentException error) {
            return new C2SPreviewTreePacket(origin,null,error.getMessage() != null ? error.getMessage() : "Invalid preview configuration");
        }
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
