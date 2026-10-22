package com.treefactory.light.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Non-interactive section header inside the scrollable generator panel. */
public final class SectionLabel extends AbstractWidget {
    public SectionLabel(int x, int y, int width, String translationKey) {
        super(x, y, width, 14, Component.translatable(translationKey));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.text(Minecraft.getInstance().font, getMessage(), getX(), getY() + 3, 0xFF83B99D);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    @Override
    public boolean isActive() { return false; }
}
