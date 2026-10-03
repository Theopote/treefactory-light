package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class Voxelizer {
    public enum Axis { X, Y, Z }
    public record VoxelData(Map<Cell,Material> blocks,Map<Cell,Axis> axes) {}
    public enum Material {
        MAIN_TRUNK, THICK_BRANCH, THIN_BRANCH, PRIMARY_LEAF, SECONDARY_LEAF, ACCENT;
        public boolean woody() { return ordinal()<=THIN_BRANCH.ordinal(); }
    }
    public static final int MAX_BLOCKS = 80000;
    public Map<Cell, Material> voxelize(List<TreeNode> nodes, TreeConfig config, BooleanSupplier cancelled) {
        return voxelizeData(nodes,config,cancelled).blocks();
    }
    public VoxelData voxelizeData(List<TreeNode> nodes, TreeConfig config, BooleanSupplier cancelled) {
        Map<Cell, Material> blocks = new LinkedHashMap<>();
        Map<Cell,Axis> axes=new HashMap<>();
        boolean[] parents = new boolean[nodes.size()];
        for (TreeNode node : nodes) {
            check(cancelled);
            if (node.parent()<0) continue;
            parents[node.parent()] = true;
            TreeNode parent = nodes.get(node.parent());
            double radius=Math.max(parent.radius(),node.radius());
            Material wood=parent.position().y()<config.scale().height*.36 || radius>=1.45
                    ? Material.MAIN_TRUNK : radius>=.7 ? Material.THICK_BRANCH : Material.THIN_BRANCH;
            Axis axis=axis(parent.position(),node.position());
            fill(blocks,axes,parent.position(),node.position(),parent.radius(),node.radius(),wood,axis,cancelled,config.seed(),config.blockPalette());
            connectSpine(blocks,axes,parent.position(),node.position(),wood,axis);
        }
        if (config.preset() != TreePreset.DEAD && config.fullness()>0) {
            double radius = 1.2+config.fullness()*1.8;
            for (int i=0; i<nodes.size(); i++) if (!parents[i]) {
                Vector3 p = nodes.get(i).position();
                fill(blocks,axes,p,p,radius,radius,Material.PRIMARY_LEAF,Axis.Y,cancelled,config.seed(),config.blockPalette());
            }
        }
        return new VoxelData(Collections.unmodifiableMap(blocks),Collections.unmodifiableMap(axes));
    }
    private static Axis axis(Vector3 a,Vector3 b) {
        Vector3 delta=b.subtract(a);
        double x=Math.abs(delta.x()),y=Math.abs(delta.y()),z=Math.abs(delta.z());
        return x>y && x>=z ? Axis.X : z>y && z>x ? Axis.Z : Axis.Y;
    }
    /** Capsule-capped frustum, tested at block centres; includes zero-length segments. */
    public boolean contains(Vector3 point, Vector3 a, Vector3 b, double r1, double r2) {
        Vector3 axis = b.subtract(a);
        double length2 = axis.lengthSquared();
        double t = length2 < 1e-12 ? 0 : Math.clamp(point.subtract(a).dot(axis)/length2,0,1);
        double radius = length2 < 1e-12 ? Math.max(r1,r2) : r1+(r2-r1)*t;
        return point.subtract(a.add(axis.multiply(t))).lengthSquared() <= radius*radius + 1e-10;
    }
    private void fill(Map<Cell,Material> result,Map<Cell,Axis> axes, Vector3 a, Vector3 b, double r1, double r2,
                      Material material,Axis axis, BooleanSupplier cancelled,long seed,BlockPalette palette) {
        double r = Math.max(r1,r2);
        for (int x=(int)Math.floor(Math.min(a.x(),b.x())-r); x<=Math.ceil(Math.max(a.x(),b.x())+r); x++) {
            check(cancelled);
            for (int y=Math.max(0,(int)Math.floor(Math.min(a.y(),b.y())-r)); y<=Math.ceil(Math.max(a.y(),b.y())+r); y++)
                for (int z=(int)Math.floor(Math.min(a.z(),b.z())-r); z<=Math.ceil(Math.max(a.z(),b.z())+r); z++) {
                    if (!contains(new Vector3(x+.5,y+.5,z+.5),a,b,r1,r2)) continue;
                    Cell cell = new Cell(x,y,z);
                    if (material.woody()) putWood(result,axes,cell,material,axis);
                    else result.putIfAbsent(cell,leafMaterial(cell,seed,palette));
                    if (result.size()>MAX_BLOCKS) throw new IllegalArgumentException("Tree exceeds block budget");
                }
        }
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    /** A radius-0.5 diagonal can miss face-adjacent voxel centres. Keep a connected digital spine. */
    private static Material leafMaterial(Cell cell,long seed,BlockPalette palette) {
        long h=seed ^ (cell.x()*0x9E3779B97F4A7C15L) ^ (cell.y()*0xC2B2AE3D27D4EB4FL) ^ (cell.z()*0x165667B19E3779F9L);
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;
        h=(h^(h>>>27))*0x94D049BB133111EBL;
        double sample=((h^(h>>>31))>>>11)*0x1.0p-53;
        if(sample<palette.primaryWeight()) return Material.PRIMARY_LEAF;
        if(sample<palette.primaryWeight()+palette.secondaryWeight()) return Material.SECONDARY_LEAF;
        return Material.ACCENT;
    }
    private static void putWood(Map<Cell,Material> blocks,Map<Cell,Axis> axes,Cell cell,Material wood,Axis axis) {
        Material old=blocks.get(cell);
        if(old==null || !old.woody() || wood.ordinal()<old.ordinal()) {
            blocks.put(cell,wood); axes.put(cell,axis);
        }
    }
    private static void connectSpine(Map<Cell,Material> blocks,Map<Cell,Axis> axes,Vector3 a,Vector3 b,Material wood,Axis axis) {
        int x=(int)Math.floor(a.x()),y=(int)Math.floor(a.y()),z=(int)Math.floor(a.z());
        putWood(blocks,axes,new Cell(x,y,z),wood,axis);
        Vector3 delta=b.subtract(a);
        int steps=Math.max(1,(int)Math.ceil(Math.sqrt(delta.lengthSquared())*4));
        for(int i=1;i<=steps;i++) {
            Vector3 p=a.add(delta.multiply(i/(double)steps));
            int tx=(int)Math.floor(p.x()),ty=(int)Math.floor(p.y()),tz=(int)Math.floor(p.z());
            while(x!=tx) { x+=Integer.signum(tx-x); putWood(blocks,axes,new Cell(x,y,z),wood,axis); }
            while(y!=ty) { y+=Integer.signum(ty-y); putWood(blocks,axes,new Cell(x,y,z),wood,axis); }
            while(z!=tz) { z+=Integer.signum(tz-z); putWood(blocks,axes,new Cell(x,y,z),wood,axis); }
            if(blocks.size()>MAX_BLOCKS) throw new IllegalArgumentException("Tree exceeds block budget");
        }
    }
}
