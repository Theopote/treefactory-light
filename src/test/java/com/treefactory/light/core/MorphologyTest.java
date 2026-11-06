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

    @Test void gravityInfluenceMakesBranchesLessUpwardAndAllowsDrooping() {
        TreeConfig config = TreeConfig.defaults();
        var noGravity = BranchMorphology.growthBias(config.withGravityInfluence(0));
        var normalGravity = BranchMorphology.growthBias(config.withGravityInfluence(.5));
        var fullGravity = BranchMorphology.growthBias(config.withGravityInfluence(1));
        assertTrue(noGravity.yBias() > normalGravity.yBias());
        assertTrue(normalGravity.yBias() > fullGravity.yBias());
        assertFalse(noGravity.allowDownward());
        assertFalse(normalGravity.allowDownward());
        assertTrue(fullGravity.allowDownward());
        assertThrows(IllegalArgumentException.class, () -> config.withGravityInfluence(-.01));
        assertThrows(IllegalArgumentException.class, () -> config.withGravityInfluence(1.01));
        assertThrows(IllegalArgumentException.class, () -> config.withGravityInfluence(Double.NaN));
    }

    @Test void defaultMorphologyKeepsDeterministicSkeleton() {
        TreeConfig config = TreeConfig.defaults();
        var colonizer = new SpaceColonizer();
        assertEquals(colonizer.generate(config), colonizer.generate(config));
    }

    @Test void gravityChangesActualBranchDirections() {
        SpaceColonizer colonizer = new SpaceColonizer();
        for (String id : List.of("builtin-oak", "builtin-birch", "builtin-cherry", "builtin-cypress")) {
            TreeConfig config = preset(id);
            int start = 1 + (int)(config.height() * .36);
            BranchDirections upward = branchDirections(colonizer.generate(config.withGravityInfluence(0)), start);
            BranchDirections normal = branchDirections(colonizer.generate(config.withGravityInfluence(.5)), start);
            BranchDirections drooping = branchDirections(colonizer.generate(config.withGravityInfluence(1)), start);
            assertTrue(upward.meanY() > normal.meanY() + .15, id);
            assertTrue(drooping.meanY() < normal.meanY() - .3, id);
            assertTrue(drooping.downwardFraction() > .3, id);
            assertTrue(drooping.downwardBends() >= 5, id);
        }
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
        TreeConfig config = TreeConfig.defaults().withMorphology(0.82, 0.71, 0.29).withGravityInfluence(0.64);
        var decoded = PresetStore.decode("id", PresetStore.encode("Morph", "Tester", config)).config();
        assertEquals(config.branchAngleBias(), decoded.branchAngleBias());
        assertEquals(config.branchDroop(), decoded.branchDroop());
        assertEquals(config.apicalDominance(), decoded.apicalDominance());
        assertEquals(config.gravityInfluence(), decoded.gravityInfluence());
    }

    private static TreeConfig preset(String id) {
        return BuiltInPresets.all().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().config();
    }

    private record BranchDirections(double meanY, double downwardFraction, int downwardBends) {}

    private static BranchDirections branchDirections(List<TreeNode> nodes, int start) {
        assertTrue(nodes.size() > start);
        double dy = 0;
        int down = 0, bends = 0;
        for (int i = start; i < nodes.size(); i++) {
            TreeNode node = nodes.get(i), parent = nodes.get(node.parent());
            double delta = node.position().y() - parent.position().y();
            dy += delta;
            if (delta < 0) {
                down++;
                if (parent.parent() >= start && parent.position().y() > nodes.get(parent.parent()).position().y()) bends++;
            }
        }
        return new BranchDirections(dy / (nodes.size() - start), (double) down / (nodes.size() - start), bends);
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
