package com.treefactory.light.core.config;

import java.util.Objects;

/** Immutable snapshots can safely cross the worker/main-thread boundary. */
public record TreeConfig(TreePreset preset, TreeScale scale, long seed,
                         double crookedness, double spread, double fullness,
                         CanopyShape canopyShape, double canopyScale, double attractionDensity,
                         double pipeExponent, BlockPalette blockPalette, double height, double trunkThickness,
                         double crownStretch, int crownLayers) {
    public static final double MIN_HEIGHT=6, MAX_HEIGHT=48, MIN_THICKNESS=.5, MAX_THICKNESS=2.0, MIN_STRETCH=.6, MAX_STRETCH=2.0;
    public static final int MIN_LAYERS=3, MAX_LAYERS=6;
    public TreeConfig {
        Objects.requireNonNull(preset);
        Objects.requireNonNull(scale);
        unit(crookedness); unit(spread); unit(fullness);
        Objects.requireNonNull(canopyShape); Objects.requireNonNull(blockPalette);
        range(height,MIN_HEIGHT,MAX_HEIGHT); range(canopyScale,.75,2.0); range(attractionDensity,.5,2.0); range(pipeExponent,2.5,3.0); range(trunkThickness,MIN_THICKNESS,MAX_THICKNESS);
        range(crownStretch,MIN_STRETCH,MAX_STRETCH); range(crownLayers,MIN_LAYERS,MAX_LAYERS);
    }
    private static void unit(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("DNA values must be finite and between 0 and 1");
    }
    private static void range(double value,double min,double max) {
        if (!Double.isFinite(value) || value<min || value>max) throw new IllegalArgumentException("Value outside allowed range");
    }
    public TreeConfig(TreePreset preset, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette, double height) {
        this(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,height,1.0,1.0,3);
    }
    public TreeConfig(TreePreset preset, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette, double height, double trunkThickness) {
        this(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,height,trunkThickness,1.0,3);
    }
    /** Tree height defaults to the height of the given size tier. */
    public TreeConfig(TreePreset preset, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette) {
        this(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,scale.height);
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
        return new TreeConfig(preset, scale, seed, crooked, spread, full, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,height,trunkThickness,crownStretch,crownLayers);
    }
    public TreeConfig withScale(TreeScale size) {
        return new TreeConfig(preset, size, seed, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,size.height,trunkThickness,crownStretch,crownLayers);
    }
    public TreeConfig withHeight(double blocks) {
        return new TreeConfig(preset, nearestScale(blocks), seed, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,blocks,trunkThickness,crownStretch,crownLayers);
    }
    private static TreeScale nearestScale(double blocks) {
        TreeScale best=TreeScale.MEDIUM;
        for(TreeScale s:TreeScale.values()) if(Math.abs(s.height-blocks)<Math.abs(best.height-blocks)) best=s;
        return best;
    }
    public TreeConfig withSeed(long value) {
        return new TreeConfig(preset, scale, value, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,height,trunkThickness,crownStretch,crownLayers);
    }
    public TreeConfig withPreset(TreePreset type) {
        String wood = type == TreePreset.PINE ? "spruce" : "oak";
        return new TreeConfig(type,scale,seed,crookedness,spread,fullness,CanopyShape.forPreset(type),canopyScale,attractionDensity,pipeExponent,BlockPalette.defaults(wood),height,trunkThickness,crownStretch,crownLayers);
    }
    public TreeConfig withPalette(String log, String leaves) {
        return withBlockPalette(blockPalette.withMainAndPrimary(log,leaves));
    }
    public String logBlock() { return blockPalette.mainTrunk(); }
    public String leavesBlock() { return blockPalette.primaryLeaf(); }
    public TreeConfig withCanopy(CanopyShape shape,double scale,double density) {
        return new TreeConfig(preset,this.scale,seed,crookedness,spread,fullness,shape,scale,density,pipeExponent,blockPalette,height,trunkThickness,crownStretch,crownLayers);
    }
    public TreeConfig withPipeExponent(double exponent) {
        return new TreeConfig(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,exponent,blockPalette,height,trunkThickness,crownStretch,crownLayers);
    }
    public TreeConfig withTrunkThickness(double value) {
        return new TreeConfig(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,height,value,crownStretch,crownLayers);
    }
    public TreeConfig withCrownStretch(double value) {
        return new TreeConfig(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,height,trunkThickness,value,crownLayers);
    }
    public TreeConfig withCrownLayers(int value) {
        return new TreeConfig(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,height,trunkThickness,crownStretch,value);
    }
    public TreeConfig withBlockPalette(BlockPalette palette) {
        return new TreeConfig(preset,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,palette,height,trunkThickness,crownStretch,crownLayers);
    }
}
