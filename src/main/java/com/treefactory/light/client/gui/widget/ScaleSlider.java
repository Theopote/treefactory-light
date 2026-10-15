package com.treefactory.light.client.gui.widget;

import com.treefactory.light.core.config.TreeConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;

/** Continuous tree-height slider. */
public final class ScaleSlider extends AbstractSliderButton {
    private static final double MIN=TreeConfig.MIN_HEIGHT, MAX=TreeConfig.MAX_HEIGHT;
    private final DoubleConsumer changed;
    public ScaleSlider(int x,int y,int width,double height,DoubleConsumer changed) {
        super(x,y,width,20,Component.empty(),(height-MIN)/(MAX-MIN));
        this.changed=changed; updateMessage();
        setTooltip(Tooltip.create(Component.translatable("treefactory.size.tip")));
    }
    private double height() { return MIN+value*(MAX-MIN); }
    @Override protected void updateMessage() {
        setMessage(Component.translatable("treefactory.size",String.format("%.1f",height())));
    }
    @Override protected void applyValue() { updateMessage(); changed.accept(height()); }
}