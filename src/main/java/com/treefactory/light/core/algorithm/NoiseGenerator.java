package com.treefactory.light.core.algorithm;

import java.util.Random;

/** Seeded, continuous gradient noise. No shared RNG or game-world access. */
public final class NoiseGenerator {
    private final int[] permutation = new int[512];
    public NoiseGenerator(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1), t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) permutation[i] = p[i & 255];
    }
    public double sample(double x, double y, double z) {
        int ix = (int)Math.floor(x) & 255, iy = (int)Math.floor(y) & 255, iz = (int)Math.floor(z) & 255;
        x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int a = permutation[ix] + iy, b = permutation[ix + 1] + iy;
        int aa = permutation[a] + iz, ab = permutation[a + 1] + iz;
        int ba = permutation[b] + iz, bb = permutation[b + 1] + iz;
        return lerp(w, lerp(v, lerp(u, grad(permutation[aa], x,y,z), grad(permutation[ba], x-1,y,z)),
                lerp(u, grad(permutation[ab], x,y-1,z), grad(permutation[bb], x-1,y-1,z))),
                lerp(v, lerp(u, grad(permutation[aa+1], x,y,z-1), grad(permutation[ba+1], x-1,y,z-1)),
                lerp(u, grad(permutation[ab+1], x,y-1,z-1), grad(permutation[bb+1], x-1,y-1,z-1))));
    }
    private static double fade(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double lerp(double t, double a, double b) { return a+t*(b-a); }
    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15; double u = h < 8 ? x : y, v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
