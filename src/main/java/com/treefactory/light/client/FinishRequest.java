package com.treefactory.light.client;

final class FinishRequest {
    private ClientTreeState.FinishPhase phase = ClientTreeState.FinishPhase.NONE;
    private Runnable callback;
    private long nextId;
    private long currentId;

    ClientTreeState.FinishPhase phase() { return phase; }

    void begin(Runnable onSuccess, boolean preparing) {
        callback = onSuccess;
        currentId = ++nextId;
        phase = preparing ? ClientTreeState.FinishPhase.PREPARING : ClientTreeState.FinishPhase.FINISHING;
    }

    long send() {
        phase = ClientTreeState.FinishPhase.FINISHING;
        return currentId;
    }

    boolean matches(long id) {
        return phase == ClientTreeState.FinishPhase.FINISHING && currentId == id;
    }

    Runnable clear() {
        Runnable previous = callback;
        callback = null;
        phase = ClientTreeState.FinishPhase.NONE;
        return previous;
    }
}
