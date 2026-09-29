package com.treefactory.light.core.algorithm;

import com.treefactory.light.core.config.*;
import com.treefactory.light.core.tree.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class SpaceColonizer {
    public static final int MAX_NODES = 1800;
    public static final int MAX_ITERATIONS = 160;

    public List<TreeNode> generate(TreeConfig config) { return generate(config, () -> false); }

    public List<TreeNode> generate(TreeConfig config, BooleanSupplier cancelled) {
        Random random = new Random(config.seed());
        NoiseGenerator noise = new NoiseGenerator(config.seed());
        double height = config.scale().height;
        double width = height * (.16 + .38 * config.spread());
        List<Vector3> attractors = new ArrayList<>();
        int count = (int)(100 + 320 * config.fullness());
        for (int i = 0; i < count; i++) {
            double x, y, z;
            do { x = random.nextDouble()*2-1; y = random.nextDouble()*2-1; z = random.nextDouble()*2-1;
            } while (x*x + y*y + z*z > 1);
            Vector3 point = switch (config.preset()) {
                case PINE -> {
                    double t = random.nextDouble(), angle = random.nextDouble()*Math.PI*2;
                    double r = Math.sqrt(random.nextDouble()) * width * (1-t) * .8;
                    yield new Vector3(.5+Math.cos(angle)*r, height*(.3+.7*t), .5+Math.sin(angle)*r);
                }
                case WILLOW -> new Vector3(.5+x*width*1.2, height*(.7+y*.22), .5+z*width*1.2);
                default -> new Vector3(.5+x*width, height*(.68+y*.32), .5+z*width);
            };
            attractors.add(point);
        }
        List<TreeNode> nodes = new ArrayList<>();
        nodes.add(new TreeNode(new Vector3(.5,.5,.5), -1, .5));
        for (int y = 1; y <= height*.42; y++) {
            double bend = config.crookedness() * Math.sin(y*.3) * .55;
            nodes.add(new TreeNode(new Vector3(.5+bend,y+.5,.5), nodes.size()-1,.5));
        }
        double influence = Math.max(4, height*.32), kill2 = 1.65*1.65;
        Set<Cell> occupied = new HashSet<>();
        for (TreeNode n : nodes) occupied.add(quantize(n.position()));
        for (int iteration = 0; iteration < MAX_ITERATIONS && nodes.size() < MAX_NODES && !attractors.isEmpty(); iteration++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
            Vector3[] sum = new Vector3[nodes.size()];
            // Spatial buckets avoid a full attractor × node scan for large trees.
            Map<Cell, List<Integer>> buckets = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++)
                buckets.computeIfAbsent(bucket(nodes.get(i).position(), influence), k -> new ArrayList<>()).add(i);
            Iterator<Vector3> iterator = attractors.iterator();
            while (iterator.hasNext()) {
                Vector3 point = iterator.next(); Cell b = bucket(point, influence);
                double best = influence*influence; int nearest = -1;
                for (int dx=-1; dx<=1; dx++) for (int dy=-1; dy<=1; dy++) for (int dz=-1; dz<=1; dz++) {
                    List<Integer> candidates = buckets.get(new Cell(b.x()+dx,b.y()+dy,b.z()+dz));
                    if (candidates == null) continue;
                    for (int index : candidates) {
                        double d = point.subtract(nodes.get(index).position()).lengthSquared();
                        if (d < best) { best = d; nearest = index; }
                    }
                }
                if (best <= kill2 && nearest >= 0) { iterator.remove(); continue; }
                if (nearest >= 0) {
                    Vector3 direction = point.subtract(nodes.get(nearest).position()).normalize();
                    sum[nearest] = sum[nearest] == null ? direction : sum[nearest].add(direction);
                }
            }
            int before = nodes.size();
            for (int i=0; i<before && nodes.size()<MAX_NODES; i++) {
                if (sum[i] == null) continue;
                Vector3 p = nodes.get(i).position();
                Vector3 perturb = new Vector3(noise.sample(p.x()*.2,p.y()*.2,p.z()*.2),
                        noise.sample(p.x()*.2+17,p.y()*.2,p.z()*.2),noise.sample(p.x()*.2,p.y()*.2,p.z()*.2+31));
                Vector3 dir = sum[i].normalize().add(perturb.multiply(config.crookedness()*.7)).normalize();
                Vector3 next = p.add(dir.multiply(1.15));
                if (next.y() < .5 || !occupied.add(quantize(next))) continue;
                nodes.add(new TreeNode(next,i,.5));
            }
            if (nodes.size() == before) break;
        }
        if (config.preset() == TreePreset.WILLOW) {
            boolean[] parent = new boolean[nodes.size()];
            for (TreeNode n : nodes) if (n.parent() >= 0) parent[n.parent()] = true;
            int before = nodes.size();
            for (int i=0; i<before && nodes.size()<MAX_NODES; i++) if (!parent[i]) {
                int last = i;
                for (int k=0; k<height*.16 && nodes.size()<MAX_NODES; k++) {
                    Vector3 p = nodes.get(last).position().add(new Vector3(0,-1,0));
                    if (p.y()<1 || !occupied.add(quantize(p))) break;
                    nodes.add(new TreeNode(p,last,.5)); last = nodes.size()-1;
                }
            }
        }
        return List.copyOf(nodes);
    }
    private static Cell quantize(Vector3 p) { return bucket(p,.55); }
    private static Cell bucket(Vector3 p, double size) {
        return new Cell((int)Math.floor(p.x()/size),(int)Math.floor(p.y()/size),(int)Math.floor(p.z()/size));
    }
}
