package com.treefactory.light.core;

import com.treefactory.light.core.algorithm.*;
import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.*;
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
        assertEquals(Math.pow(2*Math.pow(.5,2.75),1/2.75),result.getFirst().radius(),1e-12);
        assertTrue(result.getFirst().radius()<.7);
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
        for(int y=0;y<=5;y++) assertTrue(blocks.get(new Cell(0,y,0)).woody());
        assertTrue(blocks.values().stream().anyMatch(m->!m.woody()));
        assertTrue(blocks.keySet().stream().allMatch(p->p.y()>=0));
    }
    @Test void voxelizerRecordsTheDominantBranchAxis() {
        List<TreeNode> nodes=List.of(
                new TreeNode(new Vector3(.5,.5,.5),-1,.5),
                new TreeNode(new Vector3(.5,3.5,.5),0,.5),
                new TreeNode(new Vector3(4.5,3.5,.5),1,.5));
        var voxels=new Voxelizer().voxelizeData(nodes,TreeConfig.defaults(),()->false);
        assertEquals(Voxelizer.Axis.Y,voxels.axes().get(new Cell(0,1,0)));
        assertEquals(Voxelizer.Axis.X,voxels.axes().get(new Cell(3,3,0)));
        assertTrue(voxels.axes().keySet().stream().allMatch(c->voxels.blocks().get(c).woody()));
    }
    @Test void allPresetsAndSizesStayInsideBudgets() {
        for(TreePreset preset:TreePreset.values()) for(TreeScale size:TreeScale.values()) {
            var result=new TreeBuildTask().build(TreeConfig.defaults().withPreset(preset).withScale(size),()->false);
            assertFalse(result.blocks().isEmpty()); assertTrue(result.blocks().size()<=Voxelizer.MAX_BLOCKS);
            if(preset==TreePreset.DEAD) assertTrue(result.blocks().values().stream().allMatch(Voxelizer.Material::woody));
        }
    }
    @Test void cancelledWorkStopsAndInvalidDnaIsRejected() {
        assertThrows(CancellationException.class,()->new TreeBuildTask().build(TreeConfig.defaults(),()->true));
        assertThrows(IllegalArgumentException.class,()->TreeConfig.defaults().withDna(Double.NaN,.5,.5));
        assertThrows(IllegalArgumentException.class,()->TreeConfig.defaults().withDna(.5,1.1,.5));
        assertThrows(IllegalArgumentException.class,()->TreeConfig.defaults().withPalette("../bad","minecraft:oak_leaves"));
    }
    @Test void extremeDnaAndNegativeSeedsRemainBounded() {
        for(TreePreset preset:TreePreset.values()) for(int bits=0;bits<8;bits++) {
            TreeConfig config=TreeConfig.defaults().withPreset(preset).withScale(TreeScale.GIANT).withSeed(-91237)
                    .withDna(bits&1,(bits>>1)&1,(bits>>2)&1);
            var result=new TreeBuildTask().build(config,()->false);
            assertTrue(result.blocks().size()<=Voxelizer.MAX_BLOCKS);
            assertTrue(result.blocks().values().stream().anyMatch(Voxelizer.Material::woody));
            if(config.fullness()==0) assertTrue(result.blocks().values().stream().allMatch(Voxelizer.Material::woody));
        }
    }
    @Test void generatedLogsFormOneFaceConnectedTree() {
        for(TreePreset preset:TreePreset.values()) {
            var blocks=new TreeBuildTask().build(TreeConfig.defaults().withPreset(preset),()->false).blocks();
            Set<Cell> logs=new HashSet<>(); blocks.forEach((cell,material)->{if(material.woody()) logs.add(cell);});
            Deque<Cell> queue=new ArrayDeque<>(); Cell first=logs.iterator().next(); logs.remove(first); queue.add(first);
            int[][] steps={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            while(!queue.isEmpty()) {
                Cell c=queue.removeFirst();
                for(int[] d:steps) { Cell n=new Cell(c.x()+d[0],c.y()+d[1],c.z()+d[2]); if(logs.remove(n)) queue.addLast(n); }
            }
            assertTrue(logs.isEmpty(),preset+" has disconnected branch voxels");
        }
    }
    @Test void perlinNoiseIsContinuousAndSeeded() {
        NoiseGenerator a=new NoiseGenerator(123),b=new NoiseGenerator(123);
        assertEquals(a.sample(.4,.7,.3),b.sample(.4,.7,.3));
        assertEquals(a.sample(.4,.7,.3),a.sample(.400001,.7,.3),.0001);
        assertEquals(a.sampleSimplex(.4,.7,.3),b.sampleSimplex(.4,.7,.3));
        assertEquals(a.sampleSimplex(.4,.7,.3),a.sampleSimplex(.400001,.7,.3),.0001);
    }
    @Test void canopyShapesRespectTheirGeometryAndRemainDeterministic() {
        assertEquals(17,CanopyShape.values().length);
        for(CanopyShape shape:CanopyShape.values()) {
            assertTrue(shape.field(0,0,0)<=0 || shape==CanopyShape.SPINDLE_MULTI_LAYER,shape.name());
            assertTrue(shape.field(2,0,0)>0,shape.name());
            TreeConfig config=TreeConfig.defaults().withCanopy(shape,1.4,1.2);
            var first=new SpaceColonizer().generate(config);
            assertEquals(first,new SpaceColonizer().generate(config));
            assertTrue(first.size()>20,shape.name());
        }
        assertTrue(CanopyShape.HEMISPHERE.field(0,-.5,0)>0);
        assertTrue(CanopyShape.CONE.field(.8,.8,0)>0);
        assertTrue(CanopyShape.CYLINDER.field(.8,.8,0)<0);
        assertTrue(CanopyShape.OVOID.field(.8,-.24,0)<CanopyShape.OVOID.field(.8,.36,0));
        assertTrue(CanopyShape.OBOVATE.field(.8,.36,0)<CanopyShape.OBOVATE.field(.8,-.24,0));
        assertTrue(CanopyShape.HYPERBOLOID.field(.8,0,0)>CanopyShape.HYPERBOLOID.field(.8,.9,0));
        assertTrue(CanopyShape.NEILOID.field(.5,0,0)>CanopyShape.CONE.field(.5,0,0));
    }
    @Test void weightedCanopyRolesFollowConfiguredProbabilities() {
        TreeConfig config=TreeConfig.defaults().withBlockPalette(TreeConfig.defaults().blockPalette().withWeights(.8,.15,.05));
        var a=new TreeBuildTask().build(config,()->false).blocks();
        var b=new TreeBuildTask().build(config,()->false).blocks();
        assertEquals(a,b);
        long leafCount=a.values().stream().filter(m->!m.woody()).count();
        assertTrue(leafCount>100);
        double primary=a.values().stream().filter(m->m==Voxelizer.Material.PRIMARY_LEAF).count()/(double)leafCount;
        double secondary=a.values().stream().filter(m->m==Voxelizer.Material.SECONDARY_LEAF).count()/(double)leafCount;
        assertEquals(.8,primary,.1); assertEquals(.15,secondary,.08);
        assertThrows(IllegalArgumentException.class,()->config.withPipeExponent(2.2));
        assertThrows(IllegalArgumentException.class,()->config.withCanopy(CanopyShape.SPHERE,2.5,1));
    }
    @Test void largestSupportedCanopyStaysWithinBudget() {
        TreeConfig config=TreeConfig.defaults().withScale(TreeScale.GIANT)
                .withCanopy(CanopyShape.SPHERE,2,2);
        var result=new TreeBuildTask().build(config,()->false);
        assertTrue(result.blocks().size()<=Voxelizer.MAX_BLOCKS);
    }
    @Test void giantWillowSharesDroopingGrowthAcrossTips() {
        var nodes=new SpaceColonizer().generate(TreeConfig.defaults().withPreset(TreePreset.WILLOW).withScale(TreeScale.GIANT));
        int crownNodes=nodes.size();
        for(int i=1;i<nodes.size();i++) {
            Vector3 p=nodes.get(i).position(), par=nodes.get(nodes.get(i).parent()).position();
            if(Math.abs(p.x()-par.x())<1e-9 && Math.abs(p.z()-par.z())<1e-9 && p.y()<par.y()-.5) { crownNodes=i; break; }
        }
        boolean[] crownParent=new boolean[crownNodes], drooping=new boolean[crownNodes];
        for(int i=1;i<crownNodes;i++) crownParent[nodes.get(i).parent()]=true;
        for(int i=crownNodes;i<nodes.size();i++) if(nodes.get(i).parent()<crownNodes) drooping[nodes.get(i).parent()]=true;
        int tips=0, grown=0;
        for(int i=0;i<crownNodes;i++) if(!crownParent[i]) { tips++; if(drooping[i]) grown++; }
        assertTrue(tips>50);
        assertTrue(grown>=tips*.8,"Only "+grown+" of "+tips+" giant willow tips drooped");
    }
    @Test void generatedTrunksRespectScaleRelativeRadiusCap() {
        for(TreeScale scale:TreeScale.values()) {
            TreeConfig config=TreeConfig.defaults().withScale(scale);
            var nodes=new TreeBuildTask().build(config,()->false).nodes();
            assertTrue(nodes.getFirst().radius()<=Math.max(1.0,scale.height*.055)+1e-9);
        }
    }
    @Test void oakBranchesDoNotGrowDownward() {
        var nodes=new SpaceColonizer().generate(TreeConfig.defaults());
        int down=0;
        for(int i=1;i<nodes.size();i++) {
            if(nodes.get(i).position().y()<nodes.get(nodes.get(i).parent()).position().y()) down++;
        }
        assertEquals(0, down);
    }
    @Test void pineTrunkMaterialStaysNearTheColumn() {
        var result=new TreeBuildTask().build(TreeConfig.defaults().withPreset(TreePreset.PINE).withScale(TreeScale.GIANT),()->false);
        double cap=Math.max(1.0,TreeScale.GIANT.height*.055)+2.5;
        long bark=0,far=0;
        for(var cell:result.blocks().entrySet()) {
            if(cell.getValue()!=Voxelizer.Material.MAIN_TRUNK) continue;
            bark++;
            if(Math.hypot(cell.getKey().x()+.5,cell.getKey().z()+.5)>cap) far++;
        }
        assertTrue(bark>0);
        assertTrue(far<=bark*.4, far+" of "+bark+" pine trunk voxels are far from the column");
    }
}
