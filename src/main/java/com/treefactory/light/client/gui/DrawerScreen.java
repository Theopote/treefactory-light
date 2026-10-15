package com.treefactory.light.client.gui;

import com.treefactory.light.client.*;
import com.treefactory.light.client.gui.widget.*;
import com.treefactory.light.core.config.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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
    private enum Panel { NONE, SHAPES }
    private Panel expanded=Panel.NONE;
    private final MaterialPanel materials=new MaterialPanel();
    private final List<AbstractWidget> materialWidgets=new ArrayList<>();
    private boolean materialsOpen;
    private int materialLeft,materialWidth;
    private boolean looking;
    private double mouseX,lookX,lookY;
    private String presetName="My tree";
    private static final CanopyShape[] SHAPE_ORDER={
            CanopyShape.CONE,CanopyShape.TRUNCATED_CONE,CanopyShape.CYLINDER,CanopyShape.SPINDLE,
            CanopyShape.PARABOLOID,CanopyShape.OVOID,CanopyShape.PROLATE_ELLIPSOID,CanopyShape.SPHERE,
            CanopyShape.OBLATE_ELLIPSOID,CanopyShape.OBOVATE,CanopyShape.HEMISPHERE,
            CanopyShape.INVERTED_PARABOLOID,CanopyShape.HYPERBOLOID,CanopyShape.NEILOID,
            CanopyShape.ELLIPSOID,CanopyShape.UMBRELLA,CanopyShape.SPINDLE_MULTI_LAYER
    };
    public DrawerScreen() { super(Component.literal("Treefactory Light")); }
    public void refresh() { rebuildWidgets(); }
    private static final int M=6, TOP=44, TITLE_H=16;
    private int bottom() { return height-28; }
    private int viewport() { return Math.max(0,bottom()-TOP); }
    @Override protected void init() {
        panel=Math.max(64,width/4); content.clear(); fixed.clear(); materialWidgets.clear();
        materialWidth=MaterialPanel.widthFor(width); materialLeft=panel;
        int w=panel-2*M-3, half=Math.max(1,(w-2)/2);
        fixed.add(addWidget(button(M,TITLE_H+4,half,"treefactory.generator",()->{library=false;scroll=0;refresh();})));
        fixed.add(addWidget(button(M+half+2,TITLE_H+4,half,"treefactory.library",()->{library=true;scroll=0;refresh();})));
        fixed.add(addWidget(button(M,height-24,half,"treefactory.new_seed",()->state.update(state.config().withSeed(new Random().nextLong())))));
        fixed.add(addWidget(button(M+half+2,height-24,half,"treefactory.done",()->{ if(state.finish()) onClose(); })));
        if(library) {
            int gap=3, tile=Math.max(1,Math.min((w-gap)/2,viewport())), y=TOP;
            List<PresetStore.Preset> presets=state.presets();
            contentHeight=Math.max(tile,((presets.size()+1)/2)*(tile+gap)-gap);
            scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-viewport()));
            for(int i=0;i<presets.size();i++) {
                var preset=presets.get(i);
                int x=M+(i%2)*(tile+gap), top=y+(i/2)*(tile+gap)-scroll;
                addContent(new PresetCardWidget(x,top,tile,preset,()->{
                    if(state.update(preset.config())) refresh();
                }));
            }
        } else {
            TreeConfig c=state.config(); int y=TOP;
            addContent(new ScaleSlider(M,y-scroll,w,c.height(),h->state.update(state.config().withHeight(h)))); y+=24;
            addContent(new DNAControlSlider(M,y-scroll,w,"treefactory.crookedness",c.crookedness(),v->state.update(state.config().withDna(v,state.config().spread(),state.config().fullness())))); y+=22;
            addContent(new DNAControlSlider(M,y-scroll,w,"treefactory.spread",c.spread(),v->state.update(state.config().withDna(state.config().crookedness(),v,state.config().fullness())))); y+=22;
            addContent(new DNAControlSlider(M,y-scroll,w,"treefactory.fullness",c.fullness(),v->state.update(state.config().withDna(state.config().crookedness(),state.config().spread(),v)))); y+=24;
            Button shapes=button(M,y-scroll,w,"treefactory.choose_shape",()->toggle(Panel.SHAPES));
            shapes.setMessage(Component.translatable("treefactory.choose_shape").append(": ")
                    .append(Component.translatable("treefactory.canopy."+c.canopyShape().name().toLowerCase(Locale.ROOT)))
                    .append(expanded==Panel.SHAPES?" ▲":" ▼"));
            addContent(shapes); y+=22;
            if(expanded==Panel.SHAPES) for(CanopyShape shape:SHAPE_ORDER) {
                Button choice=button(M+8,y-scroll,w-8,"treefactory.canopy."+shape.name().toLowerCase(Locale.ROOT),()->{
                    TreeConfig now=state.config(); state.update(now.withCanopy(shape,now.canopyScale(),now.attractionDensity()));
                    expanded=Panel.NONE; refresh();
                });
                if(c.canopyShape()==shape) choice.setMessage(Component.literal("✓ ").append(choice.getMessage()));
                addContent(choice); y+=22;
            }
            addContent(new DNAControlSlider(M,y-scroll,w,"treefactory.canopy_scale",(c.canopyScale()-.75)/1.25,
                    v->{TreeConfig now=state.config();state.update(now.withCanopy(now.canopyShape(),.75+1.25*v,now.attractionDensity()));})); y+=22;
            addContent(new DNAControlSlider(M,y-scroll,w,"treefactory.attraction_density",(c.attractionDensity()-.5)/1.5,
                    v->{TreeConfig now=state.config();state.update(now.withCanopy(now.canopyShape(),now.canopyScale(),.5+1.5*v));})); y+=22;
            addContent(new DNAControlSlider(M,y-scroll,w,"treefactory.pipe_exponent",(c.pipeExponent()-2.5)/.5,
                    v->state.update(state.config().withPipeExponent(2.5+.5*v)))); y+=24;
            Button materials=button(M,y-scroll,w,"treefactory.choose_materials",()->{ materialsOpen=!materialsOpen; expanded=Panel.NONE; refresh(); });
            materials.setMessage(Component.translatable("treefactory.choose_materials").append(materialsOpen?" ▶":" ▼"));
            addContent(materials); y+=24;
            EditBox name=new EditBox(font,M,y-scroll,w,20,Component.translatable("treefactory.preset_name"));
            name.setMaxLength(64); name.setValue(presetName); name.setResponder(value->presetName=value); addContent(name); y+=22;
            addContent(button(M,y-scroll,w,"treefactory.save",()->state.save(presetName))); y+=20;
            contentHeight=y-TOP;
        }
        if(materialsOpen && !library) materials.init(font,materialLeft,materialWidth,height,w2->materialWidgets.add(addWidget(w2)),
                ()->{ materialsOpen=false; refresh(); },this::refresh);
        int bounded=Math.clamp(scroll,0,Math.max(0,contentHeight-viewport()));
        if(bounded!=scroll) {
            int shift=scroll-bounded;
            for(AbstractWidget widget:content) {
                widget.setY(widget.getY()+shift);
                widget.visible=inContentViewport(widget);
            }
            scroll=bounded;
        }
    }
    private void addContent(AbstractWidget widget) {
        widget.visible=inContentViewport(widget);
        content.add(addWidget(widget));
    }
    private boolean inContentViewport(AbstractWidget widget) {
        return widget.getY()<bottom() && widget.getY()+widget.getHeight()>TOP;
    }
    private static final java.util.Set<String> TIPPED=java.util.Set.of("treefactory.generator","treefactory.library",
            "treefactory.done","treefactory.choose_shape","treefactory.choose_materials","treefactory.new_seed","treefactory.save");
    private Button button(int x,int y,int width,String key,Runnable action) {
        Button b=Button.builder(Component.translatable(key),btn->action.run()).bounds(x,y,width,20).build();
        if(TIPPED.contains(key)) b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(key+".tip")));
        return b;
    }
    private void toggle(Panel panel) { expanded=expanded==panel?Panel.NONE:panel; refresh(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,panel,height,0xF010201B); g.fill(panel-1,0,panel,height,0xFF4C876C);
        g.fill(0,0,panel-1,TITLE_H,0xFF3C3F3E);
        g.text(font,font.plainSubstrByWidth("Treefactory Light",panel-2*M),M,4,0xFFB8F8D2);
        g.enableScissor(0,TOP,panel,bottom());
        for(AbstractWidget widget:content) widget.extractRenderState(g,mouseX,mouseY,delta);
        if(library && state.presets().isEmpty()) g.textWithWordWrap(font,Component.translatable("treefactory.empty_library"),M,TOP+4,panel-2*M,0xFFB0C9BC);
        g.disableScissor();
        for(AbstractWidget widget:fixed) widget.extractRenderState(g,mouseX,mouseY,delta);
        if(contentHeight>viewport()) {
            int track=Math.max(1,viewport()),thumb=Math.max(12,track*track/contentHeight);
            int y=TOP+(int)((track-thumb)*scroll/(double)Math.max(1,contentHeight-track));
            g.fill(panel-4,TOP,panel-2,bottom(),0xFF274237); g.fill(panel-4,y,panel-2,y+thumb,0xFF83B99D);
        }
        if(materialsOpen && !library) {
            materials.render(g,font,mouseX,mouseY);
            for(AbstractWidget widget:materialWidgets) widget.extractRenderState(g,mouseX,mouseY,delta);
        }
        int infoX=(materialsOpen && !library ? materialLeft+materialWidth : panel)+10;
        Component info=Component.literal(state.status()).append("  ·  ").append(Component.translatable("treefactory.preview_hint"));
        g.text(font,info,infoX,6,0xFFE2F5E9);
    }
    private boolean insidePanel(double x) { return x<panel || (materialsOpen && !library && x<materialLeft+materialWidth); }
    private static final Set<KeyMapping> HELD=new HashSet<>();
    private void releaseMovement() {
        for(KeyMapping key:HELD) key.setDown(false);
        HELD.clear();
    }
    private KeyMapping[] movementKeys() {
        var o=minecraft.options;
        return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint};
    }
    @Override public void mouseMoved(double x,double y) {
        mouseX=x;
        if(insidePanel(x)) releaseMovement();
        super.mouseMoved(x,y);
    }
    @Override public boolean keyPressed(@NonNull KeyEvent event) {
        if(!insidePanel(mouseX) && !(getFocused() instanceof EditBox) && event.key()!=InputConstants.KEY_ESCAPE) {
            for(KeyMapping key:movementKeys()) if(key.matches(event)) { key.setDown(true); HELD.add(key); return true; }
        }
        return super.keyPressed(event);
    }
    @Override public boolean keyReleased(@NonNull KeyEvent event) {
        for(KeyMapping key:movementKeys()) if(key.matches(event)) { key.setDown(false); HELD.remove(key); }
        return super.keyReleased(event);
    }
    @Override public void removed() {
        releaseMovement(); endLook();
        super.removed();
    }
    private void endLook() {
        if(!looking) return;
        looking=false;
        InputConstants.releaseMouse(minecraft.getWindow(),lookX,lookY);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(materialsOpen && !library && materials.scrolled(x,y,vertical)) return true;
        if(x<panel && y>=TOP && y<bottom()) {
            int next=Math.clamp(scroll-(int)(vertical*24),0,Math.max(0,contentHeight-viewport()));
            int shift=scroll-next;
            for(AbstractWidget widget:content) {
                widget.setY(widget.getY()+shift);
                widget.visible=inContentViewport(widget);
            }
            scroll=next; return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(insidePanel(event.x())) {
            if(super.mouseClicked(event,doubleClick)) return true;
            if(materialsOpen && !library && event.button()==InputConstants.MOUSE_BUTTON_LEFT && materials.click(event.x(),event.y())) return true;
            return true;
        }
        if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT) { state.placeAtCursor(event.x(),event.y()); return true; }
        if(event.button()==InputConstants.MOUSE_BUTTON_MIDDLE) {
            looking=true;
            lookX=minecraft.mouseHandler.xpos(); lookY=minecraft.mouseHandler.ypos();
            InputConstants.grabMouse(minecraft.getWindow(),lookX,lookY);
            return true;
        }
        return true;
    }
    @Override public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if(looking && event.button()==InputConstants.MOUSE_BUTTON_MIDDLE && minecraft.player!=null) {
            double scale=minecraft.getWindow().getGuiScale();
            double sens=minecraft.options.sensitivity().get()*.6+.2; sens=sens*sens*sens*8;
            minecraft.player.turn(dx*scale*sens,dy*scale*sens);
            return true;
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if(looking && event.button()==InputConstants.MOUSE_BUTTON_MIDDLE) { endLook(); return true; }
        return super.mouseReleased(event);
    }
    @Override public void extractBackground(@NonNull GuiGraphicsExtractor g, int x, int y, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
