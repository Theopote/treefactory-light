package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.BranchMorphology;
import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphologyTest {
    @Test void defaultMorphologyMatchesLegacyGrowthBias() {
        var bias = BranchMorphology.growthBias(TreeConfig.defaults());
        assertEquals(0.14, bias.yBias(), 1e-9);
        assertEquals(1.0, bias.inertiaWeight(), 1e-9);
        assertFalse(bias.allowDownward());
    }

    @Test void defaultMorphologyKeepsDeterministicSkeleton() {
        TreeConfig config = TreeConfig.defaults();
        var colonizer = new SpaceColonizer();
        assertEquals(colonizer.generate(config), colonizer.generate(config));
    }

    @Test void builtInPresetsExpressDistinctBranchStrategies() {
        TreeConfig cypress = preset("builtin-cypress");
        TreeConfig acacia = preset("builtin-acacia");
        TreeConfig mangrove = preset("builtin-mangrove");
        SpaceColonizer colonizer = new SpaceColonizer();
        double cypressLateral = averageLateralRatio(colonizer.generate(cypress));
        double acaciaLateral = averageLateralRatio(colonizer.generate(acacia));
        assertTrue(acaciaLateral > cypressLateral + 0.05);
        assertTrue(countPropRoots(colonizer.generate(mangrove)) >= 4);
        assertTrue(cypress.apicalDominance() > acacia.apicalDominance());
    }

    @Test void morphologyRoundTripsThroughPresetStore() {
        TreeConfig config = TreeConfig.defaults().withMorphology(0.82, 0.71, 0.29);
        var decoded = PresetStore.decode("id", PresetStore.encode("Morph", "Tester", config)).config();
        assertEquals(config.branchAngleBias(), decoded.branchAngleBias());
        assertEquals(config.branchDroop(), decoded.branchDroop());
        assertEquals(config.apicalDominance(), decoded.apicalDominance());
    }

    private static TreeConfig preset(String id) {
        return BuiltInPresets.all().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().config();
    }

    private static double averageLateralRatio(List<TreeNode> nodes) {
        double sum = 0;
        int count = 0;
        for (int i = 1; i < nodes.size(); i++) {
            TreeNode node = nodes.get(i), parent = nodes.get(node.parent());
            double dy = Math.abs(node.position().y() - parent.position().y());
            double lateral = Math.hypot(node.position().x() - parent.position().x(), node.position().z() - parent.position().z());
            if (dy < 1e-6) continue;
            sum += lateral / dy;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static int countPropRoots(List<TreeNode> nodes) {
        int roots = 0;
        for (int i = 1; i < nodes.size(); i++) {
            TreeNode node = nodes.get(i), parent = nodes.get(node.parent());
            if (node.position().y() >= parent.position().y()) continue;
            if (parent.position().y() > 4) continue;
            if (Math.hypot(node.position().x() - parent.position().x(), node.position().z() - parent.position().z()) > 0.4) roots++;
        }
        return roots;
    }
}
