package com.treefactory.light.client;

import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.tree.Cell;
import com.treefactory.light.core.tree.TreeBuildTask;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewSnapshotTest {
    @Test void checksWorldPositionsRelativeToThePreviewOrigin() {
        var blocks=Map.of(new Cell(-2,3,4),Voxelizer.Material.PRIMARY_LEAF,
                new Cell(0,0,0),Voxelizer.Material.MAIN_TRUNK);
        var tree=new TreeBuildTask.Result(List.of(),blocks,Map.of());
        var preview=new PreviewSnapshot(TreeConfig.defaults(),tree,List.of(),0,1,1,1,1);
        BlockPos origin=new BlockPos(-100,64,200);
        assertTrue(preview.containsAt(origin,origin.offset(-2,3,4)));
        assertTrue(preview.containsAt(origin,origin));
        assertFalse(preview.containsAt(origin,origin.offset(-2,3,5)));
        assertFalse(preview.containsAt(origin.offset(1,0,0),origin));
    }
}
