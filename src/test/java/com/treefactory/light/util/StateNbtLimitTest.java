package com.treefactory.light.util;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StateNbtLimitTest {
    @Test void quotaIsOneQuarterOfHeapWithA512MiBCap() {
        long mib=1024L*1024;
        assertEquals(256*mib,WorldBatchPaster.stateNbtQuota(1024*mib));
        assertEquals(512*mib,WorldBatchPaster.stateNbtQuota(4096*mib));
    }

    @Test void oversizedNbtIsRejectedWithoutAlteringTheFile(@TempDir Path directory) throws Exception {
        Path file=directory.resolve("treefactory_light.dat");
        var root=new CompoundTag();
        root.putByteArray("data",new byte[16_384]);
        NbtIo.writeCompressed(root,file);
        byte[] before=Files.readAllBytes(file);
        assertThrows(NbtAccounterException.class,() -> WorldBatchPaster.readStateFile(file,1024));
        assertArrayEquals(before,Files.readAllBytes(file));
        assertEquals(root,WorldBatchPaster.readStateFile(file,32_768));
    }
}
