package com.treefactory.light.client.gui;

import com.treefactory.light.client.*;
import com.treefactory.light.client.gui.render.TreePictureRenderer;
import com.treefactory.light.client.gui.widget.*;
import com.treefactory.light.core.config.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;

public final class DrawerScreen extends Screen {
    private final ClientTreeState state=ClientTreeState.INSTANCE;
    private final List<AbstractWidget> content=new ArrayList<>(),fixed=new ArrayList<>();
    private int panel,scroll,contentHeight;
    private boolean library;
    private String presetName="My tree";
    private float angle=(float)Math.PI/4,zoom=1;
    private static final String[] WOODS={"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak"};
    public DrawerScreen() { super(Component.literal("Treefactory Light")); }
    public void refresh() { rebuildWidgets(); }
    @Override protected void init() {
        panel=Math.max(64,width/4); content.clear(); fixed.clear();
        fixed.add(addWidget(button(8,24,(panel-20)/2,"treefactory.generator",()->{library=false;scroll=0;refresh();})));
        fixed.add(addWidget(button(12+(panel-20)/2,24,(panel-20)/2,"treefactory.library",()->{library=true;scroll=0;refresh();})));
        fixed.add(addWidget(button(8,height-47,panel-16,"treefactory.build",state::build)));
        fixed.add(addWidget(button(8,height-24,(panel-20)/2,"treefactory.undo",state::undo)));
        fixed.add(addWidget(button(12+(panel-20)/2,height-24,(panel-20)/2,"gui.done",this::onClose)));
        if(library) {
            int y=54;
            for(var preset:state.presets()) {
                addContent(new PresetCardWidget(8,y-scroll,panel-16,preset,()->{state.update(preset.config());refresh();})); y+=60;
            }
            contentHeight=Math.max(60,y-54);
        } else {
            TreeConfig c=state.config(); int y=54;
            for(TreePreset preset:TreePreset.values()) {
                Button choice=button(8,y-scroll,panel-16,"treefactory.preset."+preset.id(),()->{state.update(state.config().withPreset(preset));refresh();});
                if(c.preset()==preset) choice.setMessage(Component.literal("• ").append(choice.getMessage()));
                addContent(choice); y+=23;
            }
            addContent(button(8,y-scroll,panel-16,"treefactory.scale."+c.scale().id(),()->{state.cycleScale(1);refresh();})); y+=28;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.crookedness",c.crookedness(),v->state.update(state.config().withDna(v,state.config().spread(),state.config().fullness())))); y+=25;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.spread",c.spread(),v->state.update(state.config().withDna(state.config().crookedness(),v,state.config().fullness())))); y+=25;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.fullness",c.fullness(),v->state.update(state.config().withDna(state.config().crookedness(),state.config().spread(),v)))); y+=28;
            Button log=button(8,y-scroll,panel-16,"treefactory.log",()->{state.update(state.config().withPalette(nextBlock(state.config().logBlock(),"_log"),state.config().leavesBlock()));refresh();});
            log.setMessage(Component.translatable("block."+c.logBlock().replace(':','.'))); addContent(log); y+=23;
            Button leaf=button(8,y-scroll,panel-16,"treefactory.leaves",()->{state.update(state.config().withPalette(state.config().logBlock(),nextBlock(state.config().leavesBlock(),"_leaves")));refresh();});
            leaf.setMessage(Component.translatable("block."+c.leavesBlock().replace(':','.'))); addContent(leaf); y+=23;
            addContent(button(8,y-scroll,panel-16,"treefactory.new_seed",()->state.update(state.config().withSeed(new Random().nextLong())))); y+=28;
            EditBox name=new EditBox(font,8,y-scroll,panel-16,20,Component.translatable("treefactory.preset_name"));
            name.setMaxLength(64); name.setValue(presetName); name.setResponder(value->presetName=value); addContent(name); y+=23;
            addContent(button(8,y-scroll,panel-16,"treefactory.save",()->state.save(presetName))); y+=24;
            contentHeight=y-54;
        }
    }
    private void addContent(AbstractWidget widget) {
        widget.visible=widget.getY()>=50 && widget.getY()+widget.getHeight()<=height-52;
        content.add(addWidget(widget));
    }
    private Button button(int x,int y,int width,String key,Runnable action) {
        return Button.builder(Component.translatable(key),b->action.run()).bounds(x,y,width,20).build();
    }
    private String nextBlock(String current,String suffix) {
        int index=-1;
        for(int i=0;i<WOODS.length;i++) if(current.equals("minecraft:"+WOODS[i]+suffix)) index=i;
        return "minecraft:"+WOODS[(index+1)%WOODS.length]+suffix;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,panel,height,0xF010201B); g.fill(panel-1,0,panel,height,0xFF4C876C);
        g.text(font,font.plainSubstrByWidth("Treefactory Light",panel-16),8,8,0xFFB8F8D2);
        g.enableScissor(0,50,panel,height-51);
        for(AbstractWidget widget:content) widget.extractRenderState(g,mouseX,mouseY,delta);
        if(library && state.presets().isEmpty()) g.textWithWordWrap(font,Component.translatable("treefactory.empty_library"),8,58,panel-16,0xFFB0C9BC);
        g.disableScissor();
        for(AbstractWidget widget:fixed) widget.extractRenderState(g,mouseX,mouseY,delta);
        if(contentHeight>height-106) {
            int track=Math.max(1,height-110),thumb=Math.max(12,track*track/contentHeight);
            int y=53+(int)((track-thumb)*scroll/(double)Math.max(1,contentHeight-track));
            g.fill(panel-4,53,panel-2,height-55,0xFF274237); g.fill(panel-4,y,panel-2,y+thumb,0xFF83B99D);
        }
        var preview=state.preview();
        if(preview!=null) {
            int x0=panel+12,y0=30,x1=width-12,y1=height-54;
            float scale=(float)(Math.min(x1-x0,y1-y0)*.88/preview.extent())*zoom;
            g.guiRenderState.addPicturesInPictureState(new TreePictureRenderer.State(preview,angle,true,x0,y0,x1,y1,scale,null));
        }
        g.text(font,Component.translatable("treefactory.preview_hint"),panel+12,10,0xFFE2F5E9);
        g.text(font,state.status(),panel+12,height-39,0xFF9CE7BD);
        Component target=state.origin()==null ? Component.translatable("treefactory.aim_first") : Component.translatable("treefactory.target",state.origin().toShortString());
        g.text(font,target,panel+12,height-24,state.origin()==null?0xFFFFC580:0xFFB5CDBF);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(x<panel && y>=50 && y<height-51) {
            scroll=Math.clamp(scroll-(int)(vertical*24),0,Math.max(0,contentHeight-(height-106))); refresh(); return true;
        }
        if(x>=panel) { zoom=Math.clamp(zoom+(float)vertical*.08f,.35f,2); return true; }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT && event.hasShiftDown()) { onClose(); return true; }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(event.x()>=panel && event.button()==InputConstants.MOUSE_BUTTON_LEFT) { angle+=(float)dx*.012f; return true; }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
