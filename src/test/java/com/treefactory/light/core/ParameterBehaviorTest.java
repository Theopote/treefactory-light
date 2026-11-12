package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.BranchMorphology;
import com.treefactory.light.core.algorithm.PipeModelEngine;
import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.CanopyShape;
import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.tree.TreeBuildTask;
import com.treefactory.light.core.tree.TreeNode;
import com.treefactory.light.core.tree.Vector3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParameterBehaviorTest {
    @Test void fullnessDoesNotChangeSkeleton() {
        var config=TreeConfig.defaults();
        var low=config.withParameters(config.crookedness(),config.spread(),.1);
        var high=config.withParameters(config.crookedness(),config.spread(),1);
        var colonizer=new SpaceColonizer();
        assertEquals(colonizer.generate(low),colonizer.generate(high));
        assertTrue(new TreeBuildTask().build(high,()->false).blocks().size()
                > new TreeBuildTask().build(low,()->false).blocks().size());
    }

    @Test void spreadDoesNotControlVerticalScaleOrPropRoots() {
        var config=TreeConfig.defaults().withCanopy(CanopyShape.SPHERE,1.25,1.25)
                .withMorphology(.5,.8,.5);
        var low=config.withParameters(config.crookedness(),0,config.fullness());
        var high=config.withParameters(config.crookedness(),1,config.fullness());
        assertEquals(low.crownVerticalScale(),high.crownVerticalScale());
        assertTrue(BranchMorphology.growsPropRoots(low));
        assertTrue(BranchMorphology.growsPropRoots(high));
    }

    @Test void negativeGrowthBiasAllowsDownwardBranches() {
        var bias=BranchMorphology.growthBias(TreeConfig.defaults().withGravityInfluence(.6));
        assertTrue(bias.yBias() < 0);
        assertTrue(bias.allowDownward());
    }

    @Test void taperChangesMediumTreeBranches() {
        var config=TreeConfig.defaults();
        var skeleton=new SpaceColonizer().generate(config);
        var engine=new PipeModelEngine();
        var thick=engine.apply(skeleton,config.withTaper(0));
        var thin=engine.apply(skeleton,config.withTaper(1));
        var middle=engine.apply(skeleton,config.withTaper(.5));
        assertEquals(.5,config.taper());
        assertEquals(engine.apply(skeleton,config),middle);
        double delta=0;
        for(int i=0;i<thick.size();i++) delta+=thick.get(i).radius()-thin.get(i).radius();
        assertTrue(delta/thick.size() > .02, "average radius difference="+delta/thick.size());
        var voxelizer=new Voxelizer();
        long thickWood=voxelizer.voxelizeData(thick,config,()->false).blocks().values().stream().filter(Voxelizer.Material::woody).count();
        long thinWood=voxelizer.voxelizeData(thin,config,()->false).blocks().values().stream().filter(Voxelizer.Material::woody).count();
        assertTrue(thickWood >= thinWood * 1.15, "wood blocks: "+thickWood+" vs "+thinWood);
    }

    @Test void taperPreservesPreviousBranchRadii() {
        var skeleton=List.of(new TreeNode(Vector3.ZERO,-1,0),
                new TreeNode(Vector3.ZERO,0,0),new TreeNode(Vector3.ZERO,0,0));
        var engine=new PipeModelEngine();
        double[] taper={0,.5,1};
        double[] previousRadii={.8735907102332541,.675075263027317,.6524357239367048};
        for (int i=0;i<taper.length;i++) {
            var nodes=engine.apply(skeleton,TreeConfig.defaults().withTaper(taper[i]));
            assertEquals(previousRadii[i],nodes.getFirst().radius(),1e-14);
            assertEquals(.5,nodes.get(1).radius());
            assertEquals(.5,nodes.get(2).radius());
        }
    }
}
