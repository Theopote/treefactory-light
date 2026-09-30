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
    },
    SPINDLE {
        public double field(double x,double y,double z) {
            double cap=Math.max(0,Math.abs(y)-.42)/.58;
            return Math.max(Math.abs(y)-1,x*x+z*z-(.72*.72)*(1-cap*cap));
        }
    },
    PARABOLOID {
        public double field(double x,double y,double z) {
            double t=(y+1)*.5;
            return Math.max(Math.abs(y)-1,x*x+z*z-(1-t));
        }
    },
    OVOID {
        public double field(double x,double y,double z) { return ovalField(x,y,z,.38); }
    },
    PROLATE_ELLIPSOID {
        public double field(double x,double y,double z) { return (x*x+z*z)/(.7*.7)+y*y-1; }
    },
    OBLATE_ELLIPSOID {
        public double field(double x,double y,double z) { return x*x+z*z+y*y/(.58*.58)-1; }
    },
    OBOVATE {
        public double field(double x,double y,double z) { return ovalField(x,y,z,.68); }
    },
    INVERTED_PARABOLOID {
        public double field(double x,double y,double z) {
            double t=(y+1)*.5;
            return Math.max(Math.abs(y)-1,x*x+z*z-t);
        }
    },
    HYPERBOLOID {
        public double field(double x,double y,double z) {
            return Math.max(Math.abs(y)-1,x*x+z*z-(.38+.62*y*y));
        }
    },
    NEILOID {
        public double field(double x,double y,double z) {
            double t=(y+1)*.5;
            double radius=(1-t)*(1-t);
            return Math.max(Math.abs(y)-1,Math.hypot(x,z)-radius);
        }
    };
    private static double ovalField(double x,double y,double z,double peak) {
        double t=(y+1)*.5;
        if(t<0 || t>1) return Math.abs(y)-1;
        double alpha=peak*2,beta=(1-peak)*2;
        double maximum=Math.pow(peak,alpha)*Math.pow(1-peak,beta);
        double radius2=Math.pow(t,alpha)*Math.pow(1-t,beta)/maximum;
        return Math.max(Math.abs(y)-1,x*x+z*z-radius2);
    }
    public static CanopyShape forPreset(TreePreset preset) {
        return switch(preset) { case PINE -> CONE; case WILLOW -> UMBRELLA; case DEAD -> HEMISPHERE; default -> ELLIPSOID; };
    }
}
