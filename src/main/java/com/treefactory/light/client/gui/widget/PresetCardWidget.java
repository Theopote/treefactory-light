package com.treefactory.light.client.gui.widget;

import com.treefactory.light.client.ClientTreeState;
import com.treefactory.light.client.gui.render.*;
import com.treefactory.light.core.config.PresetStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public final class PresetCardWidget extends Button {
    private final PresetStore.Preset preset;
    public PresetCardWidget(int x,int y,int width,PresetStore.Preset preset,Runnable selected) {
        super(x,y,width,width,Component.literal(preset.name()),button->selected.run(),DEFAULT_NARRATION);
        this.preset=preset;
        Component name=preset.id().startsWith("builtin-")
                ? Component.translatable("treefactory.builtin."+preset.id().substring("builtin-".length()))
                : Component.literal(preset.name());
        setTooltip(Tooltip.create(name.copy().append("\n")
                .append(Component.translatable("treefactory.preset."+preset.config().preset().id()))));
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        var state=ClientTreeState.INSTANCE;
        boolean selected=state.config().equals(preset.config());
        graphics.fill(getX(),getY(),getX()+width,getY()+height,isHovered()?0xFF234A40:0xFF182D27);
        graphics.outline(getX(),getY(),width,height,selected?0xFF69DFB1:0xFF37584D);
        var texture=ThumbnailFBOMaker.texture(preset.id(),state.store.thumbnail(preset.id()));
        var preview=state.preview();
        ThumbnailFBOMaker.ensure(preset,selected?preview:null,state.store.thumbnail(preset.id()));
        if(isHovered() && selected && preview!=null) {
            graphics.guiRenderState.addPicturesInPictureState(new TreePictureRenderer.State(preview,(System.nanoTime()%30_000_000_000L)/30_000_000_000f*(float)Math.PI*2,
                    false,getX()+3,getY()+3,getX()+width-3,getY()+height-3,(float)((width-6)/preview.extent()),graphics.scissorStack.peek()));
        } else if(texture!=null) graphics.blit(RenderPipelines.GUI_TEXTURED,texture,getX()+3,getY()+3,0,0,width-6,height-6,128,128,128,128);
    }
}
