package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.BranchMorphology;
import com.treefactory.light.core.algorithm.PipeModelEngine;
import com.treefactory.light.core.algorithm.SpaceColonizer;
import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.CanopyShape;
import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.tree.TreeBuildTask;
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

    @Test void pipeExponentChangesMediumTreeBranches() {
        var config=TreeConfig.defaults();
        var skeleton=new SpaceColonizer().generate(config);
        var engine=new PipeModelEngine();
        var thick=engine.apply(skeleton,config.withPipeExponent(2.5));
        var thin=engine.apply(skeleton,config.withPipeExponent(3));
        double delta=0;
        for(int i=0;i<thick.size();i++) delta+=thick.get(i).radius()-thin.get(i).radius();
        assertTrue(delta/thick.size() > .02, "average radius difference="+delta/thick.size());
        var voxelizer=new Voxelizer();
        long thickWood=voxelizer.voxelizeData(thick,config,()->false).blocks().values().stream().filter(Voxelizer.Material::woody).count();
        long thinWood=voxelizer.voxelizeData(thin,config,()->false).blocks().values().stream().filter(Voxelizer.Material::woody).count();
        assertTrue(thickWood >= thinWood * 1.2, "wood blocks: "+thickWood+" vs "+thinWood);
    }
}
