package com.treefactory.light.core.config;

public enum TreeScale {
    SMALL(10), MEDIUM(16), LARGE(24), GIANT(36);
    public final int height;
    TreeScale(int height) { this.height = height; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public TreeScale cycle(int direction) {
        return values()[Math.floorMod(ordinal() + direction, values().length)];
    }
}
