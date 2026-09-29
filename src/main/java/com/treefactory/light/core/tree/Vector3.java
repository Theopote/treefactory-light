package com.treefactory.light.core.tree;

public record Vector3(double x, double y, double z) {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public Vector3 add(Vector3 v) { return new Vector3(x + v.x, y + v.y, z + v.z); }
    public Vector3 subtract(Vector3 v) { return new Vector3(x - v.x, y - v.y, z - v.z); }
    public Vector3 multiply(double s) { return new Vector3(x * s, y * s, z * s); }
    public double dot(Vector3 v) { return x * v.x + y * v.y + z * v.z; }
    public double lengthSquared() { return dot(this); }
    public Vector3 normalize() {
        double length = Math.sqrt(lengthSquared());
        return length < 1e-10 ? ZERO : multiply(1 / length);
    }
}
