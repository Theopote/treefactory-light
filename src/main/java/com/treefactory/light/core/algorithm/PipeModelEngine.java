package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.tree.TreeNode;
import com.treefactory.light.core.config.TreeConfig;
import java.util.*;

public final class PipeModelEngine {
    public List<TreeNode> apply(List<TreeNode> skeleton) {
        return apply(skeleton,2.75);
    }
    public List<TreeNode> apply(List<TreeNode> skeleton, TreeConfig config) {
        return apply(skeleton,config.pipeExponent(),Math.max(1.0,config.scale().height*.055));
    }
    public List<TreeNode> apply(List<TreeNode> skeleton,double exponent) {
        return apply(skeleton,exponent,Double.POSITIVE_INFINITY);
    }
    private List<TreeNode> apply(List<TreeNode> skeleton,double exponent,double maxRadius) {
        if (!Double.isFinite(exponent) || exponent<2.5 || exponent>3) throw new IllegalArgumentException("Invalid pipe exponent");
        double[] sums = new double[skeleton.size()];
        List<TreeNode> result = new ArrayList<>(skeleton);
        for (int i=skeleton.size()-1; i>=0; i--) {
            TreeNode node = skeleton.get(i);
            if (node.parent() >= i || node.parent() < -1) throw new IllegalArgumentException("Invalid tree topology");
            // No additive radius per generation: that compounded through deep branching.
            double radius = Math.min(maxRadius,sums[i] == 0 ? .5 : Math.pow(sums[i],1/exponent));
            result.set(i,node.withRadius(radius));
            if (node.parent() >= 0) sums[node.parent()] += Math.pow(radius,exponent);
        }
        return List.copyOf(result);
    }
}
