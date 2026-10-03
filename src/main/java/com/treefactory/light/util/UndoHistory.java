package com.treefactory.light.util;

import java.util.*;
import java.util.function.ToLongFunction;

/** Server-thread-owned history bounded both per player and across all players. */
public final class UndoHistory<T> {
    private static final class Entry<T> {
        final UUID player; final T value; final long weight;
        Entry(UUID player,T value,long weight) { this.player=player; this.value=value; this.weight=weight; }
    }
    private final Map<UUID,Deque<Entry<T>>> entries = new HashMap<>();
    private final Deque<Entry<T>> oldest=new ArrayDeque<>();
    private final long maxWeight;
    private final ToLongFunction<T> weigh;
    private long totalWeight;
    public UndoHistory() { this(Long.MAX_VALUE,value->1); }
    public UndoHistory(long maxWeight,ToLongFunction<T> weigh) {
        if(maxWeight<1) throw new IllegalArgumentException("History budget must be positive");
        this.maxWeight=maxWeight; this.weigh=Objects.requireNonNull(weigh);
    }
    public void push(UUID player, T entry) {
        long weight=weigh.applyAsLong(entry);
        if(weight<0) throw new IllegalArgumentException("History weight must be nonnegative");
        if(weight>maxWeight) throw new IllegalArgumentException("History entry exceeds global budget");
        Deque<Entry<T>> stack = entries.computeIfAbsent(player,k -> new ArrayDeque<>());
        Entry<T> wrapped=new Entry<>(player,entry,weight);
        stack.addFirst(wrapped); oldest.addLast(wrapped); totalWeight+=weight;
        while(stack.size()>5) discard(stack.removeLast());
        while(totalWeight>maxWeight && oldest.size()>1) {
            Entry<T> victim=oldest.removeFirst(); totalWeight-=victim.weight;
            Deque<Entry<T>> owner=entries.get(victim.player);
            owner.remove(victim);
            if(owner.isEmpty()) entries.remove(victim.player);
        }
    }
    private void discard(Entry<T> entry) { if(oldest.remove(entry)) totalWeight-=entry.weight; }
    public T peek(UUID player) {
        Deque<Entry<T>> stack=entries.get(player);
        return stack==null ? null : stack.peekFirst().value;
    }
    public T pop(UUID player) {
        Deque<Entry<T>> stack=entries.get(player);
        if (stack==null) return null;
        Entry<T> entry=stack.pollFirst(); discard(entry);
        if (stack.isEmpty()) entries.remove(player);
        return entry.value;
    }
    public void remove(UUID player) {
        Deque<Entry<T>> stack=entries.remove(player);
        if(stack!=null) stack.forEach(this::discard);
    }
    public void clear() { entries.clear(); oldest.clear(); totalWeight=0; }
}
