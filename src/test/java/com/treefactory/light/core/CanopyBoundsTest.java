package com.treefactory.light.core;

import com.treefactory.light.core.config.CanopyShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanopyBoundsTest {
    @Test void declaredMinYIsConservativeAlongTheCrownAxis() {
        for (CanopyShape shape : CanopyShape.values()) {
            for (int layers = 3; layers <= 6; layers++) {
                double scanned = scanAxisMinY(shape, layers);
                assertTrue(shape.minY(layers) <= scanned + 1e-6,
                        shape + " layers=" + layers + " minY=" + shape.minY(layers) + " scanned=" + scanned);
            }
        }
    }

    @Test void declaredMaxYCoversTheCrownTopAlongTheAxis() {
        for (CanopyShape shape : CanopyShape.values()) {
            for (int layers = 3; layers <= 6; layers++) {
                double scanned = scanAxisMaxY(shape, layers);
                assertTrue(shape.maxY(layers) >= scanned - 1e-6,
                        shape + " layers=" + layers + " maxY=" + shape.maxY(layers) + " scanned=" + scanned);
            }
        }
    }

    @Test void spindleMultiLayerBoundsStayValidAcrossLayerCounts() {
        for (int layers = 3; layers <= 6; layers++) {
            CanopyShape shape = CanopyShape.SPINDLE_MULTI_LAYER;
            assertTrue(shape.minY(layers) < shape.maxY(layers), "layers=" + layers);
            assertTrue(shape.field(0, shape.minY(layers), 0, layers) <= 0, "layers=" + layers);
            assertTrue(shape.field(0, shape.maxY(layers), 0, layers) <= 0, "layers=" + layers);
        }
    }

    private static double scanAxisMinY(CanopyShape shape, int layers) {
        for (int i = 0; i <= 400; i++) {
            double y = -1 + i * .005;
            if (shape.field(0, y, 0, layers) <= 0) return y;
        }
        return -1;
    }

    private static double scanAxisMaxY(CanopyShape shape, int layers) {
        for (int i = 400; i >= 0; i--) {
            double y = -1 + i * .005;
            if (shape.field(0, y, 0, layers) <= 0) return y;
        }
        return 1;
    }
}
