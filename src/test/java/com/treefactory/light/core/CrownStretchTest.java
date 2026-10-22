package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrownStretchTest {
    @Test void stretchAnchorsTheCrownBaseForEveryStretchableShape() {
        SpaceColonizer colonizer = new SpaceColonizer();
        for (CanopyShape shape : CanopyShape.values()) {
            if (!shape.stretchable()) continue;
            TreeConfig base = TreeConfig.defaults().withCanopy(shape, 1.4, 1.2).withSeed(8848123L).withCrownStretch(1.0);
            TreeConfig stretched = base.withCrownStretch(2.0);
            double baseMin = minCrownY(colonizer.generate(base), base.height());
            double stretchMin = minCrownY(colonizer.generate(stretched), stretched.height());
            assertEquals(baseMin, stretchMin, 1.5, shape.name());
            assertTrue(maxCrownY(colonizer.generate(stretched), stretched.height()) > maxCrownY(colonizer.generate(base), base.height()),
                    shape.name());
        }
    }

    @Test void spindleMultiLayerChangesWithLayerCount() {
        SpaceColonizer colonizer = new SpaceColonizer();
        var three = colonizer.generate(TreeConfig.defaults()
                .withCanopy(CanopyShape.SPINDLE_MULTI_LAYER, 1.4, 1.2)
                .withCrownLayers(3)
                .withSeed(42L));
        var six = colonizer.generate(TreeConfig.defaults()
                .withCanopy(CanopyShape.SPINDLE_MULTI_LAYER, 1.4, 1.2)
                .withCrownLayers(6)
                .withSeed(42L));
        assertNotEquals(three, six);
        assertTrue(three.size() > 20);
        assertTrue(six.size() > 20);
    }

    private static double minCrownY(List<TreeNode> nodes, double height) {
        double trunkTop = height * .36;
        return nodes.stream().mapToDouble(n -> n.position().y()).filter(y -> y > trunkTop + .5).min().orElse(trunkTop);
    }

    private static double maxCrownY(List<TreeNode> nodes, double height) {
        double trunkTop = height * .36;
        return nodes.stream().mapToDouble(n -> n.position().y()).filter(y -> y > trunkTop + .5).max().orElse(trunkTop);
    }
}
