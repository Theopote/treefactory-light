package com.treefactory.light.core.config;

import java.util.Objects;

/** Immutable snapshots can safely cross the worker/main-thread boundary. */
public record TreeConfig(TreeType treeType, long seed,
                         double crookedness, double spread, double fullness,
                         CanopyShape canopyShape, double canopyScale, double attractionDensity,
                         double pipeExponent, BlockPalette blockPalette, double trunkHeight, double trunkThickness,
                         double crownStretch, int crownLayers,
                         double branchAngleBias, double branchDroop, double apicalDominance,
                         double gravityInfluence, double crownBase) {
    public static final double MIN_HEIGHT=6, MAX_HEIGHT=48, MIN_THICKNESS=.5, MAX_THICKNESS=2.0, MIN_STRETCH=.6, MAX_STRETCH=2.0;
    public static final double MIN_CANOPY_SCALE=.75, MAX_CANOPY_SCALE=2.0, MIN_DENSITY=.5, MAX_DENSITY=2.0,
            MIN_PIPE_EXPONENT=2.5, MAX_PIPE_EXPONENT=3.0;
    public static final int MIN_LAYERS=3, MAX_LAYERS=6;
    public static final double DEFAULT_BRANCH_ANGLE_BIAS=.5, DEFAULT_BRANCH_DROOP=0, DEFAULT_APICAL_DOMINANCE=.5,
            DEFAULT_GRAVITY_INFLUENCE=.5, MIN_CROWN_BASE=.1, MAX_CROWN_BASE=.7, DEFAULT_CROWN_BASE=.3;
    public TreeConfig {
        Objects.requireNonNull(treeType);
        unit(crookedness); unit(spread); unit(fullness);
        unit(branchAngleBias); unit(branchDroop); unit(apicalDominance); unit(gravityInfluence);
        Objects.requireNonNull(canopyShape); Objects.requireNonNull(blockPalette);
        range(trunkHeight,MIN_HEIGHT,MAX_HEIGHT); range(canopyScale,MIN_CANOPY_SCALE,MAX_CANOPY_SCALE); range(attractionDensity,MIN_DENSITY,MAX_DENSITY); range(pipeExponent,MIN_PIPE_EXPONENT,MAX_PIPE_EXPONENT); range(trunkThickness,MIN_THICKNESS,MAX_THICKNESS);
        range(crownStretch,MIN_STRETCH,MAX_STRETCH); range(crownLayers,MIN_LAYERS,MAX_LAYERS);
        range(crownBase,MIN_CROWN_BASE,MAX_CROWN_BASE);
    }
    public TreeConfig(TreeType treeType, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette, double trunkHeight, double trunkThickness,
                      double crownStretch, int crownLayers,
                      double branchAngleBias, double branchDroop, double apicalDominance,
                      double gravityInfluence, double crownBase) {
        this(treeType,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,
                blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,
                apicalDominance,gravityInfluence,crownBase);
        Objects.requireNonNull(scale);
    }
    public TreeConfig(TreeType treeType, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette, double trunkHeight, double trunkThickness,
                      double crownStretch, int crownLayers,
                      double branchAngleBias, double branchDroop, double apicalDominance, double gravityInfluence) {
        this(treeType,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,
                blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,
                apicalDominance,gravityInfluence,DEFAULT_CROWN_BASE);
    }
    public TreeConfig(TreeType treeType, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette, double trunkHeight, double trunkThickness,
                      double crownStretch, int crownLayers,
                      double branchAngleBias, double branchDroop, double apicalDominance) {
        this(treeType,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,
                blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,
                apicalDominance,DEFAULT_GRAVITY_INFLUENCE);
    }
    private static void unit(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Parameter values must be finite and between 0 and 1");
    }
    private static void range(double value,double min,double max) {
        if (!Double.isFinite(value) || value<min || value>max) throw new IllegalArgumentException("Value outside allowed range");
    }
    public TreeConfig(TreeType treeType, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette, double trunkHeight) {
        this(treeType,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,1.0,1.0,3,
                DEFAULT_BRANCH_ANGLE_BIAS,DEFAULT_BRANCH_DROOP,DEFAULT_APICAL_DOMINANCE);
    }
    public TreeConfig(TreeType treeType, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette, double trunkHeight, double trunkThickness) {
        this(treeType,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,trunkThickness,1.0,3,
                DEFAULT_BRANCH_ANGLE_BIAS,DEFAULT_BRANCH_DROOP,DEFAULT_APICAL_DOMINANCE);
    }
    /** Trunk height defaults to the height of the given size tier. */
    public TreeConfig(TreeType treeType, TreeScale scale, long seed,
                      double crookedness, double spread, double fullness,
                      CanopyShape canopyShape, double canopyScale, double attractionDensity,
                      double pipeExponent, BlockPalette blockPalette) {
        this(treeType,scale,seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,scale.height);
    }
    /** Compatibility with version 1.0 presets and existing integrations. */
    public TreeConfig(TreeType treeType,TreeScale scale,long seed,double crookedness,double spread,double fullness,
                      String logBlock,String leavesBlock) {
        this(treeType,scale,seed,crookedness,spread,fullness,CanopyShape.forPreset(treeType),1.25,1.25,2.75,
                BlockPalette.defaults(treeType==TreeType.PINE?"spruce":"oak").withMainAndPrimary(logBlock,leavesBlock));
    }
    public static TreeConfig defaults() {
        return new TreeConfig(TreeType.OAK, TreeScale.MEDIUM, 8848123L,
                .35, .6, .8, "minecraft:oak_log", "minecraft:oak_leaves");
    }
    public TreeConfig withParameters(double crooked, double spread, double full) {
        return new TreeConfig(treeType, scale(), seed, crooked, spread, full, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withScale(TreeScale size) {
        return new TreeConfig(treeType, size, seed, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,size.height,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withTrunkHeight(double blocks) {
        return new TreeConfig(treeType, nearestScale(blocks), seed, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,blocks,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    private static TreeScale nearestScale(double blocks) {
        TreeScale best=TreeScale.MEDIUM;
        for(TreeScale s:TreeScale.values()) if(Math.abs(s.height-blocks)<Math.abs(best.height-blocks)) best=s;
        return best;
    }
    public TreeScale scale() { return nearestScale(trunkHeight); }
    public TreeConfig withSeed(long value) {
        return new TreeConfig(treeType, scale(), value, crookedness, spread, fullness, canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withTreeType(TreeType type) {
        String wood = type == TreeType.PINE ? "spruce" : "oak";
        return new TreeConfig(type,scale(),seed,crookedness,spread,fullness,CanopyShape.forPreset(type),canopyScale,attractionDensity,pipeExponent,BlockPalette.defaults(wood),trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withPalette(String log, String leaves) {
        return withBlockPalette(blockPalette.withMainAndPrimary(log,leaves));
    }
    public String logBlock() { return blockPalette.mainTrunk(); }
    public String leavesBlock() { return blockPalette.primaryLeaf(); }
    public TreeConfig withCanopy(CanopyShape shape,double canopyScale,double density) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,shape,canopyScale,density,pipeExponent,blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withPipeExponent(double exponent) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,exponent,blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withTrunkThickness(double value) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,value,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withCrownStretch(double value) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,trunkThickness,value,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withCrownLayers(int value) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,trunkThickness,crownStretch,value,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withBlockPalette(BlockPalette palette) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,palette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withMorphology(double branchAngleBias, double branchDroop, double apicalDominance) {
        return withMorphology(branchAngleBias,branchDroop,apicalDominance,gravityInfluence);
    }
    public TreeConfig withMorphology(double branchAngleBias, double branchDroop, double apicalDominance, double gravityInfluence) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,crownBase);
    }
    public TreeConfig withGravityInfluence(double value) {
        return withMorphology(branchAngleBias,branchDroop,apicalDominance,value);
    }
    public TreeConfig withCrownBase(double value) {
        return new TreeConfig(treeType,scale(),seed,crookedness,spread,fullness,canopyShape,canopyScale,attractionDensity,pipeExponent,blockPalette,trunkHeight,trunkThickness,crownStretch,crownLayers,branchAngleBias,branchDroop,apicalDominance,gravityInfluence,value);
    }
    /** The crown position is measured from the placement point, as a fraction of trunk height. */
    public double crownBaseHeight() { return trunkHeight*crownBase; }
    public double crownVerticalScale() {
        return trunkHeight*(canopyShape==CanopyShape.SPHERE ? .448 : .38)*canopyScale*crownStretch;
    }
    public double crownCenterHeight() {
        return crownBaseHeight() - canopyShape.crownMinY(crownLayers)*crownVerticalScale();
    }
    public int trunkSegments() { return (int)Math.ceil(trunkHeight); }
}
