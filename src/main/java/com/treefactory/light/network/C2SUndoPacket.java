package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SUndoPacket() implements CustomPacketPayload {
    public static final Type<C2SUndoPacket> TYPE = new Type<>(TreefactoryLight.id("undo"));
    public static final StreamCodec<RegistryFriendlyByteBuf,C2SUndoPacket> CODEC = StreamCodec.unit(new C2SUndoPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
