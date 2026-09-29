package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class Voxelizer {
    public enum Material { LOG, LEAVES }
    public static final int MAX_BLOCKS = 80000;
    public Map<Cell, Material> voxelize(List<TreeNode> nodes, TreeConfig config, BooleanSupplier cancelled) {
        Map<Cell, Material> blocks = new LinkedHashMap<>();
        boolean[] parents = new boolean[nodes.size()];
        for (TreeNode node : nodes) {
            check(cancelled);
            if (node.parent()<0) continue;
            parents[node.parent()] = true;
            TreeNode parent = nodes.get(node.parent());
            fill(blocks,parent.position(),node.position(),parent.radius(),node.radius(),Material.LOG,cancelled);
        }
        if (config.preset() != TreePreset.DEAD && config.fullness()>0) {
            double radius = 1.2+config.fullness()*1.8;
            for (int i=0; i<nodes.size(); i++) if (!parents[i]) {
                Vector3 p = nodes.get(i).position();
                fill(blocks,p,p,radius,radius,Material.LEAVES,cancelled);
            }
        }
        return Collections.unmodifiableMap(blocks);
    }
    /** Capsule-capped frustum, tested at block centres; includes zero-length segments. */
    public boolean contains(Vector3 point, Vector3 a, Vector3 b, double r1, double r2) {
        Vector3 axis = b.subtract(a);
        double length2 = axis.lengthSquared();
        double t = length2 < 1e-12 ? 0 : Math.clamp(point.subtract(a).dot(axis)/length2,0,1);
        double radius = length2 < 1e-12 ? Math.max(r1,r2) : r1+(r2-r1)*t;
        return point.subtract(a.add(axis.multiply(t))).lengthSquared() <= radius*radius + 1e-10;
    }
    private void fill(Map<Cell,Material> result, Vector3 a, Vector3 b, double r1, double r2,
                      Material material, BooleanSupplier cancelled) {
        double r = Math.max(r1,r2);
        for (int x=(int)Math.floor(Math.min(a.x(),b.x())-r); x<=Math.ceil(Math.max(a.x(),b.x())+r); x++) {
            check(cancelled);
            for (int y=Math.max(0,(int)Math.floor(Math.min(a.y(),b.y())-r)); y<=Math.ceil(Math.max(a.y(),b.y())+r); y++)
                for (int z=(int)Math.floor(Math.min(a.z(),b.z())-r); z<=Math.ceil(Math.max(a.z(),b.z())+r); z++) {
                    if (!contains(new Vector3(x+.5,y+.5,z+.5),a,b,r1,r2)) continue;
                    Cell cell = new Cell(x,y,z);
                    if (material==Material.LOG) result.put(cell,material); else result.putIfAbsent(cell,material);
                    if (result.size()>MAX_BLOCKS) throw new IllegalArgumentException("Tree exceeds block budget");
                }
        }
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
}
