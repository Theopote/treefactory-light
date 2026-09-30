package com.treefactory.light.core.config;

/** Signed shape function in normalized crown coordinates. Values <= 0 lie inside. */
public interface ICanopyShape {
    double field(double x, double y, double z);
}
