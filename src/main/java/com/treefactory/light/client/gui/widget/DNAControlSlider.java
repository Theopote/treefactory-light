package com.treefactory.light.client.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;
import java.util.Locale;

public final class DNAControlSlider extends AbstractSliderButton {
    private final String key;
    private final DoubleConsumer changed;
    public DNAControlSlider(int x,int y,int width,String key,double value,DoubleConsumer changed) {
        super(x,y,width,20,Component.empty(),value); this.key=key; this.changed=changed; updateMessage();
        setTooltip(Tooltip.create(Component.translatable(key+".tip")));
    }
    @Override protected void updateMessage() {
        Object shown=switch(key) {
            case "treefactory.canopy_scale" -> String.format(Locale.ROOT,"%.2f",.75+1.25*value);
            case "treefactory.attraction_density" -> String.format(Locale.ROOT,"%.2f",.5+1.5*value);
            case "treefactory.pipe_exponent" -> String.format(Locale.ROOT,"%.2f",2.5+.5*value);
            case "treefactory.trunk_thickness" -> String.format(Locale.ROOT,"%.2f",.5+1.5*value);
            case "treefactory.crown_stretch" -> String.format(Locale.ROOT,"%.2f",.6+value);
            case "treefactory.crown_layers" -> 3+(int)Math.round(3*value);
            default -> (int)Math.round(value*100);
        };
        setMessage(Component.translatable(key,shown));
    }
    public void sync(double newValue) { value=newValue; updateMessage(); }
    @Override protected void applyValue() { changed.accept(value); }
}
