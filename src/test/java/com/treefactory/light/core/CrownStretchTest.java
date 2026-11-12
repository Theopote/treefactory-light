package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrownStretchTest {
    @Test void stretchAnchorsTheCrownTopForEveryStretchableShape() {
        for (CanopyShape shape : CanopyShape.values()) {
            if (!shape.stretchable()) continue;
            TreeConfig base = TreeConfig.defaults().withCanopy(shape, .75, 1.2).withCrownStretch(.6);
            TreeConfig stretched = base.withCrownStretch(1.0);
            assertEquals(base.crownTopHeight(),stretched.crownTopHeight(),1e-9,shape.name());
            assertTrue(stretched.crownBaseHeight()<base.crownBaseHeight(),shape.name());
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

}
