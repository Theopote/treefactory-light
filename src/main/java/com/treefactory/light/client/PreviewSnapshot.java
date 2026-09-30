package com.treefactory.light.client;

import com.treefactory.light.core.algorithm.Voxelizer;
import com.treefactory.light.core.config.TreeConfig;
import com.treefactory.light.core.tree.*;
import java.util.*;

public record PreviewSnapshot(TreeConfig config, TreeBuildTask.Result tree, List<Face> faces,
                              List<Cell> surface, double centerY, double extent) {
    public record Face(Cell cell, int side, boolean leaves) {}
    public static final int[][] OFFSETS={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    public static PreviewSnapshot create(TreeConfig config,TreeBuildTask.Result tree) {
        List<Face> faces=new ArrayList<>(); List<Cell> surface=new ArrayList<>();
        int minX=0,maxX=0,minZ=0,maxZ=0,maxY=0;
        for(var entry:tree.blocks().entrySet()) {
            Cell c=entry.getKey(); boolean visible=false;
            for(int side=0;side<6;side++) {
                int[] d=OFFSETS[side];
                var neighbor=tree.blocks().get(new Cell(c.x()+d[0],c.y()+d[1],c.z()+d[2]));
                if(neighbor==null || entry.getValue()==Voxelizer.Material.LOG && neighbor==Voxelizer.Material.LEAVES) {
                    faces.add(new Face(c,side,entry.getValue()==Voxelizer.Material.LEAVES)); visible=true;
                }
            }
            if(visible) surface.add(c);
            minX=Math.min(minX,c.x()); maxX=Math.max(maxX,c.x()); minZ=Math.min(minZ,c.z()); maxZ=Math.max(maxZ,c.z()); maxY=Math.max(maxY,c.y()+1);
        }
        // Conservative rotating bounding sphere fit, including the crown width and branch thickness.
        double horizontal=Math.max(Math.max(Math.abs(minX),maxX+1),Math.max(Math.abs(minZ),maxZ+1));
        double radius=Math.sqrt(horizontal*horizontal*2+maxY*maxY*.25);
        return new PreviewSnapshot(config,tree,List.copyOf(faces),List.copyOf(surface),maxY*.5,Math.max(1,radius*2));
    }
}
