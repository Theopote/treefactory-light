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
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class DrawerScreen extends Screen {
    private final ClientTreeState state=ClientTreeState.INSTANCE;
    private final List<AbstractWidget> content=new ArrayList<>(),fixed=new ArrayList<>();
    private int panel,scroll,contentHeight;
    private boolean library;
    private enum Panel { NONE, SHAPES, MATERIALS }
    private Panel expanded=Panel.NONE;
    private boolean rotating;
    private String presetName="My tree";
    private float angle=(float)Math.PI/4,zoom=1;
    private static final String[] WOODS={"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak"};
    private static final CanopyShape[] SHAPE_ORDER={
            CanopyShape.CONE,CanopyShape.TRUNCATED_CONE,CanopyShape.CYLINDER,CanopyShape.SPINDLE,
            CanopyShape.PARABOLOID,CanopyShape.OVOID,CanopyShape.PROLATE_ELLIPSOID,CanopyShape.SPHERE,
            CanopyShape.OBLATE_ELLIPSOID,CanopyShape.OBOVATE,CanopyShape.HEMISPHERE,
            CanopyShape.INVERTED_PARABOLOID,CanopyShape.HYPERBOLOID,CanopyShape.NEILOID,
            CanopyShape.ELLIPSOID,CanopyShape.UMBRELLA,CanopyShape.SPINDLE_MULTI_LAYER
    };
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
            int gap=6, tile=Math.max(1,Math.min((panel-16-gap)/2,height-108)), y=54;
            List<PresetStore.Preset> presets=state.presets();
            contentHeight=Math.max(tile,((presets.size()+1)/2)*(tile+gap));
            scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-(height-106)));
            for(int i=0;i<presets.size();i++) {
                var preset=presets.get(i);
                int x=8+(i%2)*(tile+gap), top=y+(i/2)*(tile+gap)-scroll;
                addContent(new PresetCardWidget(x,top,tile,preset,()->{
                    if(state.update(preset.config())) { library=false; expanded=Panel.NONE; scroll=0; refresh(); }
                }));
            }
        } else {
            TreeConfig c=state.config(); int y=54;
            addContent(button(8,y-scroll,panel-16,"treefactory.scale."+c.scale().id(),()->{state.cycleScale(1);refresh();})); y+=28;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.crookedness",c.crookedness(),v->state.update(state.config().withDna(v,state.config().spread(),state.config().fullness())))); y+=25;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.spread",c.spread(),v->state.update(state.config().withDna(state.config().crookedness(),v,state.config().fullness())))); y+=25;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.fullness",c.fullness(),v->state.update(state.config().withDna(state.config().crookedness(),state.config().spread(),v)))); y+=28;
            Button shapes=button(8,y-scroll,panel-16,"treefactory.choose_shape",()->toggle(Panel.SHAPES));
            shapes.setMessage(Component.translatable("treefactory.choose_shape").append(": ")
                    .append(Component.translatable("treefactory.canopy."+c.canopyShape().name().toLowerCase(Locale.ROOT)))
                    .append(expanded==Panel.SHAPES?" ▲":" ▼"));
            addContent(shapes); y+=23;
            if(expanded==Panel.SHAPES) for(int i=0;i<SHAPE_ORDER.length;i++) {
                CanopyShape shape=SHAPE_ORDER[i];
                Button choice=button(16,y-scroll,panel-24,"treefactory.canopy."+shape.name().toLowerCase(Locale.ROOT),()->{
                    TreeConfig now=state.config(); state.update(now.withCanopy(shape,now.canopyScale(),now.attractionDensity()));
                    expanded=Panel.NONE; refresh();
                });
                Component label=Component.literal(i<14?"A"+(i+1)+" · ":"• ").append(choice.getMessage());
                if(c.canopyShape()==shape) label=Component.literal("✓ ").append(label);
                choice.setMessage(label);
                addContent(choice); y+=23;
            }
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.canopy_scale",(c.canopyScale()-.75)/1.25,
                    v->{TreeConfig now=state.config();state.update(now.withCanopy(now.canopyShape(),.75+1.25*v,now.attractionDensity()));})); y+=25;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.attraction_density",(c.attractionDensity()-.5)/1.5,
                    v->{TreeConfig now=state.config();state.update(now.withCanopy(now.canopyShape(),now.canopyScale(),.5+1.5*v));})); y+=25;
            addContent(new DNAControlSlider(8,y-scroll,panel-16,"treefactory.pipe_exponent",(c.pipeExponent()-2.5)/.5,
                    v->state.update(state.config().withPipeExponent(2.5+.5*v)))); y+=28;
            Button materials=button(8,y-scroll,panel-16,"treefactory.choose_materials",()->toggle(Panel.MATERIALS));
            materials.setMessage(Component.translatable("treefactory.choose_materials").append(expanded==Panel.MATERIALS?" ▲":" ▼"));
            addContent(materials); y+=23;
            if(expanded==Panel.MATERIALS) {
            addContent(paletteButton(y,"treefactory.log",c.blockPalette().mainTrunk(),"","_log",0)); y+=23;
            addContent(paletteButton(y,"treefactory.thick_branch",c.blockPalette().thickBranch(),"stripped_","_log",1)); y+=23;
            addContent(paletteButton(y,"treefactory.thin_branch",c.blockPalette().thinBranch(),"","_planks",2)); y+=23;
            addContent(paletteButton(y,"treefactory.leaves",c.blockPalette().primaryLeaf(),"","_leaves",3)); y+=23;
            addContent(paletteButton(y,"treefactory.secondary_leaf",c.blockPalette().secondaryLeaf(),"","_leaves",4)); y+=23;
            Button accent=button(16,y-scroll,panel-24,"treefactory.accent",()->{
                var p=state.config().blockPalette(); String next=p.accentBlock().equals("minecraft:moss_block")?"minecraft:ochre_froglight":"minecraft:moss_block";
                state.update(state.config().withBlockPalette(p.withBlocks(p.mainTrunk(),p.thickBranch(),p.thinBranch(),p.primaryLeaf(),p.secondaryLeaf(),next)));refresh();
            });
            accent.setMessage(Component.translatable("treefactory.accent").append(": ").append(Component.translatable("block."+c.blockPalette().accentBlock().replace(':','.'))));
            addContent(accent); y+=23;
            Button weights=button(16,y-scroll,panel-24,"treefactory.leaf_weights",()->{
                var p=state.config().blockPalette();
                double[][] choices={{.8,.15,.05},{.65,.25,.10},{.95,.05,0}};
                int index=p.primaryWeight()==.8?1:p.primaryWeight()==.65?2:0;
                state.update(state.config().withBlockPalette(p.withWeights(choices[index][0],choices[index][1],choices[index][2])));refresh();
            });
            var weightsNow=c.blockPalette();
            weights.setMessage(Component.translatable("treefactory.leaf_weights",
                    (int)Math.round(weightsNow.primaryWeight()*100),(int)Math.round(weightsNow.secondaryWeight()*100),
                    (int)Math.round(weightsNow.accentWeight()*100)));
            addContent(weights); y+=23;
            }
            addContent(button(8,y-scroll,panel-16,"treefactory.new_seed",()->state.update(state.config().withSeed(new Random().nextLong())))); y+=28;
            EditBox name=new EditBox(font,8,y-scroll,panel-16,20,Component.translatable("treefactory.preset_name"));
            name.setMaxLength(64); name.setValue(presetName); name.setResponder(value->presetName=value); addContent(name); y+=23;
            addContent(button(8,y-scroll,panel-16,"treefactory.save",()->state.save(presetName))); y+=24;
            contentHeight=y-54;
        }
        int bounded=Math.clamp(scroll,0,Math.max(0,contentHeight-(height-106)));
        if(bounded!=scroll) {
            int shift=scroll-bounded;
            for(AbstractWidget widget:content) {
                widget.setY(widget.getY()+shift);
                widget.visible=widget.getY()>=50 && widget.getY()+widget.getHeight()<=height-52;
            }
            scroll=bounded;
        }
    }
    private void addContent(AbstractWidget widget) {
        widget.visible=widget.getY()>=50 && widget.getY()+widget.getHeight()<=height-52;
        content.add(addWidget(widget));
    }
    private Button button(int x,int y,int width,String key,Runnable action) {
        return Button.builder(Component.translatable(key),b->action.run()).bounds(x,y,width,20).build();
    }
    private void toggle(Panel panel) { expanded=expanded==panel?Panel.NONE:panel; refresh(); }
    private Button paletteButton(int y,String key,String current,String prefix,String suffix,int role) {
        Button choice=button(16,y-scroll,panel-24,key,()->{
            var p=state.config().blockPalette();
            String next=nextBlock(current,prefix,suffix);
            state.update(state.config().withBlockPalette(p.withBlocks(
                    role==0?next:p.mainTrunk(),role==1?next:p.thickBranch(),role==2?next:p.thinBranch(),
                    role==3?next:p.primaryLeaf(),role==4?next:p.secondaryLeaf(),role==5?next:p.accentBlock())));
            refresh();
        });
        choice.setMessage(Component.translatable(key).append(": ").append(Component.translatable("block."+current.replace(':','.'))));
        return choice;
    }
    private String nextBlock(String current,String prefix,String suffix) {
        int index=-1;
        for(int i=0;i<WOODS.length;i++) if(current.equals("minecraft:"+prefix+WOODS[i]+suffix)) index=i;
        return "minecraft:"+prefix+WOODS[(index+1)%WOODS.length]+suffix;
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
        Component target=state.origin()==null ? Component.translatable("treefactory.aim_first") : Component.translatable("treefactory.target", Objects.requireNonNull(state.origin()).toShortString());
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
        if(event.x()>=panel && event.button()==InputConstants.MOUSE_BUTTON_LEFT) { rotating=true; return true; }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if(rotating && event.button()==InputConstants.MOUSE_BUTTON_LEFT) { angle+=(float)dx*.012f; return true; }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if(rotating && event.button()==InputConstants.MOUSE_BUTTON_LEFT) { rotating=false; return true; }
        return super.mouseReleased(event);
    }
    @Override public void extractBackground(@NonNull GuiGraphicsExtractor g, int x, int y, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
