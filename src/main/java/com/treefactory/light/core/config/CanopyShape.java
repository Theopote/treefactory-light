package com.treefactory.light.core.config;

public enum CanopyShape implements ICanopyShape {
    ELLIPSOID {
        public double field(double x,double y,double z) { return x*x+y*y+z*z-1; }
    },
    SPHERE {
        public double field(double x,double y,double z) { return x*x+y*y+z*z-1; }
    },
    UMBRELLA {
        public double field(double x,double y,double z) {
            double r2=x*x+z*z;
            return Math.max(r2-1,Math.max(y-(.22+.28*Math.sqrt(Math.max(0,1-r2))),-.23-.32*r2-y));
        }
    },
    CONE {
        public double field(double x,double y,double z) {
            double t=(y+1)*.5;
            return Math.max(Math.abs(y)-1,Math.hypot(x,z)-(1-t));
        }
    },
    TRUNCATED_CONE {
        public double field(double x,double y,double z) {
            double t=(y+1)*.5;
            return Math.max(Math.abs(y)-.78,Math.hypot(x,z)-(.95-.7*t));
        }
    },
    CYLINDER {
        public double field(double x,double y,double z) { return Math.max(x*x+z*z-1,Math.abs(y)-1); }
    },
    HEMISPHERE {
        public double field(double x,double y,double z) { return Math.max(x*x+y*y+z*z-1,-y); }
    },
    SPINDLE_MULTI_LAYER {
        public double field(double x,double y,double z) {
            double best=Double.POSITIVE_INFINITY;
            double[] heights={-.58,0,.58}, radii={.92,1,.72};
            for(int i=0;i<3;i++) {
                double radial=(x*x+z*z)/(radii[i]*radii[i]);
                double vertical=(y-heights[i])/.23;
                best=Math.min(best,radial+vertical*vertical-1);
            }
            return best;
        }
    };
    public static CanopyShape forPreset(TreePreset preset) {
        return switch(preset) { case PINE -> CONE; case WILLOW -> UMBRELLA; case DEAD -> HEMISPHERE; default -> ELLIPSOID; };
    }
}
