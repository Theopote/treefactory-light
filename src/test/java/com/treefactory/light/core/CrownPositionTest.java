package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.Cell;
import com.treefactory.light.core.tree.TreeBuildTask;
import com.treefactory.light.core.tree.TreeNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrownPositionTest {
    @Test void crownBoundariesMatchEveryShape() {
        for (CanopyShape shape : CanopyShape.values()) for (int layers=3;layers<=6;layers++) {
            double base=shape.crownMinY(layers), top=shape.crownMaxY(layers);
            boolean foundAboveBase=false, foundBelowTop=false;
            for (int x=-50;x<=50;x++) for (int z=-50;z<=50;z++) {
                assertTrue(shape.field(x/50.0,base-.001,z/50.0,layers)>0,shape.name());
                assertTrue(shape.field(x/50.0,top+.001,z/50.0,layers)>0,shape.name());
                if (shape.field(x/50.0,base+.001,z/50.0,layers)<=0) foundAboveBase=true;
                if (shape.field(x/50.0,top-.001,z/50.0,layers)<=0) foundBelowTop=true;
            }
            assertTrue(foundAboveBase,shape.name());
            assertTrue(foundBelowTop,shape.name());
        }
    }

    @Test void crownTopTracksTrunkHeightForAllShapesAndSizes() {
        for (CanopyShape shape : CanopyShape.values())
            for (int layers=3;layers<=6;layers++)
                for (double height : new double[] {6,16.4,48})
                    for (double size : new double[] {.75,1.25,2})
                        for (double stretch : new double[] {.6,1,2}) {
                            var config=TreeConfig.defaults().withTrunkHeight(height)
                                    .withCanopy(shape,size,1.25).withCrownLayers(layers).withCrownStretch(stretch);
                            assertEquals(height*1.1,config.withCrownStretch(1).crownTopHeight(),1e-9);
                            assertEquals(config.crownTopHeight(),config.crownCenterHeight()
                                    +shape.crownMaxY(layers)*config.crownVerticalScale(),1e-9,shape.name());
                            assertTrue(config.crownBaseHeight()>=-1e-9,shape.name());
                        }
    }

    @Test void trunkReachesConfiguredHeightAndBranchesStartAtCalculatedCrownBase() {
        SpaceColonizer colonizer=new SpaceColonizer();
        for (double height : new double[] {6,16.4,48}) for (CanopyShape shape : CanopyShape.values()) {
            TreeConfig config=TreeConfig.defaults().withTrunkHeight(height).withCanopy(shape,1.25,1.25);
            List<TreeNode> nodes=colonizer.generate(config);
            int segments=config.trunkSegments();
            assertEquals(height,nodes.get(segments).position().y()-nodes.getFirst().position().y(),1e-9);
            for (int i=1;i<=segments;i++) assertEquals(i-1,nodes.get(i).parent());
            int attached=0;
            for (int i=segments+1;i<nodes.size();i++) {
                TreeNode node=nodes.get(i);
                if (node.parent()<=segments) {
                    attached++;
                    assertTrue(nodes.get(node.parent()).position().y()-.5>=config.crownBaseHeight(),shape.name());
                }
            }
            assertTrue(attached>0,shape.name()+" height="+height);
        }
    }

    @Test void generatedTrunkUsesTrunkMaterialAlongEntireHeight() {
        TreeConfig config=TreeConfig.defaults().withTrunkHeight(20);
        TreeBuildTask.Result tree=new TreeBuildTask().build(config,() -> false);
        for (int i=1;i<=config.trunkSegments();i++) {
            var p=tree.nodes().get(i).position();
            Cell cell=new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));
            assertEquals(Voxelizer.Material.MAIN_TRUNK,tree.blocks().get(cell),"trunk segment "+i);
        }
    }

    @Test void generatedTreesReachSlightlyAboveTrunkHeight() {
        for (double height : new double[] {6,16,48})
            for (CanopyShape shape : CanopyShape.values()) {
                var config=TreeConfig.defaults().withTrunkHeight(height).withCanopy(shape,1.25,1.25);
                var tree=new TreeBuildTask().build(config,() -> false);
                int top=tree.blocks().keySet().stream().mapToInt(Cell::y).max().orElseThrow();
                assertTrue(top>height,shape+" height="+height+" top="+top);
                assertTrue(top<=height+Math.max(6,height*.4),shape+" height="+height+" top="+top);
            }
    }
}
