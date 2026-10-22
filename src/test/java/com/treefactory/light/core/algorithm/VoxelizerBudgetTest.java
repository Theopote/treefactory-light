package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoxelizerBudgetTest {
    @Test void accumulatorRejectsNewCellsBeyondBudget() {
        Voxelizer.VoxelAccumulator acc = new Voxelizer.VoxelAccumulator();
        for (int i = 0; i < Voxelizer.MAX_BLOCKS; i++) acc.putLeaf(new Cell(i, 0, 0), Voxelizer.Material.PRIMARY_LEAF);
        assertEquals(Voxelizer.MAX_BLOCKS, acc.size());
        assertThrows(IllegalArgumentException.class, () -> acc.putLeaf(new Cell(-1, 0, 0), Voxelizer.Material.PRIMARY_LEAF));
    }

    @Test void connectSpineChecksBudgetOnEveryNewCell() {
        Voxelizer.VoxelAccumulator acc = new Voxelizer.VoxelAccumulator();
        for (int i = 0; i < Voxelizer.MAX_BLOCKS - 1; i++) acc.putWood(new Cell(i, 0, 0), Voxelizer.Material.MAIN_TRUNK, Voxelizer.Axis.X);
        Vector3 a = new Vector3(.5, 50.5, .5);
        Vector3 b = new Vector3(4.5, 50.5, .5);
        assertThrows(IllegalArgumentException.class,
                () -> Voxelizer.connectSpineForTest(acc, a, b, Voxelizer.Material.MAIN_TRUNK, Voxelizer.Axis.X));
    }

    @Test void extremeThicknessAndDensityStayWithinBudget() {
        TreeConfig config = TreeConfig.defaults()
                .withScale(TreeScale.GIANT)
                .withCanopy(CanopyShape.SPHERE, 2, 2)
                .withDna(1, 1, 1)
                .withTrunkThickness(2);
        var result = new TreeBuildTask().build(config, () -> false);
        assertTrue(result.blocks().size() <= Voxelizer.MAX_BLOCKS);
        assertTrue(result.blocks().values().stream().anyMatch(Voxelizer.Material::woody));
    }
}
