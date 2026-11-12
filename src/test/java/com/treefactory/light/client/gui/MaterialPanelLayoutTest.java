package com.treefactory.light.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialPanelLayoutTest {
    @Test void shortWindowsRetainGridAndAllowAccessToBottomControls() {
        int height=180;
        int content=MaterialPanel.contentHeightFor(height);
        assertTrue(content - 60 - 86 >= 18);
        assertEquals(40,MaterialPanel.clampPanelScroll(100,height));
        assertTrue(content - 18 - MaterialPanel.clampPanelScroll(100,height) < height);
        assertEquals(0,MaterialPanel.clampPanelScroll(-1,height));
    }

    @Test void normalWindowDoesNotScrollPanel() {
        assertEquals(400,MaterialPanel.contentHeightFor(400));
        assertEquals(0,MaterialPanel.clampPanelScroll(20,400));
    }
}
