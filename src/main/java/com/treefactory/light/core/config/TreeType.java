package com.treefactory.light.core.config;

public enum TreeType {
    OAK, PINE, WILLOW, DEAD;

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
