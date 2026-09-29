package com.treefactory.light.client.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;

public final class DNAControlSlider extends AbstractSliderButton {
    private final String key;
    private final DoubleConsumer changed;
    public DNAControlSlider(int x,int y,int width,String key,double value,DoubleConsumer changed) {
        super(x,y,width,20,Component.empty(),value); this.key=key; this.changed=changed; updateMessage();
    }
    @Override protected void updateMessage() { setMessage(Component.translatable(key,(int)Math.round(value*100))); }
    @Override protected void applyValue() { changed.accept(value); }
}
