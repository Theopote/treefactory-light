package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.tree.TreeNode;
import com.treefactory.light.core.config.TreeConfig;
import java.util.*;

public final class PipeModelEngine {
    public List<TreeNode> apply(List<TreeNode> skeleton) {
        return apply(skeleton,2.75);
    }
    /**
     * Radius = blend of (a) the pipe-model radius, soft-capped so ordering survives above the cap instead of being
     * clipped to one value, and (b) a length-based taper from the distance to the farthest tip. Both are monotone
     * along every root-to-tip path, so a child is never thicker than its parent.
     */
    private static final double LENGTH_WEIGHT = .25, GAMMA = .8;
    public List<TreeNode> apply(List<TreeNode> skeleton, TreeConfig config) {
        // Expand the displayed exponent's narrow range to make branch thickness visibly adjustable.
        double cap=Math.max(1.0,config.trunkHeight()*.055), e=2.75+(config.pipeExponent()-2.75)*8.6, t=config.trunkThickness();
        int n=skeleton.size();
        double[] sums=new double[n], raw=new double[n]; int[] h=new int[n];
        for (int i=n-1;i>=0;i--) {
            TreeNode node=skeleton.get(i);
            if (node.parent() >= i || node.parent() < -1) throw new IllegalArgumentException("Invalid tree topology");
            raw[i]= sums[i]==0 ? .5 : StrictMath.pow(sums[i],1/e);
            if (node.parent()>=0) { sums[node.parent()]+=StrictMath.pow(raw[i],e); h[node.parent()]=Math.max(h[node.parent()],h[i]+1); }
        }
        double span=cap-.5, hRoot=Math.max(1,h[0]);
        double[] fin=new double[n]; List<TreeNode> out=new ArrayList<>(skeleton);
        for (int i=0;i<n;i++) {
            double pipe = .5+span*(1-StrictMath.exp(-.5*(raw[i]-.5)/span));
            double len  = .5+(cap-.5)*StrictMath.pow(h[i]/hRoot,GAMMA);
            double r = (1-LENGTH_WEIGHT)*pipe + LENGTH_WEIGHT*len;
            TreeNode node=skeleton.get(i);
            if (node.parent()>=0) r=Math.min(r,fin[node.parent()]/t);
            fin[i]=Math.max(.5,Math.min(cap*t,r*t)); out.set(i,node.withRadius(fin[i]));
        }
        return List.copyOf(out);
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
            double radius = Math.min(maxRadius,sums[i] == 0 ? .5 : StrictMath.pow(sums[i],1/exponent));
            result.set(i,node.withRadius(radius));
            if (node.parent() >= 0) sums[node.parent()] += StrictMath.pow(radius,exponent);
        }
        return List.copyOf(result);
    }
}
