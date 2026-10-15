package com.treefactory.light.client.gui.widget;

import com.treefactory.light.core.config.CanopyShape;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.EnumMap;
import java.util.Map;

/** Side-view silhouettes sampled from each shape's own field function, so new shapes get icons for free. */
public final class ShapeIcons {
    private static final Map<Integer,Map<CanopyShape,boolean[][]>> MASKS=new java.util.HashMap<>();
    private ShapeIcons() {}

    private static boolean[][] mask(CanopyShape shape,int size) {
        return MASKS.computeIfAbsent(size,s->new EnumMap<>(CanopyShape.class)).computeIfAbsent(shape,sh->{
            boolean[][] rows=new boolean[size][size];
            for(int row=0;row<size;row++) {
                double y=1-(row+.5)*2/size;
                for(int col=0;col<size;col++) {
                    double x=(col+.5)*2/size-1;
                    rows[row][col]=sh.field(x,y,0)<=0;
                }
            }
            return rows;
        });
    }

    public static void draw(GuiGraphicsExtractor g,CanopyShape shape,int x,int y,int size,int color) {
        boolean[][] mask=mask(shape,size);
        for(int row=0;row<size;row++) {
            int start=-1;
            for(int col=0;col<=size;col++) {
                boolean on=col<size && mask[row][col];
                if(on && start<0) start=col;
                else if(!on && start>=0) { g.fill(x+start,y+row,x+col,y+row+1,color); start=-1; }
            }
        }
    }

    /** Enlarged preview with a frame, used while hovering or choosing a shape. */
    public static void drawFramed(GuiGraphicsExtractor g,CanopyShape shape,int x,int y,int size) {
        g.fill(x-3,y-3,x+size+3,y+size+3,0xE00B1814);
        g.outline(x-3,y-3,size+6,size+6,0xFF4C876C);
        draw(g,shape,x,y,size,0xFF83B99D);
    }
}
