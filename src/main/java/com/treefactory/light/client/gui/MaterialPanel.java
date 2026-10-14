package com.treefactory.light.client.gui;

import com.treefactory.light.client.ClientTreeState;
import com.treefactory.light.client.gui.widget.DNAControlSlider;
import com.treefactory.light.core.config.BlockPalette;
import com.treefactory.light.core.config.TreePreset;
import com.treefactory.light.util.PaletteRules;
import com.treefactory.light.util.PaletteRules.Role;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.function.Consumer;

/**
 * Side panel next to the drawer for choosing the block of every tree role. It is not a separate Screen on purpose:
 * the live preview is cancelled whenever the drawer is closed, and the world must stay visible while choosing.
 */
final class MaterialPanel {
    private static final Role[] ROLES=Role.values();
    private static final String[] ROLE_KEYS={"treefactory.role.trunk","treefactory.role.thick_branch","treefactory.role.thin_branch",
            "treefactory.role.primary_leaf","treefactory.role.secondary_leaf","treefactory.role.accent"};
    private static final int CELL=20, GRID_TOP=184;
    private static final Map<Role,List<Block>> CANDIDATES=new EnumMap<>(Role.class);

    private final ClientTreeState state=ClientTreeState.INSTANCE;
    private Role role=Role.TRUNK;
    private String query="";
    private int scroll;
    private int x, width, height, gridBottom;
    private Block hovered;
    private Runnable rebuild;

    static int widthFor(int screenWidth) { return Math.clamp(screenWidth/3,200,280); }

    void init(Font font,int left,int width,int height,Consumer<AbstractWidget> add,Runnable close,Runnable rebuild) {
        this.x=left; this.width=width; this.height=height; this.gridBottom=height-96;
        this.rebuild=rebuild;
        add.accept(Button.builder(Component.literal("✕"),b->close.run()).bounds(left+width-26,6,20,18).build());
        for(int i=0;i<ROLES.length;i++) {
            Role r=ROLES[i];
            Button row=Button.builder(rowLabel(i),b->{ role=r; scroll=0; rebuild.run(); }).bounds(left+8,28+i*22,width-16-24,20).build();
            add.accept(row);
        }
        EditBox search=new EditBox(font,left+8,GRID_TOP-24,width-16,18,Component.translatable("treefactory.materials.search"));
        search.setHint(Component.translatable("treefactory.materials.search"));
        search.setMaxLength(48); search.setValue(query);
        search.setResponder(value->{ query=value.toLowerCase(Locale.ROOT).trim(); scroll=0; });
        add.accept(search);
        var palette=state.config().blockPalette();
        add.accept(new DNAControlSlider(left+8,height-76,width-16,"treefactory.secondary_weight",palette.secondaryWeight(),
                v->setWeights(v,state.config().blockPalette().accentWeight())));
        add.accept(new DNAControlSlider(left+8,height-52,width-16,"treefactory.accent_weight",palette.accentWeight(),
                v->setWeights(state.config().blockPalette().secondaryWeight(),v)));
        add.accept(Button.builder(Component.translatable("treefactory.materials.reset"),b->{
            state.update(state.config().withBlockPalette(defaults())); rebuild.run();
        }).bounds(left+8,height-28,width-16,20).build());
    }

    private BlockPalette defaults() {
        return BlockPalette.defaults(state.config().preset()==TreePreset.PINE ? "spruce" : "oak");
    }
    private void setWeights(double secondary,double accent) {
        var p=state.config().blockPalette();
        double s=Math.clamp(secondary,0,1), a=Math.clamp(accent,0,1-s);
        state.update(state.config().withBlockPalette(p.withWeights(1-s-a,s,a)));
    }
    private static String idOf(Role role,BlockPalette p) {
        return switch(role) {
            case TRUNK -> p.mainTrunk(); case THICK_BRANCH -> p.thickBranch(); case THIN_BRANCH -> p.thinBranch();
            case PRIMARY_LEAF -> p.primaryLeaf(); case SECONDARY_LEAF -> p.secondaryLeaf(); case ACCENT -> p.accentBlock();
        };
    }
    private static BlockPalette with(Role role,BlockPalette p,String id) {
        return p.withBlocks(role==Role.TRUNK?id:p.mainTrunk(),role==Role.THICK_BRANCH?id:p.thickBranch(),
                role==Role.THIN_BRANCH?id:p.thinBranch(),role==Role.PRIMARY_LEAF?id:p.primaryLeaf(),
                role==Role.SECONDARY_LEAF?id:p.secondaryLeaf(),role==Role.ACCENT?id:p.accentBlock());
    }
    private static Optional<Block> block(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.parse(id));
    }
    private Component rowLabel(int index) {
        String id=idOf(ROLES[index],state.config().blockPalette());
        Component name=block(id).<Component>map(Block::getName).orElse(Component.literal(id));
        return Component.translatable(ROLE_KEYS[index]).append(": ").append(name);
    }
    private List<Block> visible() {
        List<Block> all=CANDIDATES.computeIfAbsent(role,PaletteRules::candidates);
        if(query.isEmpty()) return all;
        List<Block> result=new ArrayList<>();
        for(Block b:all) {
            if(PaletteRules.id(b).contains(query) || b.getName().getString().toLowerCase(Locale.ROOT).contains(query)) result.add(b);
        }
        return result;
    }
    private int columns() { return Math.max(1,(width-16)/CELL); }
    private int maxScroll(int count) {
        int rows=(count+columns()-1)/columns();
        return Math.max(0,rows*CELL-(gridBottom-GRID_TOP));
    }

    void render(GuiGraphicsExtractor g,Font font,int mouseX,int mouseY) {
        g.fill(x,0,x+width,height,0xF00B1814); g.fill(x+width-1,0,x+width,height,0xFF4C876C);
        g.text(font,Component.translatable("treefactory.materials.title"),x+8,10,0xFFB8F8D2);
        for(int i=0;i<ROLES.length;i++) {
            int rowY=28+i*22;
            if(ROLES[i]==role) g.outline(x+8,rowY,width-16-24,20,0xFF69DFB1);
            block(idOf(ROLES[i],state.config().blockPalette())).ifPresent(b->g.item(new ItemStack(b),x+width-8-18,rowY+2));
        }
        List<Block> blocks=visible();
        scroll=Math.clamp(scroll,0,maxScroll(blocks.size()));
        String current=idOf(role,state.config().blockPalette());
        int cols=columns(), originX=x+8;
        hovered=null;
        g.enableScissor(x,GRID_TOP,x+width,gridBottom);
        for(int i=0;i<blocks.size();i++) {
            int cx=originX+(i%cols)*CELL, cy=GRID_TOP+(i/cols)*CELL-scroll;
            if(cy+CELL<GRID_TOP || cy>gridBottom) continue;
            Block b=blocks.get(i);
            boolean over=mouseX>=cx && mouseX<cx+CELL && mouseY>=cy && mouseY<cy+CELL && mouseY>=GRID_TOP && mouseY<gridBottom;
            if(over) hovered=b;
            boolean selected=PaletteRules.id(b).equals(current);
            g.fill(cx,cy,cx+CELL-1,cy+CELL-1,over?0xFF234A40:0xFF14261F);
            if(selected) g.outline(cx,cy,CELL-1,CELL-1,0xFF69DFB1);
            g.item(new ItemStack(b),cx+1,cy+1);
        }
        g.disableScissor();
        if(blocks.isEmpty()) g.text(font,Component.translatable("treefactory.materials.none"),x+8,GRID_TOP+4,0xFFB0C9BC);
        Block shown=hovered!=null ? hovered : block(current).orElse(null);
        if(shown!=null)
            g.text(font,font.plainSubstrByWidth(shown.getName().getString()+"  "+PaletteRules.id(shown),width-16),x+8,height-92+4,0xFF9CE7BD);
    }

    boolean click(double mx,double my) {
        if(mx<x || mx>=x+width || my<GRID_TOP || my>=gridBottom) return false;
        int col=(int)((mx-(x+8))/CELL), row=(int)((my-GRID_TOP+scroll)/CELL);
        if(mx<x+8 || col>=columns()) return true;
        List<Block> blocks=visible();
        int index=row*columns()+col;
        if(index>=0 && index<blocks.size()) {
            var p=state.config().blockPalette();
            state.update(state.config().withBlockPalette(with(role,p,PaletteRules.id(blocks.get(index)))));
            if(rebuild!=null) rebuild.run();
        }
        return true;
    }

    boolean scrolled(double mx,double my,double vertical) {
        if(mx<x || mx>=x+width || my<GRID_TOP-24 || my>=gridBottom) return false;
        scroll=Math.clamp(scroll-(int)(vertical*CELL*2),0,maxScroll(visible().size()));
        return true;
    }
}
