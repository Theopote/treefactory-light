package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.TreeNode;
import com.treefactory.light.core.tree.Cell;
import com.treefactory.light.core.tree.TreeBuildTask;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrownStretchTest {
    @Test void compressionKeepsTopAndStretchKeepsBase() {
        for (CanopyShape shape : CanopyShape.values())
            for (int layers=3;layers<=6;layers++)
                for (double height : new double[] {6,16.4,48})
                    for (double size : new double[] {.75,1.25,2}) {
                        TreeConfig base=TreeConfig.defaults().withTrunkHeight(height)
                                .withCanopy(shape,size,1.2).withCrownLayers(layers).withCrownStretch(.6);
                        TreeConfig normal=base.withCrownStretch(1);
                        TreeConfig stretched=base.withCrownStretch(2);
                        String name=shape+" layers="+layers+" height="+height+" size="+size;
                        assertTrue(base.crownBaseHeight()>normal.crownBaseHeight(),name);
                        assertEquals(normal.crownBaseHeight(),stretched.crownBaseHeight(),1e-9,name);
                        assertEquals(height*1.1,normal.crownTopHeight(),1e-9,name);
                        assertEquals(normal.crownTopHeight(),base.crownTopHeight(),1e-9,name);
                        assertTrue(normal.crownTopHeight()<stretched.crownTopHeight(),name);
                        assertEquals(normal.crownVerticalScale()*2,stretched.crownVerticalScale(),1e-9,name);
                        var below=base.withCrownStretch(1-1e-8);
                        var above=base.withCrownStretch(1+1e-8);
                        assertEquals(normal.crownBaseHeight(),below.crownBaseHeight(),height*1e-7,name);
                        assertEquals(normal.crownTopHeight(),above.crownTopHeight(),height*1e-7,name);
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

    @Test void stretchingRaisesGeneratedTreeTops() {
        var builder=new TreeBuildTask();
        for (CanopyShape shape : new CanopyShape[] {CanopyShape.ELLIPSOID,CanopyShape.UMBRELLA,CanopyShape.CONE}) {
            var config=TreeConfig.defaults().withTrunkHeight(24).withCanopy(shape,1.25,1.2);
            var low=builder.build(config.withCrownStretch(.6),() -> false);
            var high=builder.build(config.withCrownStretch(1.5),() -> false);
            int lowTop=low.blocks().keySet().stream().mapToInt(Cell::y).max().orElseThrow();
            int highTop=high.blocks().keySet().stream().mapToInt(Cell::y).max().orElseThrow();
            assertTrue(highTop>lowTop,shape+" low="+lowTop+" high="+highTop);
        }
    }

    @Test void compressedCrownReachesTrunkTop() {
        var builder=new TreeBuildTask();
        for (CanopyShape shape : CanopyShape.values())
            for (double height : new double[] {6,16,48})
                for (double size : new double[] {.75,1.25,2}) {
        var config=TreeConfig.defaults().withTrunkHeight(height).withCanopy(shape,size,1.25).withCrownStretch(.6);
        var tree=builder.build(config,() -> false);
        int leavesTop=tree.blocks().entrySet().stream()
                .filter(entry -> !entry.getValue().woody())
                .mapToInt(entry -> entry.getKey().y()).max().orElse(Integer.MIN_VALUE);
        int trunkTop=tree.blocks().entrySet().stream()
                .filter(entry -> entry.getValue()==Voxelizer.Material.MAIN_TRUNK)
                .mapToInt(entry -> entry.getKey().y()).max().orElseThrow();
        assertTrue(leavesTop>=trunkTop,shape+" height="+height+" size="+size+" leaves="+leavesTop+" trunk="+trunkTop);
                }
    }
}
