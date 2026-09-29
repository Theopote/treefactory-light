package com.treefactory.light.core.config;

import java.util.Objects;

/** Immutable snapshots can safely cross the worker/main-thread boundary. */
public record TreeConfig(TreePreset preset, TreeScale scale, long seed,
                         double crookedness, double spread, double fullness,
                         String logBlock, String leavesBlock) {
    public TreeConfig {
        Objects.requireNonNull(preset);
        Objects.requireNonNull(scale);
        unit(crookedness); unit(spread); unit(fullness);
        checkId(logBlock); checkId(leavesBlock);
    }
    private static void unit(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("DNA values must be finite and between 0 and 1");
    }
    private static void checkId(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid block identifier");
    }
    public static TreeConfig defaults() {
        return new TreeConfig(TreePreset.OAK, TreeScale.MEDIUM, 8848123L,
                .35, .6, .8, "minecraft:oak_log", "minecraft:oak_leaves");
    }
    public TreeConfig withDna(double crooked, double spread, double full) {
        return new TreeConfig(preset, scale, seed, crooked, spread, full, logBlock, leavesBlock);
    }
    public TreeConfig withScale(TreeScale size) {
        return new TreeConfig(preset, size, seed, crookedness, spread, fullness, logBlock, leavesBlock);
    }
    public TreeConfig withSeed(long value) {
        return new TreeConfig(preset, scale, value, crookedness, spread, fullness, logBlock, leavesBlock);
    }
    public TreeConfig withPreset(TreePreset type) {
        String wood = type == TreePreset.PINE ? "spruce" : "oak";
        return new TreeConfig(type, scale, seed, crookedness, spread, fullness,
                "minecraft:" + wood + "_log", "minecraft:" + wood + "_leaves");
    }
    public TreeConfig withPalette(String log, String leaves) {
        return new TreeConfig(preset, scale, seed, crookedness, spread, fullness, log, leaves);
    }
}
