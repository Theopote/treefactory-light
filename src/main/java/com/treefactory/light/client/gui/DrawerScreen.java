package com.treefactory.light.client.gui;

import com.treefactory.light.client.*;
import com.treefactory.light.client.gui.widget.*;
import com.treefactory.light.core.config.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class DrawerScreen extends Screen {
    private static final int M = 6, TOP = 36, TITLE_H = 14, BOTTOM_BAR = 20, SCROLLBAR_W = 4, SHAPE_COLS = 4;
    private static final CanopyShape[] SHAPE_ORDER = {
            CanopyShape.CONE, CanopyShape.TRUNCATED_CONE, CanopyShape.CYLINDER, CanopyShape.SPINDLE,
            CanopyShape.PARABOLOID, CanopyShape.OVOID, CanopyShape.PROLATE_ELLIPSOID, CanopyShape.SPHERE,
            CanopyShape.OBLATE_ELLIPSOID, CanopyShape.OBOVATE, CanopyShape.HEMISPHERE,
            CanopyShape.INVERTED_PARABOLOID, CanopyShape.HYPERBOLOID, CanopyShape.NEILOID,
            CanopyShape.ELLIPSOID, CanopyShape.UMBRELLA, CanopyShape.SPINDLE_MULTI_LAYER
    };
    private static final Set<String> TIPPED = Set.of(
            "treefactory.generator", "treefactory.library", "treefactory.finish_tree", "treefactory.choose_materials",
            "treefactory.new_seed", "treefactory.save_preset");

    private final ClientTreeState state = ClientTreeState.INSTANCE;
    private final List<AbstractWidget> content = new ArrayList<>(), fixed = new ArrayList<>();
    private final MaterialPanel materials = new MaterialPanel();
    private final List<AbstractWidget> materialWidgets = new ArrayList<>();
    private final Map<AbstractWidget, PresetStore.Preset> cards = new HashMap<>();

    private int panel, scroll, contentHeight;
    private boolean library, materialsOpen, shapesOpen = true;
    private ShapeGrid shapeGrid;
    private Button finishButton;
    private PresetStore.Preset menuPreset;
    private int menuX, menuY, materialLeft, materialWidth;
    private static final int MENU_W = 70, MENU_H = 18;
    private static Boolean previousHideGui;
    private boolean looking;
    private double mouseX, lookX, lookY;
    private String presetName;

    public DrawerScreen() { super(Component.literal("Treefactory Light")); }

    public void refresh() { rebuildWidgets(); }

    private int bottom() { return height - BOTTOM_BAR; }
    private int viewport() { return Math.max(0, bottom() - TOP); }
    private static double normalize(double value,double min,double max) { return (value-min)/(max-min); }
    private static double interpolate(double value,double min,double max) { return min+(max-min)*value; }

    @Override
    protected void init() {
        mouseX = minecraft.mouseHandler.xpos() / minecraft.getWindow().getGuiScale();
        if (presetName == null) presetName = Component.translatable("treefactory.preset.default_name").getString();
        panel = 2 * M + ShapeGrid.CELL * SHAPE_COLS + SCROLLBAR_W;
        content.clear();
        fixed.clear();
        materialWidgets.clear();
        cards.clear();
        menuPreset = null;
        shapeGrid = null;
        finishButton = null;

        if (previousHideGui == null) {
            previousHideGui = minecraft.gui.hud.isHidden();
            if (!previousHideGui) minecraft.gui.hud.toggle();
        }
        materialWidth = MaterialPanel.panelWidth();
        materialLeft = panel;
        int w = panel - 2 * M - SCROLLBAR_W;
        int half = (w - 2) / 2;

        fixed.add(addWidget(button(M, TITLE_H + 4, half, "treefactory.generator", () -> {
            library = false;
            scroll = 0;
            refresh();
        })));
        fixed.add(addWidget(button(M + half + 2, TITLE_H + 4, half, "treefactory.library", () -> {
            library = true;
            scroll = 0;
            refresh();
        })));

        fixed.add(addWidget(button(M, height - BOTTOM_BAR + 2, half, "treefactory.new_seed",
                () -> state.update(state.config().withSeed(new Random().nextLong())))));
        finishButton = button(M + half + 2, height - BOTTOM_BAR + 2, half, "treefactory.finish_tree", this::onFinishClicked);
        fixed.add(addWidget(finishButton));
        updateFinishButton();

        if (library) buildLibrary(w);
        else buildGenerator(w);

        if (materialsOpen && !library) {
            materials.init(font, materialLeft, materialWidth, height, w2 -> materialWidgets.add(addWidget(w2)),
                    () -> { materialsOpen = false; refresh(); }, this::refresh);
        }
        clampScroll();
    }

    private void buildLibrary(int w) {
        int gap = 3, tile = Math.max(1, Math.min((w - gap) / 2, viewport())), y = TOP;
        List<PresetStore.Preset> presets = state.presets();
        contentHeight = Math.max(tile, ((presets.size() + 1) / 2) * (tile + gap) - gap);
        for (int i = 0; i < presets.size(); i++) {
            var preset = presets.get(i);
            int x = M + (i % 2) * (tile + gap), top = y + (i / 2) * (tile + gap) - scroll;
            var card = new PresetCardWidget(x, top, tile, preset, () -> {
                if (state.update(preset.config())) refresh();
            });
            cards.put(card, preset);
            addContent(card);
        }
    }

    private void buildGenerator(int w) {
        TreeConfig c = state.config();
        int y = TOP;

        addContent(new SectionLabel(M, y - scroll, w, "treefactory.section.structure"));
        y += 16;
        addContent(new TrunkHeightSlider(M, y - scroll, w, c.trunkHeight(), h -> state.update(state.config().withTrunkHeight(h))));
        y += 18;
        addContent(new ParamSlider(M, y - scroll, w, "treefactory.trunk_thickness", normalize(c.trunkThickness(),TreeConfig.MIN_THICKNESS,TreeConfig.MAX_THICKNESS),
                v -> state.update(state.config().withTrunkThickness(interpolate(v,TreeConfig.MIN_THICKNESS,TreeConfig.MAX_THICKNESS)))));
        y += 16;
        addContent(new ParamSlider(M, y - scroll, w, "treefactory.crookedness", c.crookedness(),
                v -> state.update(state.config().withParameters(v, state.config().spread(), state.config().fullness()))));
        y += 18;

        addContent(new SectionLabel(M, y - scroll, w, "treefactory.section.canopy"));
        y += 16;
        Component shapeName = Component.translatable("treefactory.canopy." + c.canopyShape().name().toLowerCase(java.util.Locale.ROOT));
        addContent(Button.builder(Component.translatable(shapesOpen ? "treefactory.shapes.collapse" : "treefactory.shapes.expand", shapeName),
                btn -> { shapesOpen = !shapesOpen; refresh(); }).bounds(M, y - scroll, w, 14).build());
        y += 18;
        if (shapesOpen) {
            shapeGrid = new ShapeGrid(M, y - scroll, w, SHAPE_ORDER, c.canopyShape(), shape -> {
                TreeConfig now = state.config();
                state.update(now.withCanopy(shape, now.canopyScale(), now.attractionDensity()));
                refresh();
            });
            addContent(shapeGrid);
            y += shapeGrid.getHeight() + 4;
        }

        addContent(new ParamSlider(M, y - scroll, w, "treefactory.canopy_size", normalize(c.canopyScale(),TreeConfig.MIN_CANOPY_SCALE,TreeConfig.MAX_CANOPY_SCALE),
                v -> {
                    TreeConfig now = state.config();
                    state.update(now.withCanopy(now.canopyShape(), interpolate(v,TreeConfig.MIN_CANOPY_SCALE,TreeConfig.MAX_CANOPY_SCALE), now.attractionDensity()));
                }));
        y += 16;
        addContent(new ParamSlider(M, y - scroll, w, "treefactory.spread", c.spread(),
                v -> state.update(state.config().withParameters(state.config().crookedness(), v, state.config().fullness()))));
                                y += 16;
        addContent(new ParamSlider(M, y - scroll, w, "treefactory.fullness", c.fullness(),
                v -> state.update(state.config().withParameters(state.config().crookedness(), state.config().spread(), v))));
                                y += 16;
        if (c.canopyShape().stretchable()) {
            addContent(new ParamSlider(M, y - scroll, w, "treefactory.crown_stretch", normalize(c.crownStretch(),TreeConfig.MIN_STRETCH,TreeConfig.MAX_STRETCH),
                        v -> state.update(state.config().withCrownStretch(interpolate(v,TreeConfig.MIN_STRETCH,TreeConfig.MAX_STRETCH)))));
                                y += 16;
        }
        if (c.canopyShape().layered()) {
            addContent(new ParamSlider(M, y - scroll, w, "treefactory.crown_layers", normalize(c.crownLayers(),TreeConfig.MIN_LAYERS,TreeConfig.MAX_LAYERS),
                    v -> state.update(state.config().withCrownLayers((int) Math.round(interpolate(v,TreeConfig.MIN_LAYERS,TreeConfig.MAX_LAYERS))))));
                                y += 16;
        }

        addContent(new ParamSlider(M, y - scroll, w, "treefactory.attraction_density", normalize(c.attractionDensity(),TreeConfig.MIN_DENSITY,TreeConfig.MAX_DENSITY),
                v -> {
                    TreeConfig now = state.config();
                    state.update(now.withCanopy(now.canopyShape(), now.canopyScale(), interpolate(v,TreeConfig.MIN_DENSITY,TreeConfig.MAX_DENSITY)));
                }));
        y += 16;
        addContent(new ParamSlider(M, y - scroll, w, "treefactory.taper", c.taper(),
                v -> state.update(state.config().withTaper(v))));
        y += 16;
        addContent(new ParamSlider(M, y - scroll, w, "treefactory.gravity_influence", c.gravityInfluence(),
                v -> state.update(state.config().withGravityInfluence(v))));
        y += 16;

        Button materialsBtn = button(M, y - scroll, w, "treefactory.choose_materials", () -> {
            materialsOpen = !materialsOpen;
            refresh();
        });
        materialsBtn.setMessage(Component.translatable("treefactory.choose_materials").append(materialsOpen ? " ▶" : " ◀"));
        addContent(materialsBtn);
        y += 18;
        int saveHalf = (w - 2) / 2;
        EditBox presetNameBox = new EditBox(font, M, y - scroll, saveHalf, 14, Component.translatable("treefactory.preset_name"));
        presetNameBox.setMaxLength(64);
        presetNameBox.setValue(presetName);
        presetNameBox.setHint(Component.translatable("treefactory.preset_name"));
        presetNameBox.setResponder(v -> presetName = v);
        addContent(presetNameBox);
        addContent(button(M + saveHalf + 2, y - scroll, saveHalf, "treefactory.save_preset", () -> state.save(presetName)));
                y += 16;
        contentHeight = y - TOP;
    }

    private void onFinishClicked() {
        if (state.finishPhase() != ClientTreeState.FinishPhase.NONE) return;
        state.requestFinish(() -> {
            if (minecraft.gui.screen() == this) onClose();
        });
        updateFinishButton();
    }

    private void updateFinishButton() {
        if (finishButton == null) return;
        finishButton.active = state.finishPhase() == ClientTreeState.FinishPhase.NONE;
        String key = switch (state.finishPhase()) {
            case PREPARING -> "treefactory.preparing";
            case FINISHING -> "treefactory.finishing";
            case NONE -> state.isGenerating() ? "treefactory.preparing" : "treefactory.finish_tree";
        };
        finishButton.setMessage(Component.translatable(key));
    }

    @Override
    public void tick() {
        super.tick();
        updateFinishButton();
    }

    private void addContent(AbstractWidget widget) {
        syncWidgetInteraction(widget);
        content.add(addWidget(widget));
    }

    private boolean inContentViewport(AbstractWidget widget) {
        return widget.getY() < bottom() && widget.getY() + widget.getHeight() > TOP;
    }

    private void syncWidgetInteraction(AbstractWidget widget) {
        widget.visible = inContentViewport(widget);
        widget.active = widget.visible;
    }

    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
        return children().stream()
                .filter(child -> !content.contains(child) || (y >= TOP && y < bottom()))
                .filter(child -> child.isMouseOver(x, y))
                .findFirst().map(child -> child);
    }

    private void syncAllContentWidgets() {
        for (AbstractWidget widget : content) syncWidgetInteraction(widget);
    }

    private void clampScroll() {
        int bounded = Math.clamp(scroll, 0, Math.max(0, contentHeight - viewport()));
        if (bounded != scroll) {
            int shift = scroll - bounded;
            for (AbstractWidget widget : content) widget.setY(widget.getY() + shift);
            scroll = bounded;
        }
        syncAllContentWidgets();
    }

    private void applyScrollDelta(int delta) {
        int next = Math.clamp(scroll + delta, 0, Math.max(0, contentHeight - viewport()));
        int shift = scroll - next;
        if (shift == 0) return;
        for (AbstractWidget widget : content) widget.setY(widget.getY() + shift);
        scroll = next;
        syncAllContentWidgets();
    }

    private Button button(int x, int y, int width, String key, Runnable action) {
        Button b = Button.builder(Component.translatable(key), btn -> action.run()).bounds(x, y, width, 14).build();
        if (TIPPED.contains(key)) {
            b.setTooltip(Tooltip.create(Component.translatable(key + ".tip")));
        }
        return b;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, panel, height, 0xF010201B);
        g.fill(panel - 1, 0, panel, height, 0xFF808080);
        g.fill(0, 0, panel - 1, TITLE_H, 0xFF3C3F3E);
        g.text(font, font.plainSubstrByWidth("Treefactory Light", panel - 2 * M), M, 4, 0xFFFFFFFF);

        g.enableScissor(0, TOP, panel, bottom());
        for (AbstractWidget widget : content) widget.extractRenderState(g, mouseX, mouseY, delta);
        if (library && state.presets().isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("treefactory.empty_library"), M, TOP + 4, panel - 2 * M, 0xFFFFFFFF);
        }
        g.disableScissor();

        if (shapeGrid != null && mouseY >= TOP && mouseY < bottom() && shapeGrid.hovered() != null) {
            Component tip = shapeGrid.shapeTooltip(shapeGrid.hovered());
            int tipW = font.width(tip);
            int tx = Math.min(mouseX + 8, panel - tipW - 4);
            int ty = Math.max(TOP + 2, mouseY - 14);
            g.fill(tx - 2, ty - 2, tx + tipW + 2, ty + 10, 0xE010201B);
            g.text(font, tip, tx, ty, 0xFFFFFFFF);
        }

        for (AbstractWidget widget : fixed) widget.extractRenderState(g, mouseX, mouseY, delta);

        if (contentHeight > viewport()) {
            int track = Math.max(1, viewport()), thumb = Math.max(12, track * track / contentHeight);
            int y = TOP + (int) ((track - thumb) * scroll / (double) Math.max(1, contentHeight - track));
            g.fill(panel - 4, TOP, panel - 2, bottom(), 0xFF303030);
            g.fill(panel - 4, y, panel - 2, y + thumb, 0xFFB0B0B0);
        }

        if (materialsOpen && !library) {
            materials.render(g, font, mouseX, mouseY);
            g.enableScissor(materialLeft, 0, materialLeft + materialWidth, height);
            for (AbstractWidget widget : materialWidgets) widget.extractRenderState(g, mouseX, mouseY, delta);
            g.disableScissor();
        }

        if (menuPreset != null) {
            boolean over = mouseX >= menuX && mouseX < menuX + MENU_W && mouseY >= menuY && mouseY < menuY + MENU_H;
            g.fill(menuX, menuY, menuX + MENU_W, menuY + MENU_H, over ? 0xFF5A2A2A : 0xFF2A1F1F);
            g.outline(menuX, menuY, MENU_W, MENU_H, 0xFF808080);
            Component label = Component.translatable("treefactory.delete");
            g.text(font, label, menuX + (MENU_W - font.width(label)) / 2, menuY + 5, 0xFFFFFFFF);
        }

        int statusLeft = (materialsOpen && !library ? materialLeft + materialWidth : panel) + 10;
        int statusRight = width - 10;
        var infoLines = font.split(Component.literal(state.status()), Math.max(40, statusRight - statusLeft));
        if (!state.status().isEmpty()) {
            int lineH = font.lineHeight + 1, boxTop = height - 3 - infoLines.size() * lineH - 2;
            int boxW = infoLines.stream().mapToInt(font::width).max().orElse(0);
            g.fill(statusRight - boxW - 3, boxTop, statusRight + 3, height - 3, 0xC010201B);
            for (int i = 0; i < infoLines.size(); i++)
                g.text(font, infoLines.get(i), statusRight - font.width(infoLines.get(i)), boxTop + 2 + i * lineH, 0xFFFFFFFF);
        }

        var hintLines = font.split(Component.translatable("treefactory.preview_hint"), Math.max(40, width - statusLeft - 20));
        if (!hintLines.isEmpty()) {
            int lineH = font.lineHeight + 1;
            int hintW = hintLines.stream().mapToInt(font::width).max().orElse(0);
            g.fill(statusLeft - 3, 4, statusLeft + hintW + 3, 8 + hintLines.size() * lineH, 0xC010201B);
            for (int i = 0; i < hintLines.size(); i++) g.text(font, hintLines.get(i), statusLeft, 6 + i * lineH, 0xFFFFFFFF);
        }

    }

    @Override
    public void removed() {
        state.cancel();
        if (previousHideGui != null) {
            if (!previousHideGui && minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
            previousHideGui = null;
        }
        releaseMovement();
        endLook();
        super.removed();
    }

    private boolean insidePanel(double x) {
        return x < panel || (materialsOpen && !library && x < materialLeft + materialWidth);
    }

    private static final Set<KeyMapping> HELD = new HashSet<>();

    private void releaseMovement() {
        for (KeyMapping key : HELD) key.setDown(false);
        HELD.clear();
    }

    private KeyMapping[] movementKeys() {
        var o = minecraft.options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint};
    }

    @Override
    public void mouseMoved(double x, double y) {
        mouseX = x;
        if (insidePanel(x)) releaseMovement();
        super.mouseMoved(x, y);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!insidePanel(mouseX) && !(getFocused() instanceof EditBox) && event.key() != InputConstants.KEY_ESCAPE) {
            for (KeyMapping key : movementKeys()) {
                if (key.matches(event)) {
                    key.setDown(true);
                    HELD.add(key);
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(@NonNull KeyEvent event) {
        for (KeyMapping key : movementKeys()) {
            if (key.matches(event)) {
                key.setDown(false);
                HELD.remove(key);
            }
        }
        return super.keyReleased(event);
    }

    private void endLook() {
        if (!looking) return;
        looking = false;
        InputConstants.releaseMouse(minecraft.getWindow(), lookX, lookY);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (materialsOpen && !library && materials.scrolled(x, y, vertical)) return true;
        if (x < panel && y >= TOP && y < bottom()) {
            applyScrollDelta(-(int) (vertical * 24));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (menuPreset != null) {
            var chosen = menuPreset;
            menuPreset = null;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= menuX && event.x() < menuX + MENU_W
                    && event.y() >= menuY && event.y() < menuY + MENU_H) {
                state.delete(chosen);
                return true;
            }
            return true;
        }
        if (library && event.button() == InputConstants.MOUSE_BUTTON_RIGHT && event.x() < panel
                && event.y() >= TOP && event.y() < bottom()) {
            for (var card : cards.entrySet()) {
                if (card.getKey().visible && card.getKey().active && card.getKey().isMouseOver(event.x(), event.y())
                        && !card.getValue().id().startsWith("builtin-")) {
                    menuPreset = card.getValue();
                    menuX = Math.min((int) event.x(), width - MENU_W);
                    menuY = Math.min((int) event.y(), height - MENU_H);
                    return true;
                }
            }
        }
        if (insidePanel(event.x())) {
            if (shapeGrid != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.y() >= TOP && event.y() < bottom() && shapeGrid.isMouseOver(event.x(), event.y())) {
                shapeGrid.onClick(event, doubleClick);
                return true;
            }
            if (super.mouseClicked(event, doubleClick)) return true;
            if (materialsOpen && !library && event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && materials.click(event.x(), event.y())) return true;
            return true;
        }
        setFocused(null);
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            state.placeAtCursor(event.x(), event.y());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
            looking = true;
            lookX = minecraft.mouseHandler.xpos();
            lookY = minecraft.mouseHandler.ypos();
            InputConstants.grabMouse(minecraft.getWindow(), lookX, lookY);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (looking && event.button() == InputConstants.MOUSE_BUTTON_MIDDLE && minecraft.player != null) {
            double scale = minecraft.getWindow().getGuiScale();
            double sens = minecraft.options.sensitivity().get() * .6 + .2;
            sens = sens * sens * sens * 8;
            minecraft.player.turn(dx * scale * sens, dy * scale * sens);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (looking && event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
            endLook();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor g, int x, int y, float delta) {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isInGameUi() { return true; }
}
