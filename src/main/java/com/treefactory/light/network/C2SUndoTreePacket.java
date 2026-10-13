package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SUndoTreePacket() implements CustomPacketPayload {
    public static final Type<C2SUndoTreePacket> TYPE = new Type<>(TreefactoryLight.id("undo_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf,C2SUndoTreePacket> CODEC = CustomPacketPayload.codec(
            (packet,b)->{}, b->new C2SUndoTreePacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
