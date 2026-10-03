package com.treefactory.light.core.tree;

import com.treefactory.light.core.algorithm.*;
import com.treefactory.light.core.config.TreeConfig;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class TreeBuildTask {
    public record Result(List<TreeNode> nodes, Map<Cell,Voxelizer.Material> blocks,Map<Cell,Voxelizer.Axis> axes) {}
    public Result build(TreeConfig config, BooleanSupplier cancelled) {
        List<TreeNode> nodes = new PipeModelEngine().apply(new SpaceColonizer().generate(config,cancelled),config);
        Voxelizer.VoxelData data=new Voxelizer().voxelizeData(nodes,config,cancelled);
        return new Result(nodes,data.blocks(),data.axes());
    }
}
