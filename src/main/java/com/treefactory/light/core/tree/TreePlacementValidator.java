package com.treefactory.light.core.tree;

import com.treefactory.light.core.algorithm.Voxelizer;
import java.util.*;
import java.util.function.Predicate;

/** Pre-placement filter: blocked cells are skipped; placement fails only if no woody block can be placed. */
public final class TreePlacementValidator {
    public record Result(boolean ok, String messageKey, Map<Cell, Voxelizer.Material> placeable) {
        public static Result ok(Map<Cell, Voxelizer.Material> placeable) {
            return new Result(true, null, placeable);
        }
        public static Result fail(String messageKey) {
            return new Result(false, messageKey, Map.of());
        }
    }

    private TreePlacementValidator() {}

    /**
     * @param accessibility returns a message key when a world position is inaccessible, else {@code null}
     * @param canInteract whether the player may modify the position
     * @param replaceable whether the existing block may be replaced
     */
    public static Result validate(Map<Cell, Voxelizer.Material> blocks,
            java.util.function.Function<Cell, String> accessibility,
            Predicate<Cell> canInteract, Predicate<Cell> replaceable) {
        Map<Cell, Voxelizer.Material> placeable = new LinkedHashMap<>();
        String firstBlock = null;
        boolean anyWoody = false;
        for (var entry : blocks.entrySet()) {
            Cell cell = entry.getKey();
            Voxelizer.Material material = entry.getValue();
            String blocked = accessibility.apply(cell);
            // Occupied, protected or unloaded cells are simply skipped; the tree grows around obstacles.
            if (blocked == null && canInteract.test(cell) && replaceable.test(cell)) placeable.put(cell, material);
            else if (material.woody() && firstBlock == null)
                firstBlock = blocked != null ? blocked : "treefactory.target_protected";
            anyWoody |= material.woody();
        }
        if (placeable.values().stream().noneMatch(Voxelizer.Material::woody))
            return Result.fail(anyWoody && firstBlock != null ? firstBlock : "treefactory.tree_incomplete");
        return Result.ok(placeable);
    }
}
