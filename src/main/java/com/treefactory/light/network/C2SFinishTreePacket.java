package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Ends the live preview: commit keeps the tree permanently, otherwise the preview is removed. */
public record C2SFinishTreePacket(boolean commit) implements CustomPacketPayload {
    public static final Type<C2SFinishTreePacket> TYPE = new Type<>(TreefactoryLight.id("finish_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf,C2SFinishTreePacket> CODEC = CustomPacketPayload.codec(
            (packet,b)->b.writeBoolean(packet.commit),b->new C2SFinishTreePacket(b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
