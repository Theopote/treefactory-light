package com.treefactory.light.client.gui.widget;

import com.treefactory.light.client.ClientTreeState;
import com.treefactory.light.client.gui.render.*;
import com.treefactory.light.core.config.PresetStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public final class PresetCardWidget extends Button {
    private final PresetStore.Preset preset;
    public PresetCardWidget(int x,int y,int width,PresetStore.Preset preset,Runnable selected) {
        super(x,y,width,54,Component.literal(preset.name()),button->selected.run(),DEFAULT_NARRATION); this.preset=preset;
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        var state=ClientTreeState.INSTANCE; var font=Minecraft.getInstance().font;
        boolean selected=state.config().equals(preset.config());
        graphics.fill(getX(),getY(),getX()+width,getY()+height,isHovered()?0xFF234A40:0xFF182D27);
        graphics.outline(getX(),getY(),width,height,selected?0xFF69DFB1:0xFF37584D);
        var texture=ThumbnailFBOMaker.texture(preset.id(),state.store.thumbnail(preset.id()));
        var preview=state.preview();
        if(isHovered() && selected && preview!=null) {
            graphics.guiRenderState.addPicturesInPictureState(new TreePictureRenderer.State(preview,(System.nanoTime()%30_000_000_000L)/30_000_000_000f*(float)Math.PI*2,
                    false,getX()+2,getY()+2,getX()+50,getY()+50,(float)(42/preview.extent()),graphics.scissorStack.peek()));
        } else if(texture!=null) graphics.blit(RenderPipelines.GUI_TEXTURED,texture,getX()+3,getY()+3,0,0,48,48,128,128,128,128);
        int textX=getX()+54;
        graphics.text(font,font.plainSubstrByWidth(preset.name(),Math.max(8,width-58)),textX,getY()+12,0xFFF0F8EE);
        graphics.text(font,Component.translatable("treefactory.preset."+preset.config().preset().id()),textX,getY()+30,0xFF9BB7A9);
    }
}
