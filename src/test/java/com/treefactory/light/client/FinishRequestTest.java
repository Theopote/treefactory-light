package com.treefactory.light.client;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinishRequestTest {
    @Test void cancellingPreparingClearsPhaseAndCallback() {
        var request = new FinishRequest();
        request.begin(() -> fail("cancelled callback"), true);
        assertEquals(ClientTreeState.FinishPhase.PREPARING, request.phase());
        request.clear();
        assertEquals(ClientTreeState.FinishPhase.NONE, request.phase());
        assertNull(request.clear());
        assertFalse(request.matches(1));
    }

    @Test void cancelledResponseCannotCompleteANewerRequest() {
        var request = new FinishRequest();
        var callbacks = new AtomicInteger();
        request.begin(() -> fail("old callback"), false);
        long oldId = request.send();
        request.clear();

        request.begin(callbacks::incrementAndGet, true);
        assertFalse(request.matches(oldId));
        long newId = request.send();
        assertNotEquals(oldId, newId);
        assertFalse(request.matches(oldId));
        assertTrue(request.matches(newId));
        request.clear().run();
        assertEquals(1, callbacks.get());
        assertFalse(request.matches(newId));
    }

    @Test void cancellingFinishingDiscardsCallbackAndResponse() {
        var request = new FinishRequest();
        request.begin(() -> fail("cancelled callback"), false);
        long id = request.send();
        request.clear();
        assertFalse(request.matches(id));
        assertEquals(ClientTreeState.FinishPhase.NONE, request.phase());
        assertNull(request.clear());
    }
}
