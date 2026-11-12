package com.treefactory.light.client.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import com.treefactory.light.core.config.TreeConfig;
import java.util.function.DoubleConsumer;
import java.util.Locale;

public final class DNAControlSlider extends AbstractSliderButton {
    private final String key;
    private final DoubleConsumer changed;
    public DNAControlSlider(int x,int y,int width,String key,double value,DoubleConsumer changed) {
        super(x,y,width,14,Component.empty(),value); this.key=key; this.changed=changed; updateMessage();
        setTooltip(Tooltip.create(Component.translatable(key+".tip")));
    }
    @Override protected void updateMessage() {
        Object shown=switch(key) {
            case "treefactory.canopy_size" -> String.format(Locale.ROOT,"%.2f",interpolate(TreeConfig.MIN_CANOPY_SCALE,TreeConfig.MAX_CANOPY_SCALE));
            case "treefactory.attraction_density" -> String.format(Locale.ROOT,"%.2f",interpolate(TreeConfig.MIN_DENSITY,TreeConfig.MAX_DENSITY));
            case "treefactory.pipe_exponent" -> String.format(Locale.ROOT,"%.2f",interpolate(TreeConfig.MIN_PIPE_EXPONENT,TreeConfig.MAX_PIPE_EXPONENT));
            case "treefactory.trunk_thickness" -> String.format(Locale.ROOT,"%.2f",interpolate(TreeConfig.MIN_THICKNESS,TreeConfig.MAX_THICKNESS));
            case "treefactory.crown_stretch" -> String.format(Locale.ROOT,"%.2f",interpolate(TreeConfig.MIN_STRETCH,TreeConfig.MAX_STRETCH));
            case "treefactory.crown_base" -> (int)Math.round((TreeConfig.MIN_CROWN_BASE
                    +(TreeConfig.MAX_CROWN_BASE-TreeConfig.MIN_CROWN_BASE)*value)*100);
            case "treefactory.crown_layers" -> TreeConfig.MIN_LAYERS+(int)Math.round((TreeConfig.MAX_LAYERS-TreeConfig.MIN_LAYERS)*value);
            case "treefactory.gravity_influence" -> (int)Math.round(value*100);
            default -> (int)Math.round(value*100);
        };
        setMessage(Component.translatable(key,shown));
    }
    private double interpolate(double min,double max) { return min+(max-min)*value; }
    public void sync(double newValue) { value=newValue; updateMessage(); }
    @Override protected void applyValue() { changed.accept(value); }
}
