package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrownPositionTest {
    @Test void crownBaseMatchesShapeBoundary() {
        for (CanopyShape shape : CanopyShape.values()) {
            double base = shape.crownMinY(3);
            boolean foundAbove = false;
            for (int x = -50; x <= 50; x++) for (int z = -50; z <= 50; z++) {
                assertTrue(shape.field(x / 50.0, base - .001, z / 50.0, 3) > 0, shape.name());
                if (shape.field(x / 50.0, base + .001, z / 50.0, 3) <= 0) foundAbove = true;
            }
            assertTrue(foundAbove, shape.name());
        }
    }

    @Test void shiftsCrownAndBranchBaseForEveryShape() {
        SpaceColonizer colonizer = new SpaceColonizer();
        for (CanopyShape shape : CanopyShape.values()) {
            TreeConfig config = TreeConfig.defaults().withHeight(24).withCanopy(shape, 1.25, 1.25);
            double[] bottoms = new double[3], tops = new double[3];
            int index = 0;
            for (double offset : new double[] {0, .25, .5}) {
                TreeConfig moved = config.withCrownBase(offset);
                assertEquals(offset * moved.height(),
                        moved.crownCenterHeight() + shape.crownMinY(moved.crownLayers()) * moved.crownVerticalScale(), 1e-9, shape.name());
                List<TreeNode> nodes = colonizer.generate(moved);
                int trunkNodes = 1 + (int)moved.initialTrunkHeight();
                assertTrue(nodes.size() > trunkNodes, shape.name());
                bottoms[index] = nodes.subList(trunkNodes, nodes.size()).stream().mapToDouble(n -> n.position().y()).min().orElseThrow();
                tops[index] = nodes.subList(trunkNodes, nodes.size()).stream().mapToDouble(n -> n.position().y()).max().orElseThrow();
                index++;
            }
            assertTrue(bottoms[0] < bottoms[1] - 2 && bottoms[1] < bottoms[2] - 2, shape.name());
            assertTrue(tops[0] < tops[1] - 3 && tops[1] < tops[2] - 3, shape.name());
            assertEquals(tops[0] - bottoms[0], tops[2] - bottoms[2], 4, shape.name());
        }
    }

    @Test void positionIsValidatedAndSurvivesOtherChanges() {
        TreeConfig config = TreeConfig.defaults().withCrownBase(.15);
        assertThrows(IllegalArgumentException.class, () -> config.withCrownBase(-.01));
        assertThrows(IllegalArgumentException.class, () -> config.withCrownBase(.51));
        assertThrows(IllegalArgumentException.class, () -> config.withCrownBase(Double.NaN));
        TreeConfig updated = config.withSeed(1).withHeight(32).withScale(TreeScale.LARGE)
                .withDna(.2, .4, .6).withCanopy(CanopyShape.SPHERE, 1.1, 1)
                .withTrunkThickness(1.2).withCrownStretch(1.3).withCrownLayers(4)
                .withPipeExponent(2.6).withGravityInfluence(.8)
                .withMorphology(.1, .2, .3).withBlockPalette(config.blockPalette()).withPreset(TreePreset.PINE);
        assertEquals(.15, updated.crownBase());
        assertEquals(config, PresetStore.decode("id", PresetStore.encode("Tree", "Player", config)).config());
    }

    @Test void legacyPresetConvertsOriginalCrownPosition() {
        var root = com.google.gson.JsonParser.parseString(PresetStore.encode("Tree", "Player",
                TreeConfig.defaults().withCrownBase(.2))).getAsJsonObject();
        root.addProperty("version", "1.3");
        root.getAsJsonObject("canopy").remove("crown_offset");
        TreeConfig decoded = PresetStore.decode("id", root.toString()).config();
        assertEquals(.76 - .38 * 1.25, decoded.crownBase(), 1e-9);
    }

    @Test void legacyOffsetsAreConvertedAndClampedForDifferentShapes() {
        for (CanopyShape shape : CanopyShape.values()) {
            TreeConfig config = TreeConfig.defaults().withHeight(24).withCanopy(shape, 1.25, 1.25).withCrownStretch(1.4);
            for (double offset : new double[] {-.2, 0, .2}) {
                var root = com.google.gson.JsonParser.parseString(PresetStore.encode("Tree", "Player", config)).getAsJsonObject();
                root.addProperty("version", "1.4");
                var canopy = root.getAsJsonObject("canopy");
                canopy.remove("crown_base");
                canopy.addProperty("crown_offset", offset);
                double height = config.height(), vertical = config.crownVerticalScale();
                double oldCenter = shape == CanopyShape.SPHERE ? Math.max(.76 * height, vertical + 2)
                        : .76 * height - shape.minY(config.crownLayers()) * height * .38
                        * config.canopyScale() * (config.crownStretch() - 1);
                oldCenter += Math.max(1, height * (.36 + offset)) - height * .36;
                double expected = Math.clamp((oldCenter + shape.crownMinY(config.crownLayers()) * vertical) / height, 0, .5);
                assertEquals(expected, PresetStore.decode("legacy", root.toString()).config().crownBase(), 1e-9, shape.name());
            }
        }
    }
}
