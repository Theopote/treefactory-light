package com.treefactory.light.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoalescedPersistQueueTest {
    @Test void burstEnqueuesWriteOnlyTheLatestSnapshot() throws Exception {
        CoalescedPersistQueue queue = new CoalescedPersistQueue();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<Integer> writes = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch holdFirstWrite = new CountDownLatch(1);
            CoalescedPersistQueue.PersistWriter writer = snapshot -> {
                writes.add((Integer) snapshot);
                if (writes.size() == 1) holdFirstWrite.await(2, TimeUnit.SECONDS);
            };
            CompletableFuture<Void> first = queue.enqueue(() -> 0, writer, executor);
            for (int i = 1; i < 20; i++) {
                final int value = i;
                queue.enqueue(() -> value, writer, executor);
            }
            holdFirstWrite.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertFalse(writes.isEmpty());
            assertEquals(19, writes.getLast());
            assertTrue(writes.size() <= 2, "expected coalesced writes, got " + writes.size());
        } finally {
            executor.shutdownNow();
        }
    }
}
