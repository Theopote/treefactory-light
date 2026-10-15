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
import net.minecraft.client.gui.components.Tooltip;
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
    private static final int CELL=20;
    private static final Map<Role,List<Block>> CANDIDATES=new EnumMap<>(Role.class);
    private static int cacheEpoch=-1;

    private final ClientTreeState state=ClientTreeState.INSTANCE;
    private Role role=Role.TRUNK;
    private String rawQuery="", query="";
    private int scroll;
    private int x, width, height, gridTop=66, gridBottom;
    private Block hovered;
    private Runnable rebuild;
    private Role cachedRole; private String cachedQuery; private List<Block> cachedVisible=List.of();
    private DNAControlSlider secondarySlider, accentSlider;

    private static final int TAB_HEIGHT=30;

    /** Role tab drawn by hand so the block icon and the role name are both visible. */
    private final class RoleTab extends Button {
        private final Role tabRole; private final Font tabFont;
        RoleTab(int x,int y,int w,int h,Role tabRole,Component label,Font font) {
            super(x,y,w,h,label,b->{ role=tabRole; scroll=0; },DEFAULT_NARRATION);
            this.tabRole=tabRole; this.tabFont=font;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
            g.fill(getX(),getY(),getX()+width,getY()+height,isHovered()?0xFF234A40:0xFF14261F);
            g.outline(getX(),getY(),width,height,tabRole==role?0xFF69DFB1:0xFF37584D);
            block(idOf(tabRole,state.config().blockPalette())).ifPresent(b->g.item(new ItemStack(b),getX()+(width-16)/2,getY()+2));
            String text=tabFont.plainSubstrByWidth(getMessage().getString(),width-2);
            g.text(tabFont,text,getX()+(width-tabFont.width(text))/2,getY()+20,tabRole==role?0xFFE2F5E9:0xFF9CB8A9);
        }
    }

    static int widthFor(int screenWidth) { return Math.clamp(screenWidth/3,200,280); }

    void init(Font font,int left,int width,int height,Consumer<AbstractWidget> add,Runnable close,Runnable rebuild) {
        this.x=left; this.width=width; this.height=height; this.gridTop=22+TAB_HEIGHT+24; this.gridBottom=height-62;
        this.rebuild=rebuild;
        add.accept(Button.builder(Component.literal("✕"),b->close.run()).bounds(left+width-26,4,20,16)
                .tooltip(Tooltip.create(Component.translatable("treefactory.materials.close.tip"))).build());
        int tabW=(width-16)/ROLES.length;
        for(int i=0;i<ROLES.length;i++) {
            Role r=ROLES[i];
            RoleTab tab=new RoleTab(left+8+i*tabW,22,tabW-2,TAB_HEIGHT,r,Component.translatable(ROLE_KEYS[i]),font);
            tab.setTooltip(Tooltip.create(Component.translatable(ROLE_KEYS[i]).append("\n").append(Component.translatable("treefactory.materials.tab.tip"))));
            add.accept(tab);
        }
        EditBox search=new EditBox(font,left+8,22+TAB_HEIGHT+4,width-16,16,Component.translatable("treefactory.materials.search"));
        search.setHint(Component.translatable("treefactory.materials.search"));
        search.setTooltip(Tooltip.create(Component.translatable("treefactory.materials.search.tip")));
        search.setMaxLength(48); search.setValue(rawQuery);
        search.setResponder(value->{ rawQuery=value; query=value.toLowerCase(Locale.ROOT).trim(); scroll=0; });
        add.accept(search);
        var palette=state.config().blockPalette();
        int half=(width-18)/2;
        secondarySlider=new DNAControlSlider(left+8,height-46,half,"treefactory.secondary_weight",palette.secondaryWeight(),
                v->setWeights(v,state.config().blockPalette().accentWeight()));
        accentSlider=new DNAControlSlider(left+10+half,height-46,half,"treefactory.accent_weight",palette.accentWeight(),
                v->setWeights(state.config().blockPalette().secondaryWeight(),v));
        add.accept(secondarySlider);
        add.accept(accentSlider);
        add.accept(Button.builder(Component.translatable("treefactory.materials.reset"),b->{
            state.update(state.config().withBlockPalette(defaults())); rebuild.run();
        }).bounds(left+8,height-22,width-16,20)
                .tooltip(Tooltip.create(Component.translatable("treefactory.materials.reset.tip"))).build());
    }

    private BlockPalette defaults() {
        return BlockPalette.defaults(state.config().preset()==TreePreset.PINE ? "spruce" : "oak");
    }
    private void setWeights(double secondary,double accent) {
        var p=state.config().blockPalette();
        double s=Math.clamp(secondary,0,1), a=Math.clamp(accent,0,1-s);
        state.update(state.config().withBlockPalette(p.withWeights(1-s-a,s,a)));
        if(secondarySlider!=null) secondarySlider.sync(s);
        if(accentSlider!=null) accentSlider.sync(a);
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
    private List<Block> visible() {
        if(cacheEpoch!=ClientTreeState.sessionEpoch) { CANDIDATES.clear(); cacheEpoch=ClientTreeState.sessionEpoch; cachedVisible=null; }
        if(cachedVisible!=null && role==cachedRole && query.equals(cachedQuery)) return cachedVisible;
        List<Block> all=CANDIDATES.computeIfAbsent(role,PaletteRules::candidates);
        List<Block> result;
        if(query.isEmpty()) result=all;
        else {
            result=new ArrayList<>();
            for(Block b:all) {
                if(PaletteRules.id(b).contains(query) || b.getName().getString().toLowerCase(Locale.ROOT).contains(query)) result.add(b);
            }
        }
        cachedRole=role; cachedQuery=query; cachedVisible=result;
        return result;
    }
    private int columns() { return Math.max(1,(width-16)/CELL); }
    private int maxScroll(int count) {
        int rows=(count+columns()-1)/columns();
        return Math.max(0,rows*CELL-(gridBottom-gridTop));
    }

    void render(GuiGraphicsExtractor g,Font font,int mouseX,int mouseY) {
        g.fill(x,0,x+width,height,0xF00B1814); g.fill(x+width-1,0,x+width,height,0xFF4C876C);
        g.text(font,Component.translatable("treefactory.materials.title"),x+8,8,0xFFB8F8D2);
        List<Block> blocks=visible();
        scroll=Math.clamp(scroll,0,maxScroll(blocks.size()));
        String current=idOf(role,state.config().blockPalette());
        int cols=columns(), originX=x+8;
        hovered=null;
        g.enableScissor(x,gridTop,x+width,gridBottom);
        for(int i=0;i<blocks.size();i++) {
            int cx=originX+(i%cols)*CELL, cy=gridTop+(i/cols)*CELL-scroll;
            if(cy+CELL<gridTop || cy>gridBottom) continue;
            Block b=blocks.get(i);
            boolean over=mouseX>=cx && mouseX<cx+CELL && mouseY>=cy && mouseY<cy+CELL && mouseY>=gridTop && mouseY<gridBottom;
            if(over) hovered=b;
            boolean selected=PaletteRules.id(b).equals(current);
            g.fill(cx,cy,cx+CELL-1,cy+CELL-1,over?0xFF234A40:0xFF14261F);
            if(selected) g.outline(cx,cy,CELL-1,CELL-1,0xFF69DFB1);
            g.item(new ItemStack(b),cx+1,cy+1);
        }
        g.disableScissor();
        if(blocks.isEmpty()) g.text(font,Component.translatable("treefactory.materials.none"),x+8,gridTop+4,0xFFB0C9BC);
        Block shown=hovered!=null ? hovered : block(current).orElse(null);
        Component roleName=Component.translatable(ROLE_KEYS[role.ordinal()]);
        String line=(hovered!=null ? "" : roleName.getString()+": ")+(shown!=null ? shown.getName().getString() : current);
        g.text(font,font.plainSubstrByWidth(line,width-16),x+8,height-58,0xFF9CE7BD);
    }

    boolean click(double mx,double my) {
        if(mx<x || mx>=x+width || my<gridTop || my>=gridBottom) return false;
        int col=(int)((mx-(x+8))/CELL), row=(int)((my-gridTop+scroll)/CELL);
        if(mx<x+8 || col>=columns()) return true;
        List<Block> blocks=visible();
        int index=row*columns()+col;
        if(index>=0 && index<blocks.size()) {
            var p=state.config().blockPalette();
            state.update(state.config().withBlockPalette(with(role,p,PaletteRules.id(blocks.get(index)))));
        }
        return true;
    }

    boolean scrolled(double mx,double my,double vertical) {
        if(mx<x || mx>=x+width || my<gridTop-20 || my>=gridBottom) return false;
        scroll=Math.clamp(scroll-(int)(vertical*CELL*2),0,maxScroll(visible().size()));
        return true;
    }
}
