package com.treefactory.light.network;

import com.treefactory.light.TreefactoryLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

/** Ends the live preview: commit keeps the tree permanently, otherwise the preview is removed. */
public record C2SEndPreviewPacket(boolean commit, long requestId) implements CustomPacketPayload {
    public static final Type<C2SEndPreviewPacket> TYPE = new Type<>(TreefactoryLight.id("finish_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf,C2SEndPreviewPacket> CODEC = CustomPacketPayload.codec(
            (packet,b)-> { b.writeBoolean(packet.commit); b.writeLong(packet.requestId); },
            b->new C2SEndPreviewPacket(b.readBoolean(), b.readLong()));
    @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
