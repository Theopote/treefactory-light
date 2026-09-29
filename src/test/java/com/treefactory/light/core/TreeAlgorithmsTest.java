package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.*;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreeAlgorithmsTest {
    @Test void seededGrowthIsDeterministicAndBranches() {
        SpaceColonizer colonizer=new SpaceColonizer(); TreeConfig c=TreeConfig.defaults();
        var a=colonizer.generate(c);
        assertEquals(a,colonizer.generate(c));
        assertNotEquals(a,colonizer.generate(c.withSeed(9981)));
        assertTrue(a.size()>25); assertTrue(a.size()<=SpaceColonizer.MAX_NODES);
        Set<Integer> parents=new HashSet<>(); boolean branched=false;
        for(int i=1;i<a.size();i++) {
            assertTrue(a.get(i).parent()>=0 && a.get(i).parent()<i);
            if(!parents.add(a.get(i).parent())) branched=true;
            assertTrue(Double.isFinite(a.get(i).position().lengthSquared()));
        }
        assertTrue(branched);
    }
    @Test void pipeModelMatchesMurrayLaw() {
        var result=new PipeModelEngine().apply(List.of(new TreeNode(Vector3.ZERO,-1,0),
                new TreeNode(new Vector3(1,1,0),0,0),new TreeNode(new Vector3(-1,1,0),0,0)));
        assertEquals(.5,result.get(1).radius());
        assertEquals(Math.pow(2*Math.pow(.5,2.3),1/2.3)+.1,result.getFirst().radius(),1e-12);
        assertThrows(IllegalArgumentException.class,()->new PipeModelEngine().apply(List.of(new TreeNode(Vector3.ZERO,0,1))));
    }
    @Test void frustumUsesInterpolatedRadiusAndHandlesDegenerateSegments() {
        Voxelizer v=new Voxelizer(); Vector3 a=Vector3.ZERO,b=new Vector3(0,10,0);
        assertTrue(v.contains(new Vector3(1.4,5,0),a,b,2,1));
        assertFalse(v.contains(new Vector3(1.6,5,0),a,b,2,1));
        assertTrue(v.contains(new Vector3(0,-1,0),a,b,2,1));
        assertFalse(v.contains(new Vector3(0,12,0),a,b,2,1));
        assertTrue(v.contains(new Vector3(.5,0,0),a,a,.5,.5));
        assertFalse(v.contains(new Vector3(.6,0,0),a,a,.5,.5));
    }
    @Test void voxelizationKeepsTrunkConnectedAndLeavesNeverReplaceLogs() {
        List<TreeNode> nodes=List.of(new TreeNode(new Vector3(.5,.5,.5),-1,.5),new TreeNode(new Vector3(.5,5.5,.5),0,.5));
        var blocks=new Voxelizer().voxelize(nodes,TreeConfig.defaults(),()->false);
        for(int y=0;y<=5;y++) assertEquals(Voxelizer.Material.LOG,blocks.get(new Cell(0,y,0)));
        assertTrue(blocks.containsValue(Voxelizer.Material.LEAVES));
        assertTrue(blocks.keySet().stream().allMatch(p->p.y()>=0));
    }
    @Test void allPresetsAndSizesStayInsideBudgets() {
        assertTimeout(Duration.ofSeconds(30),()-> {
            for(TreePreset preset:TreePreset.values()) for(TreeScale size:TreeScale.values()) {
                var result=new TreeBuildTask().build(TreeConfig.defaults().withPreset(preset).withScale(size),()->false);
                assertFalse(result.blocks().isEmpty()); assertTrue(result.blocks().size()<=Voxelizer.MAX_BLOCKS);
                if(preset==TreePreset.DEAD) assertFalse(result.blocks().containsValue(Voxelizer.Material.LEAVES));
            }
        });
    }
    @Test void cancelledWorkStopsAndInvalidDnaIsRejected() {
        assertThrows(CancellationException.class,()->new TreeBuildTask().build(TreeConfig.defaults(),()->true));
        assertThrows(IllegalArgumentException.class,()->TreeConfig.defaults().withDna(Double.NaN,.5,.5));
        assertThrows(IllegalArgumentException.class,()->TreeConfig.defaults().withDna(.5,1.1,.5));
        assertThrows(IllegalArgumentException.class,()->TreeConfig.defaults().withPalette("../bad","minecraft:oak_leaves"));
    }
    @Test void perlinNoiseIsContinuousAndSeeded() {
        NoiseGenerator a=new NoiseGenerator(123),b=new NoiseGenerator(123);
        assertEquals(a.sample(.4,.7,.3),b.sample(.4,.7,.3));
        assertEquals(a.sample(.4,.7,.3),a.sample(.400001,.7,.3),.0001);
    }
}
