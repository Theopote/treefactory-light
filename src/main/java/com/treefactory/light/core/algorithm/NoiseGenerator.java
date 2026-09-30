package com.treefactory.light.core.algorithm;

import java.util.Random;

/** Seeded, continuous gradient noise. No shared RNG or game-world access. */
public final class NoiseGenerator {
    private static final int[][] GRAD3={{1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},
            {1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},{0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1}};
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
    /** Seeded 3D simplex noise used to disturb the edges of canopy implicit surfaces. */
    public double sampleSimplex(double x,double y,double z) {
        double s=(x+y+z)/3, xs=x+s,ys=y+s,zs=z+s;
        int i=(int)Math.floor(xs),j=(int)Math.floor(ys),k=(int)Math.floor(zs);
        double t=(i+j+k)/6.0, x0=x-(i-t),y0=y-(j-t),z0=z-(k-t);
        int i1,j1,k1,i2,j2,k2;
        if(x0>=y0) {
            if(y0>=z0) {i1=1;j1=0;k1=0;i2=1;j2=1;k2=0;}
            else if(x0>=z0) {i1=1;j1=0;k1=0;i2=1;j2=0;k2=1;}
            else {i1=0;j1=0;k1=1;i2=1;j2=0;k2=1;}
        } else {
            if(y0<z0) {i1=0;j1=0;k1=1;i2=0;j2=1;k2=1;}
            else if(x0<z0) {i1=0;j1=1;k1=0;i2=0;j2=1;k2=1;}
            else {i1=0;j1=1;k1=0;i2=1;j2=1;k2=0;}
        }
        double x1=x0-i1+1.0/6,y1=y0-j1+1.0/6,z1=z0-k1+1.0/6;
        double x2=x0-i2+1.0/3,y2=y0-j2+1.0/3,z2=z0-k2+1.0/3;
        double x3=x0-.5,y3=y0-.5,z3=z0-.5;
        int ii=i&255,jj=j&255,kk=k&255;
        int g0=permutation[ii+permutation[jj+permutation[kk]]]%12;
        int g1=permutation[ii+i1+permutation[jj+j1+permutation[kk+k1]]]%12;
        int g2=permutation[ii+i2+permutation[jj+j2+permutation[kk+k2]]]%12;
        int g3=permutation[ii+1+permutation[jj+1+permutation[kk+1]]]%12;
        return 32*(corner(g0,x0,y0,z0)+corner(g1,x1,y1,z1)+corner(g2,x2,y2,z2)+corner(g3,x3,y3,z3));
    }
    private static double corner(int gradient,double x,double y,double z) {
        double t=.6-x*x-y*y-z*z;
        if(t<0) return 0;
        t*=t; int[] g=GRAD3[gradient];
        return t*t*(g[0]*x+g[1]*y+g[2]*z);
    }
    private static double fade(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double lerp(double t, double a, double b) { return a+t*(b-a); }
    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15; double u = h < 8 ? x : y, v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
