package com.treefactory.light.core;

import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeBuildTask;
import org.junit.jupiter.api.Test;

class TmpShapeTest {
    @Test void dump() {
        for (TreePreset p : TreePreset.values()) {
            TreeConfig base = TreeConfig.defaults();
            System.out.println("PRESET " + p);
            for (CanopyShape s : CanopyShape.values()) {
                TreeConfig c = base.withCanopy(s, base.canopyScale(), base.attractionDensity());
                var r = new TreeBuildTask().build(c, () -> false);
                int minWoodY = Integer.MAX_VALUE, wood = 0, minX = 99, maxX = -99;
                for (var e : r.blocks().entrySet()) {
                    if (e.getValue().woody()) { wood++; minWoodY = Math.min(minWoodY, e.getKey().y()); }
                    minX = Math.min(minX, e.getKey().x()); maxX = Math.max(maxX, e.getKey().x());
                }
                System.out.println("SHAPE " + s + " nodes=" + r.nodes().size() + " blocks=" + r.blocks().size() + " wood=" + wood + " minWoodY=" + minWoodY + " x=" + minX + ".." + maxX);
            }
            break;
        }
    }
}
