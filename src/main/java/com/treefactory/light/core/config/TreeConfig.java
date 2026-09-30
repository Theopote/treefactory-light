package com.treefactory.light.core.config;

import java.util.Objects;

/** Immutable snapshots can safely cross the worker/main-thread boundary. */
public record TreeConfig(TreePreset preset, TreeScale scale, long seed,
                         double crookedness, double spread, double fullness,
                         CanopyShape canopyShape, double canopyScale, double attractionDensity,
                         double pipeExponent, BlockPalette blockPalette) {
    public TreeConfig {
        Objects.requireNonNull(preset);
        Objects.requireNonNull(scale);
        unit(crookedness); unit(spread); unit(fullness);
        Objects.requireNonNull(canopyShape); Objects.requireNonNull(blockPalette);
        range(canopyScale,.75,2.0); range(attractionDensity,.5,2.0); range(pipeExponent,2.5,3.0);
    }
    private static void unit(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("DNA values must be finite and between 0 and 1");
    }
    private static void range(double value,double min,double max) {
        if (!Double.isFinite(value) || value<min || value>max) throw new IllegalArgumentException("Value outside allowed range");
    }
    /** Compatibility with version 1.0 presets and existing integrations. */
    public TreeConfig(TreePreset preset,TreeScale scale,long seed,double crookedness,double spread,double fullness,
                      String logBlock,String leavesBlock) {
        this(preset,scale,seed,crookedness,spread,fullness,CanopyShape.forPreset(preset),1.25,1.25,2.75,
                BlockPalette.defaults(preset==TreePreset.PINE?"spruce":"oak").withMainAndPrimary(logBlock,leavesBlock));
    }
    public static TreeConfig defaults() {
        return new TreeConfig(TreePreset.OAK, TreeScale.MEDIUM, 8848123L,
                .35, .6, .8, "minecraft:oak_log", "minecraft:oak_leaves");
    }
    public TreeConfig withDna(double crooked, double spread, double full) {
        return new TreeConfig(preset, scale, seed, crooked, spread, full, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette);
    }
    public TreeConfig withScale(TreeScale size) {
        return new TreeConfig(preset, size, seed, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette);
    }
    public TreeConfig withSeed(long value) {
        return new TreeConfig(preset, scale, value, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette);
    }
    public TreeConfig withPreset(TreePreset type) {
        String wood = type == TreePreset.PINE ? "spruce" : "oak";
        return new TreeConfig(type,scale,seed,crookedness,spread,fullness,CanopyShape.forPreset(type),canopyScale,attractionDensity,pipeExponent,BlockPalette.defaults(wood));
    }
    public TreeConfig withPalette(String log, String leaves) {
        return withBlockPalette(blockPalette.withMainAndPrimary(log,leaves));
    }
    public String logBlock() { return blockPalette.mainTrunk(); }
    public String leavesBlock() { return blockPalette.primaryLeaf(); }
    public TreeConfig withCanopy(CanopyShape shape,double scale,double density) {
        return new TreeConfig(preset,this.scale,seed,crookedness,spread,fullness,shape,scale,density,pipeExponent,blockPalette);
    }
    public TreeConfig withPipeExponent(double exponent) {
        return new TreeConfig(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,exponent,blockPalette);
    }
    public TreeConfig withBlockPalette(BlockPalette palette) {
        return new TreeConfig(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,palette);
    }
}
