package com.treefactory.light.core.config;

import java.util.Objects;

/** Block identifiers and canopy probabilities, independent of Minecraft classes. */
public record BlockPalette(String mainTrunk, String thickBranch, String thinBranch,
                           String primaryLeaf, String secondaryLeaf, String accentBlock,
                           double primaryWeight, double secondaryWeight, double accentWeight) {
    public BlockPalette {
        for (String id : new String[]{mainTrunk, thickBranch, thinBranch, primaryLeaf, secondaryLeaf, accentBlock}) {
            Objects.requireNonNull(id);
            if (id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid block identifier: " + id);
        }
        if (!Double.isFinite(primaryWeight) || !Double.isFinite(secondaryWeight) || !Double.isFinite(accentWeight)
                || primaryWeight < 0 || secondaryWeight < 0 || accentWeight < 0
                || Math.abs(primaryWeight + secondaryWeight + accentWeight - 1) > 1e-6)
            throw new IllegalArgumentException("Canopy weights must be nonnegative and sum to one");
    }
    public static BlockPalette defaults(String wood) {
        return new BlockPalette("minecraft:"+wood+"_log", "minecraft:stripped_"+wood+"_log",
                "minecraft:"+wood+"_planks", "minecraft:"+wood+"_leaves",
                "minecraft:flowering_azalea_leaves", "minecraft:moss_block", .8, .15, .05);
    }
    public BlockPalette withMainAndPrimary(String trunk, String leaf) {
        return new BlockPalette(trunk,thickBranch,thinBranch,leaf,secondaryLeaf,accentBlock,
                primaryWeight,secondaryWeight,accentWeight);
    }
    public BlockPalette withBlocks(String main,String thick,String thin,String primary,String secondary,String accent) {
        return new BlockPalette(main,thick,thin,primary,secondary,accent,primaryWeight,secondaryWeight,accentWeight);
    }
    public BlockPalette withWeights(double primary,double secondary,double accent) {
        return new BlockPalette(mainTrunk,thickBranch,thinBranch,primaryLeaf,secondaryLeaf,accentBlock,
                primary,secondary,accent);
    }
}
