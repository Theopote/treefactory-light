package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.BuiltInPresets;
import com.treefactory.light.core.config.BlockPalette;
import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.config.TreeType;
import com.treefactory.light.core.tree.TreeBuildTask;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuiltInPresetsTest {
    @Test void defaultAndBuiltInThinBranchesMatchWood() {
        assertEquals("minecraft:oak_fence",TreeConfig.defaults().blockPalette().thinBranch());
        assertEquals("minecraft:spruce_fence",TreeConfig.defaults().withTreeType(TreeType.PINE).blockPalette().thinBranch());
        assertEquals("minecraft:oak_fence",BlockPalette.defaults("oak").thinBranch());
        for (var preset : BuiltInPresets.all()) {
            var palette=preset.config().blockPalette();
            String expected=palette.mainTrunk().replace("_log","_fence");
            assertEquals(expected,palette.thinBranch(),preset.id());
            assertEquals(palette.mainTrunk(),palette.thickBranch(),preset.id());
        }
    }

    @Test void idsAreUniqueAndEveryPresetBuildsWithinBudget() {
        var ids=new HashSet<String>();
        for(var preset:BuiltInPresets.all()) {
            assertTrue(ids.add(preset.id()),preset.id());
            var result=new TreeBuildTask().build(preset.config(),()->false);
            assertFalse(result.blocks().isEmpty(),preset.id());
            assertTrue(result.blocks().size()<=Voxelizer.MAX_BLOCKS,preset.id());
        }
        assertTrue(BuiltInPresets.all().size()>=12);
    }
}
