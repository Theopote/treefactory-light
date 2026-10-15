package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.BuiltInPresets;
import com.treefactory.light.core.tree.TreeBuildTask;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuiltInPresetsTest {
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
