package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.tree.Cell;
import com.treefactory.light.core.tree.TreePlacementValidator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreePlacementValidatorTest {
    @Test void rejectsBlockedRootEvenWhenOtherWoodIsPlaceable() {
        Map<Cell,Voxelizer.Material> blocks=Map.of(
                new Cell(0,0,0),Voxelizer.Material.MAIN_TRUNK,
                new Cell(0,1,0),Voxelizer.Material.MAIN_TRUNK);
        var result=TreePlacementValidator.validate(blocks, cell -> null, cell -> true,
                cell -> !cell.equals(new Cell(0,0,0)));
        assertFalse(result.ok());
        assertEquals("treefactory.target_protected",result.messageKey());
    }

    @Test void removesDisconnectedWoodAndItsIsolatedLeaves() {
        Cell root=new Cell(0,0,0), blocked=new Cell(0,1,0), branch=new Cell(0,2,0);
        Cell leaf=new Cell(0,3,0), attached=new Cell(1,0,0);
        Map<Cell,Voxelizer.Material> blocks=Map.of(
                root,Voxelizer.Material.MAIN_TRUNK, blocked,Voxelizer.Material.MAIN_TRUNK,
                branch,Voxelizer.Material.THIN_BRANCH, leaf,Voxelizer.Material.PRIMARY_LEAF,
                attached,Voxelizer.Material.PRIMARY_LEAF);
        var result=TreePlacementValidator.validate(blocks, cell -> null, cell -> true,
                cell -> !cell.equals(blocked));
        assertTrue(result.ok());
        assertEquals(Map.of(root,Voxelizer.Material.MAIN_TRUNK,attached,Voxelizer.Material.PRIMARY_LEAF),
                result.placeable());
    }

    @Test void rejectsWhenNoWoodyBlockCanBePlaced() {
        Map<Cell, Voxelizer.Material> blocks = Map.of(
                new Cell(0, 0, 0), Voxelizer.Material.MAIN_TRUNK,
                new Cell(1, 1, 0), Voxelizer.Material.PRIMARY_LEAF);
        var result = TreePlacementValidator.validate(blocks,
                cell -> cell.y() == 0 ? "treefactory.target_unloaded" : null,
                cell -> true, cell -> true);
        assertFalse(result.ok());
        assertEquals("treefactory.target_unloaded", result.messageKey());
    }

    @Test void skipsBlockedWoodyBlocksInsteadOfFailing() {
        Map<Cell, Voxelizer.Material> blocks = new LinkedHashMap<>();
        blocks.put(new Cell(0, 0, 0), Voxelizer.Material.MAIN_TRUNK);
        blocks.put(new Cell(0, 1, 0), Voxelizer.Material.MAIN_TRUNK);
        var result = TreePlacementValidator.validate(blocks, cell -> null, cell -> true, cell -> cell.y() == 0);
        assertTrue(result.ok());
        assertEquals(1, result.placeable().size());
    }

    @Test void allowsLeafSkipsWhileKeepingWoodyBlocks() {
        Map<Cell, Voxelizer.Material> blocks = new LinkedHashMap<>();
        blocks.put(new Cell(0, 0, 0), Voxelizer.Material.MAIN_TRUNK);
        blocks.put(new Cell(2, 2, 0), Voxelizer.Material.PRIMARY_LEAF);
        var result = TreePlacementValidator.validate(blocks,
                cell -> null,
                cell -> cell.x() == 0,
                cell -> cell.x() == 0);
        assertTrue(result.ok());
        assertEquals(Voxelizer.Material.MAIN_TRUNK, result.placeable().get(new Cell(0, 0, 0)));
        assertFalse(result.placeable().containsKey(new Cell(2, 2, 0)));
    }
}
