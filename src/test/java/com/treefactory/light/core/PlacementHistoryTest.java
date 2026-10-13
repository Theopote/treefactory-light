package com.treefactory.light.core;

import com.treefactory.light.core.tree.PlacementHistory;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementHistoryTest {
    @Test void keepsOnlyTheLastFiveAndSkipsEditedCells() {
        PlacementHistory<String,Integer> history=new PlacementHistory<>();
        for(int i=0;i<6;i++) history.push(Map.of("p"+i, i), Map.of("p"+i, -i));
        assertEquals(5, history.size());
        var sixth=history.pop();
        assertEquals(Map.of("p5",-5), sixth.restorations(key->5));
        assertTrue(sixth.restorations(key->99).isEmpty());
        assertEquals(4, history.size());
        assertEquals("p1", history.snapshot().getFirst().placed().keySet().iterator().next());
    }
}