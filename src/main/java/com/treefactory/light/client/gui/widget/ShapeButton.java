package com.treefactory.light.client.gui.widget;

import com.treefactory.light.core.config.CanopyShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Button with a canopy silhouette icon in front of its label. */
public final class ShapeButton extends Button {
    public static final int ICON=14;
    private final CanopyShape shape;
    public ShapeButton(int x,int y,int width,Component label,CanopyShape shape,Runnable action) {
        super(x,y,width,20,label,b->action.run(),DEFAULT_NARRATION);
        this.shape=shape;
    }
    public CanopyShape shape() { return shape; }
    @Override protected void extractContents(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractDefaultSprite(g);
        var font=Minecraft.getInstance().font;
        ShapeIcons.draw(g,shape,getX()+4,getY()+(height-ICON)/2,ICON,0xFFB8F8D2);
        int left=getX()+4+ICON+4;
        String text=font.plainSubstrByWidth(getMessage().getString(),width-(left-getX())-4);
        g.text(font,text,left,getY()+(height-8)/2,active?0xFFFFFFFF:0xFFA0A0A0);
    }
}
