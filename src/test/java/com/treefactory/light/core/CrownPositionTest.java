package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrownPositionTest {
    @Test void shiftsCrownAndBranchBaseForEveryShape() {
        SpaceColonizer colonizer = new SpaceColonizer();
        for (CanopyShape shape : CanopyShape.values()) {
            TreeConfig config = TreeConfig.defaults().withHeight(24).withCanopy(shape, 1.25, 1.25);
            double[] bottoms = new double[3], tops = new double[3];
            int index = 0;
            for (double offset : new double[] {-.2, 0, .2}) {
                TreeConfig moved = config.withCrownOffset(offset);
                List<TreeNode> nodes = colonizer.generate(moved);
                int trunkNodes = 1 + (int)moved.initialTrunkHeight();
                assertTrue(nodes.size() > trunkNodes, shape.name());
                bottoms[index] = nodes.subList(trunkNodes, nodes.size()).stream().mapToDouble(n -> n.position().y()).min().orElseThrow();
                tops[index] = nodes.subList(trunkNodes, nodes.size()).stream().mapToDouble(n -> n.position().y()).max().orElseThrow();
                index++;
            }
            assertTrue(bottoms[0] < bottoms[1] - 3 && bottoms[1] < bottoms[2] - 3, shape.name());
            assertTrue(tops[0] < tops[1] - 3 && tops[1] < tops[2] - 3, shape.name());
            assertEquals(tops[0] - bottoms[0], tops[2] - bottoms[2], 3, shape.name());
        }
    }

    @Test void positionIsValidatedAndSurvivesOtherChanges() {
        TreeConfig config = TreeConfig.defaults().withCrownOffset(.15);
        assertThrows(IllegalArgumentException.class, () -> config.withCrownOffset(-.21));
        assertThrows(IllegalArgumentException.class, () -> config.withCrownOffset(.21));
        assertThrows(IllegalArgumentException.class, () -> config.withCrownOffset(Double.NaN));
        TreeConfig updated = config.withSeed(1).withHeight(32).withScale(TreeScale.LARGE)
                .withDna(.2, .4, .6).withCanopy(CanopyShape.SPHERE, 1.1, 1)
                .withTrunkThickness(1.2).withCrownStretch(1.3).withCrownLayers(4)
                .withPipeExponent(2.6).withGravityInfluence(.8)
                .withMorphology(.1, .2, .3).withBlockPalette(config.blockPalette()).withPreset(TreePreset.PINE);
        assertEquals(.15, updated.crownOffset());
        assertEquals(config, PresetStore.decode("id", PresetStore.encode("Tree", "Player", config)).config());
    }

    @Test void legacyPresetUsesOriginalCrownPosition() {
        var root = com.google.gson.JsonParser.parseString(PresetStore.encode("Tree", "Player",
                TreeConfig.defaults().withCrownOffset(.2))).getAsJsonObject();
        root.addProperty("version", "1.3");
        root.getAsJsonObject("canopy").remove("crown_offset");
        assertEquals(TreeConfig.defaults(), PresetStore.decode("id", root.toString()).config());
    }
}
