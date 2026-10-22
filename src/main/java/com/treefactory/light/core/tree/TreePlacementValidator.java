package com.treefactory.light.core.tree;

import com.treefactory.light.core.algorithm.Voxelizer;
import java.util.*;
import java.util.function.Predicate;

/** Pre-placement checks: woody structure must be complete; leaves may be skipped. */
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
        for (var entry : blocks.entrySet()) {
            Cell cell = entry.getKey();
            Voxelizer.Material material = entry.getValue();
            String blocked = accessibility.apply(cell);
            boolean allowed = blocked == null && canInteract.test(cell) && replaceable.test(cell);
            if (material.woody()) {
                if (!allowed) return Result.fail(blocked != null ? blocked : "treefactory.target_protected");
            } else if (allowed) {
                placeable.put(cell, material);
            }
        }
        for (var entry : blocks.entrySet()) {
            if (entry.getValue().woody()) placeable.put(entry.getKey(), entry.getValue());
        }
        if (placeable.values().stream().noneMatch(Voxelizer.Material::woody)) return Result.fail("treefactory.tree_incomplete");
        return Result.ok(placeable);
    }
}
