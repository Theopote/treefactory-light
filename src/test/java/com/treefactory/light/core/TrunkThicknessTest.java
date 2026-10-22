package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeBuildTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrunkThicknessTest {
    @Test void thicknessScalesMonotonicallyWithoutExceedingBudget() {
        int[] blockCounts = new int[3];
        double[] rootRadii = new double[3];
        double[] thicknesses = {0.5, 1.0, 2.0};
        for (int i = 0; i < thicknesses.length; i++) {
            TreeConfig config = TreeConfig.defaults()
                    .withScale(TreeScale.LARGE)
                    .withTrunkThickness(thicknesses[i])
                    .withCrownStretch(1.8)
                    .withSeed(12345L);
            var result = new TreeBuildTask().build(config, () -> false);
            assertTrue(result.blocks().size() <= Voxelizer.MAX_BLOCKS, "t=" + thicknesses[i]);
            rootRadii[i] = result.nodes().getFirst().radius();
            blockCounts[i] = result.blocks().size();
            for (var node : result.nodes()) {
                if (node.parent() >= 0) {
                    assertTrue(node.radius() <= result.nodes().get(node.parent()).radius() + 1e-9,
                            "child thicker than parent at t=" + thicknesses[i]);
                }
            }
        }
        assertTrue(rootRadii[0] < rootRadii[1] && rootRadii[1] < rootRadii[2]);
        assertTrue(blockCounts[2] >= blockCounts[0], "thicker trunk should not shrink the tree unexpectedly");
    }

    @Test void thicknessWithExtremeCrownStretchRemainsStable() {
        TreeConfig config = TreeConfig.defaults()
                .withScale(TreeScale.GIANT)
                .withCanopy(CanopyShape.CONE, 2.0, 2.0)
                .withTrunkThickness(2.0)
                .withCrownStretch(2.0)
                .withDna(1, 1, 1);
        var result = new TreeBuildTask().build(config, () -> false);
        assertTrue(result.blocks().size() <= Voxelizer.MAX_BLOCKS);
        assertTrue(result.blocks().values().stream().anyMatch(Voxelizer.Material::woody));
        assertTrue(result.nodes().getFirst().radius() <= TreeScale.GIANT.height * .055 * 2.0 + 1e-9);
    }
}
