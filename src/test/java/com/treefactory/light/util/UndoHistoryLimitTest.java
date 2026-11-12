package com.treefactory.light.util;

import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UndoHistoryLimitTest {
    @Test void retainsTheNewestFiveEntries() {
        var stack = new ArrayDeque<Integer>();
        for (int i=0; i<6; i++) {
            stack.addLast(i);
            WorldBatchPaster.trimUndoHistory(stack);
        }
        assertEquals(List.of(1, 2, 3, 4, 5), List.copyOf(stack));
    }
}
