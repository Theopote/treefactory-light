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

    /** Tracks voxel count and enforces {@link #MAX_BLOCKS} on every new cell. */
    static final class VoxelAccumulator {
        private final Map<Cell, Material> blocks = new LinkedHashMap<>();
        private final Map<Cell, Axis> axes = new HashMap<>();

        Map<Cell, Material> blocks() { return blocks; }
        Map<Cell, Axis> axes() { return axes; }
        int size() { return blocks.size(); }

        void putWood(Cell cell, Material wood, Axis axis) {
            Material old = blocks.get(cell);
            if (old == null) {
                ensureBudget();
                blocks.put(cell, wood);
                axes.put(cell, axis);
                return;
            }
            if (!old.woody() || wood.ordinal() < old.ordinal()) {
                blocks.put(cell, wood);
                axes.put(cell, axis);
            }
        }

        void putLeaf(Cell cell, Material leaf) {
            if (blocks.containsKey(cell)) return;
            ensureBudget();
            blocks.put(cell, leaf);
        }

        private void ensureBudget() {
            if (blocks.size() >= MAX_BLOCKS) throw new IllegalArgumentException("Tree exceeds block budget");
        }
    }

    public Map<Cell, Material> voxelize(List<TreeNode> nodes, TreeConfig config, BooleanSupplier cancelled) {
        return voxelizeData(nodes,config,cancelled).blocks();
    }
    public VoxelData voxelizeData(List<TreeNode> nodes, TreeConfig config, BooleanSupplier cancelled) {
        VoxelAccumulator acc = new VoxelAccumulator();
        boolean[] parents = new boolean[nodes.size()];
        for (TreeNode node : nodes) {
            check(cancelled);
            if (node.parent()<0) continue;
            parents[node.parent()] = true;
            TreeNode parent = nodes.get(node.parent());
            double radius=Math.max(parent.radius(),node.radius());
            if (config.preset()==TreePreset.WILLOW && config.fullness()>0
                    && isDroopDrop(node,parent) && droopDepth(nodes,node)>=2) {
                double leafRadius=Math.max(radius,.9);
                fill(acc,parent.position(),node.position(),leafRadius,leafRadius,
                        Material.PRIMARY_LEAF,Axis.Y,cancelled,config.seed(),config.blockPalette());
                continue;
            }
            double cap=Math.max(1.0,config.height()*.055);
            double lateral=Math.hypot(parent.position().x()-.5,parent.position().z()-.5);
            Material wood=parent.position().y()<config.height()*.36+1 && lateral<=cap+.8
                    ? Material.MAIN_TRUNK : radius>=.7 ? Material.THICK_BRANCH : Material.THIN_BRANCH;
            Axis axis=axis(parent.position(),node.position());
            fill(acc,parent.position(),node.position(),parent.radius(),node.radius(),wood,axis,cancelled,config.seed(),config.blockPalette());
            connectSpine(acc,parent.position(),node.position(),wood,axis);
        }
        if (config.preset() != TreePreset.DEAD && config.fullness()>0) {
            double radius = 1.2+config.fullness()*1.8;
            for (int i=0; i<nodes.size(); i++) if (!parents[i]) {
                Vector3 p = nodes.get(i).position();
                double leafRadius=radius*(.72+.5*tipHash(i,config.seed()));
                if (config.preset()==TreePreset.WILLOW && nodes.get(i).parent()>=0
                        && p.y()<nodes.get(nodes.get(i).parent()).position().y())
                    leafRadius*=.45;
                fill(acc,p,p,leafRadius,leafRadius,Material.PRIMARY_LEAF,Axis.Y,cancelled,config.seed(),config.blockPalette());
            }
        }
        return new VoxelData(Collections.unmodifiableMap(acc.blocks()),Collections.unmodifiableMap(acc.axes()));
    }
    /** True for the straight-down, same-column edges the willow tail-growth appends below each crown tip. */
    private static boolean isDroopDrop(TreeNode node, TreeNode parent) {
        return Math.abs(node.position().x()-parent.position().x())<1e-9
                && Math.abs(node.position().z()-parent.position().z())<1e-9
                && node.position().y()<parent.position().y()-.5;
    }
    /** How many consecutive drop-edges lead up to {@code node} from the crown tip it hangs off of. */
    private static int droopDepth(List<TreeNode> nodes, TreeNode node) {
        int depth=0;
        TreeNode current=node;
        while (current.parent()>=0) {
            TreeNode parent=nodes.get(current.parent());
            if (!isDroopDrop(current,parent)) break;
            depth++; current=parent;
        }
        return depth;
    }
    private static double tipHash(int i,long seed) {
        long h=seed ^ (i*0x9E3779B97F4A7C15L); h=(h^(h>>>30))*0xBF58476D1CE4E5B9L; h=(h^(h>>>27))*0x94D049BB133111EBL;
        return ((h^(h>>>31))>>>11)*0x1.0p-53;
    }
    private static double cellHash(long a,long b,long c,long seed) {
        long h=seed ^ (a*0x9E3779B97F4A7C15L) ^ (b*0xC2B2AE3D27D4EB4FL) ^ (c*0x165667B19E3779F9L);
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L; h=(h^(h>>>27))*0x94D049BB133111EBL;
        return ((h^(h>>>31))>>>11)*0x1.0p-53;
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
    private void fill(VoxelAccumulator acc, Vector3 a, Vector3 b, double r1, double r2,
                      Material material,Axis axis, BooleanSupplier cancelled,long seed,BlockPalette palette) {
        double r = Math.max(r1,r2);
        for (int x=(int)Math.floor(Math.min(a.x(),b.x())-r); x<=Math.ceil(Math.max(a.x(),b.x())+r); x++) {
            check(cancelled);
            for (int y=Math.max(0,(int)Math.floor(Math.min(a.y(),b.y())-r)); y<=Math.ceil(Math.max(a.y(),b.y())+r); y++)
                for (int z=(int)Math.floor(Math.min(a.z(),b.z())-r); z<=Math.ceil(Math.max(a.z(),b.z())+r); z++) {
                    if (!contains(new Vector3(x+.5,y+.5,z+.5),a,b,r1,r2)) continue;
                    Cell cell = new Cell(x,y,z);
                    if (!material.woody()) {
                        double dx=x+.5-a.x(),dy=y+.5-a.y(),dz=z+.5-a.z();
                        if (Math.sqrt(dx*dx+dy*dy+dz*dz) > r*(.78+.3*cellHash(x,y,z,seed^0x5bd1e995L))) continue;
                        acc.putLeaf(cell, leafMaterial(cell,seed,palette));
                    } else acc.putWood(cell, material, axis);
                }
        }
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    /** A radius-0.5 diagonal can miss face-adjacent voxel centres. Keep a connected digital spine. */
    private static Material leafMaterial(Cell cell,long seed,BlockPalette palette) {
        double cellU=cellHash(cell.x(),cell.y(),cell.z(),seed);
        double patchU=cellHash(Math.floorDiv(cell.x(),3),Math.floorDiv(cell.y(),3),Math.floorDiv(cell.z(),3),seed^0x77L);
        double sample=cellHash(cell.x(),cell.y(),cell.z(),seed^0x1234L)<.6 ? patchU : cellU;
        if(sample<palette.primaryWeight()) return Material.PRIMARY_LEAF;
        if(sample<palette.primaryWeight()+palette.secondaryWeight()) return Material.SECONDARY_LEAF;
        return Material.ACCENT;
    }
    static void connectSpineForTest(VoxelAccumulator acc,Vector3 a,Vector3 b,Material wood,Axis axis) {
        connectSpine(acc,a,b,wood,axis);
    }
    private static void connectSpine(VoxelAccumulator acc,Vector3 a,Vector3 b,Material wood,Axis axis) {
        int x=(int)Math.floor(a.x()),y=(int)Math.floor(a.y()),z=(int)Math.floor(a.z());
        acc.putWood(new Cell(x,y,z),wood,axis);
        Vector3 delta=b.subtract(a);
        int steps=Math.max(1,(int)Math.ceil(Math.sqrt(delta.lengthSquared())*4));
        for(int i=1;i<=steps;i++) {
            Vector3 p=a.add(delta.multiply(i/(double)steps));
            int tx=(int)Math.floor(p.x()),ty=(int)Math.floor(p.y()),tz=(int)Math.floor(p.z());
            while(x!=tx) { x+=Integer.signum(tx-x); acc.putWood(new Cell(x,y,z),wood,axis); }
            while(y!=ty) { y+=Integer.signum(ty-y); acc.putWood(new Cell(x,y,z),wood,axis); }
            while(z!=tz) { z+=Integer.signum(tz-z); acc.putWood(new Cell(x,y,z),wood,axis); }
        }
    }
}
