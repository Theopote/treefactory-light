package com.treefactory.light.core.config;

public enum TreeType {
    OAK, PINE, WILLOW, DEAD, MANGROVE;

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String defaultWood() {
        return switch (this) {
            case PINE -> "spruce";
            case MANGROVE -> "mangrove";
            default -> "oak";
        };
    }
}
