package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.config.TreePreset;

/** Maps internal morphology sliders to branch growth vectors. */
public final class BranchMorphology {
    private BranchMorphology() {}

    public record GrowthBias(double yBias, double inertiaWeight, boolean allowDownward) {}

    public static GrowthBias growthBias(TreeConfig config) {
        boolean willow = config.preset() == TreePreset.WILLOW;
        double yBias = 0.14 + 0.28 * (TreeConfig.DEFAULT_BRANCH_ANGLE_BIAS - config.branchAngleBias())
                - config.branchDroop() * 0.35;
        if (willow) yBias -= 0.05;
        double inertia = 0.6 + config.apicalDominance() * 0.8;
        boolean allowDown = willow || config.branchDroop() > 0.4;
        return new GrowthBias(yBias, inertia, allowDown);
    }

    public static boolean growsPropRoots(TreeConfig config) {
        return config.branchDroop() >= 0.65 && config.spread() >= 0.55;
    }
}
