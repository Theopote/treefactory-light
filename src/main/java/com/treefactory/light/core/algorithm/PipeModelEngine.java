package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.tree.TreeNode;
import java.util.*;

public final class PipeModelEngine {
    public List<TreeNode> apply(List<TreeNode> skeleton) {
        double[] sums = new double[skeleton.size()];
        List<TreeNode> result = new ArrayList<>(skeleton);
        for (int i=skeleton.size()-1; i>=0; i--) {
            TreeNode node = skeleton.get(i);
            if (node.parent() >= i || node.parent() < -1) throw new IllegalArgumentException("Invalid tree topology");
            double radius = sums[i] == 0 ? .5 : Math.pow(sums[i],1/2.3)+.1;
            result.set(i,node.withRadius(radius));
            if (node.parent() >= 0) sums[node.parent()] += Math.pow(radius,2.3);
        }
        return List.copyOf(result);
    }
}
