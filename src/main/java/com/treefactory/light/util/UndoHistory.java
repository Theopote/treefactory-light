package com.treefactory.light.util;

import java.util.*;

/** Server-thread-owned, bounded, per-player history. */
public final class UndoHistory<T> {
    private final Map<UUID,Deque<T>> entries = new HashMap<>();
    public void push(UUID player, T entry) {
        Deque<T> stack = entries.computeIfAbsent(player,k -> new ArrayDeque<>());
        stack.addFirst(entry);
        while (stack.size()>5) stack.removeLast();
    }
    public T peek(UUID player) { Deque<T> stack=entries.get(player); return stack==null ? null : stack.peekFirst(); }
    public T pop(UUID player) {
        Deque<T> stack=entries.get(player);
        if (stack==null) return null;
        T entry=stack.pollFirst(); if (stack.isEmpty()) entries.remove(player); return entry;
    }
    public void remove(UUID player) { entries.remove(player); }
    public void clear() { entries.clear(); }
}
