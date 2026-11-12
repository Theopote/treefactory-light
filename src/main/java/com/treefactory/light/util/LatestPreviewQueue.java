package com.treefactory.light.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coalesces waiting requests without interrupting an update already in progress. */
final class LatestPreviewQueue<K, V> {
    private final Map<K, V> waiting = new LinkedHashMap<>();

    void put(K key, V value) { waiting.put(key, value); }
    boolean containsKey(K key) { return waiting.containsKey(key); }
    List<K> keys() { return new ArrayList<>(waiting.keySet()); }
    V takeIfIdle(K key, boolean busy) { return busy ? null : waiting.remove(key); }
    void remove(K key) { waiting.remove(key); }
    void clear() { waiting.clear(); }
}
