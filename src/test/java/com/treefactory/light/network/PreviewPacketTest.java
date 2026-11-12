package com.treefactory.light.network;

import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.config.TreeType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewPacketTest {
    @Test void rejectsInvalidNumericValueWithoutDisconnecting() {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            C2SPreviewTreePacket.CODEC.encode(buffer,new C2SPreviewTreePacket(BlockPos.ZERO,TreeConfig.defaults()));
            buffer.setDouble(9,Double.NaN);
            var decoded=C2SPreviewTreePacket.CODEC.decode(buffer);
            assertNull(decoded.config());
            assertNotNull(decoded.validationError());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
    @Test void roundTripsTreeTypeAndContinuousHeight() {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            var config=TreeConfig.defaults().withTreeType(TreeType.PINE).withTrunkHeight(10.3).withTaper(.73);
            config=config.withCanopy(config.canopyShape(),config.canopyScale(),.1);
            var packet=new C2SPreviewTreePacket(new BlockPos(12,75,-3),config);
            C2SPreviewTreePacket.CODEC.encode(buffer,packet);
            assertEquals(packet,C2SPreviewTreePacket.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
