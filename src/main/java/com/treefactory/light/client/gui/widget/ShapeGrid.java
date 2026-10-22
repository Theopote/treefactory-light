package com.treefactory.light.client.gui.widget;

import com.treefactory.light.core.config.CanopyShape;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import java.util.Locale;
import java.util.function.Consumer;

/** Compact icon grid for choosing among many canopy shapes. */
public final class ShapeGrid extends AbstractWidget {
    public static final int CELL = 28;
    private final CanopyShape[] shapes;
    private CanopyShape selected;
    private final Consumer<CanopyShape> onSelect;
    private int cols;
    private CanopyShape hovered;

    public ShapeGrid(int x, int y, int width, CanopyShape[] shapes, CanopyShape selected, Consumer<CanopyShape> onSelect) {
        super(x, y, width, heightFor(width, shapes.length), Component.translatable("treefactory.choose_shape"));
        this.shapes = shapes;
        this.selected = selected;
        this.onSelect = onSelect;
        this.cols = columnsFor(width);
    }

    public CanopyShape selected() { return selected; }

    public void setSelected(CanopyShape shape) { selected = shape; }

    private static int columnsFor(int width) {
        int fit = width / CELL;
        if (fit >= 4) return 4;
        return Math.max(2, fit);
    }

    private static int heightFor(int width, int count) {
        int cols = columnsFor(width);
        int rows = (count + cols - 1) / cols;
        return rows * CELL + 2;
    }

    public CanopyShape hovered() { return hovered; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        cols = columnsFor(width);
        hovered = shapeAt(mouseX, mouseY);
        for (int i = 0; i < shapes.length; i++) {
            CanopyShape shape = shapes[i];
            int cx = getX() + (i % cols) * CELL;
            int cy = getY() + (i / cols) * CELL;
            boolean over = shape == hovered;
            boolean pick = shape == selected;
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, over ? 0xFF234A40 : 0xFF14261F);
            g.outline(cx, cy, CELL - 1, CELL - 1, pick ? 0xFF69DFB1 : 0xFF37584D);
            ShapeIcons.draw(g, shape, cx + 7, cy + 7, 14, pick ? 0xFFB8F8D2 : 0xFF9CB8A9);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && active && shapeAt(mouseX, mouseY) != null;
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        CanopyShape shape = shapeAt(event.x(), event.y());
        if (shape == null || shape == selected) return;
        selected = shape;
        onSelect.accept(shape);
    }

    private CanopyShape shapeAt(double mouseX, double mouseY) {
        cols = columnsFor(width);
        int col = (int) ((mouseX - getX()) / CELL);
        int row = (int) ((mouseY - getY()) / CELL);
        if (col < 0 || col >= cols || row < 0) return null;
        int index = row * cols + col;
        if (index < 0 || index >= shapes.length) return null;
        int cx = getX() + col * CELL;
        int cy = getY() + row * CELL;
        if (mouseX < cx || mouseX >= cx + CELL || mouseY < cy || mouseY >= cy + CELL) return null;
        return shapes[index];
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public Component shapeTooltip(CanopyShape shape) {
        return Component.translatable("treefactory.canopy." + shape.name().toLowerCase(Locale.ROOT));
    }
}
