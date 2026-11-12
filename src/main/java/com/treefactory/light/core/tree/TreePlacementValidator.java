package com.treefactory.light.core.tree;

import com.treefactory.light.core.algorithm.Voxelizer;
import java.util.*;
import java.util.function.Predicate;

/** Filters blocked cells and, when wood is blocked, keeps only the rooted tree. */
public final class TreePlacementValidator {
    public record Result(boolean ok, String messageKey, Map<Cell, Voxelizer.Material> placeable) {
        public static Result ok(Map<Cell, Voxelizer.Material> placeable) {
            return new Result(true, null, placeable);
        }
        public static Result fail(String messageKey) {
            return new Result(false, messageKey, Map.of());
        }
    }

    private static final double MIN_WOOD_RATIO=.5;

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
        boolean anyWoody = false, blockedWood = false;
        Cell origin=new Cell(0,0,0);
        String originBlock=null;
        for (var entry : blocks.entrySet()) {
            Cell cell = entry.getKey();
            Voxelizer.Material material = entry.getValue();
            String blocked = accessibility.apply(cell);
            // Occupied, protected or unloaded cells are simply skipped; the tree grows around obstacles.
            if (blocked == null && canInteract.test(cell) && replaceable.test(cell)) placeable.put(cell, material);
            else if (material.woody()) {
                blockedWood=true;
                String reason=blocked != null ? blocked : "treefactory.target_protected";
                if (firstBlock == null) firstBlock=reason;
                if (cell.equals(origin)) originBlock=reason;
            }
            anyWoody |= material.woody();
        }
        if (placeable.values().stream().noneMatch(Voxelizer.Material::woody))
            return Result.fail(anyWoody && firstBlock != null ? firstBlock : "treefactory.tree_incomplete");
        if (blockedWood) {
            Voxelizer.Material root=placeable.get(origin);
            if (root == null || !root.woody())
                return Result.fail(originBlock != null ? originBlock : "treefactory.tree_incomplete");
            Set<Cell> reachable=new HashSet<>();
            ArrayDeque<Cell> queue=new ArrayDeque<>();
            reachable.add(origin); queue.add(origin);
            flood(queue, reachable, placeable, true);
            queue.addAll(reachable);
            flood(queue, reachable, placeable, false);
            placeable.keySet().removeIf(cell -> !reachable.contains(cell));
            long totalWood=blocks.values().stream().filter(Voxelizer.Material::woody).count();
            long keptWood=placeable.values().stream().filter(Voxelizer.Material::woody).count();
            if (keptWood < totalWood * MIN_WOOD_RATIO) return Result.fail("treefactory.tree_mostly_blocked");
        }
        return Result.ok(placeable);
    }

    private static void flood(ArrayDeque<Cell> queue, Set<Cell> reachable,
            Map<Cell,Voxelizer.Material> cells, boolean wood) {
        int[][] offsets={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty()) {
            Cell pos=queue.removeFirst();
            for (int[] offset : offsets) {
                Cell next=new Cell(pos.x()+offset[0],pos.y()+offset[1],pos.z()+offset[2]);
                Voxelizer.Material material=cells.get(next);
                if (material != null && material.woody()==wood && reachable.add(next)) queue.addLast(next);
            }
        }
    }
}
