package com.treefactory.light.core.tree;

import java.util.*;
import java.util.function.Function;

/** Last-N committed placements. Undo skips cells the player has since changed. */
public final class PlacementHistory<K, V> {
    public static final int LIMIT = 5;
    public record Entry<K, V>(Map<K, V> placed, Map<K, V> original) {
        public Entry {
            placed=Map.copyOf(placed);
            original=Map.copyOf(original);
        }
        public Map<K, V> restorations(Function<K, V> current) {
            Map<K, V> result=new LinkedHashMap<>();
            for (var cell : placed.entrySet()) {
                if (!Objects.equals(current.apply(cell.getKey()), cell.getValue())) continue;
                V before=original.get(cell.getKey());
                if (before!=null) result.put(cell.getKey(), before);
            }
            return result;
        }
    }
    private final ArrayDeque<Entry<K, V>> entries=new ArrayDeque<>();
    public void push(Map<K, V> placed, Map<K, V> original) {
        entries.addLast(new Entry<>(placed, original));
        while (entries.size()>LIMIT) entries.removeFirst();
    }
    public void push(Entry<K, V> entry) {
        entries.addLast(entry);
        while (entries.size()>LIMIT) entries.removeFirst();
    }
    public Entry<K, V> pop() { return entries.pollLast(); }
    public int size() { return entries.size(); }
    public List<Entry<K, V>> snapshot() { return List.copyOf(entries); }
    public void clear() { entries.clear(); }
    public void replaceAll(List<Entry<K, V>> items) {
        entries.clear();
        for (Entry<K, V> entry : items) {
            entries.addLast(entry);
            if (entries.size()==LIMIT) break;
        }
    }
}
