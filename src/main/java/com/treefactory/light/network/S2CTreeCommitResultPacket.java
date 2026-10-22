package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server response after the player requests to finish (commit) a preview tree. */
public record S2CTreeCommitResultPacket(boolean success) implements CustomPacketPayload {
    public static final Type<S2CTreeCommitResultPacket> TYPE = new Type<>(TreefactoryLight.id("commit_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CTreeCommitResultPacket> CODEC = CustomPacketPayload.codec(
            (packet, b) -> b.writeBoolean(packet.success), b -> new S2CTreeCommitResultPacket(b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
