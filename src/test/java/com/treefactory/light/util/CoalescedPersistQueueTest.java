package com.treefactory.light.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoalescedPersistQueueTest {
    @Test void laterSnapshotFutureWaitsUntilItsWriteCompletes() throws Exception {
        CoalescedPersistQueue queue=new CoalescedPersistQueue();
        ExecutorService executor=Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted=new CountDownLatch(1), releaseFirst=new CountDownLatch(1);
        CountDownLatch secondStarted=new CountDownLatch(1), releaseSecond=new CountDownLatch(1);
        try {
            CoalescedPersistQueue.PersistWriter writer=snapshot -> {
                if ((int)snapshot==1) { firstStarted.countDown(); releaseFirst.await(); }
                else { secondStarted.countDown(); releaseSecond.await(); }
            };
            queue.enqueue(1,writer,executor);
            assertTrue(firstStarted.await(5,TimeUnit.SECONDS));
            CompletableFuture<Void> later=queue.enqueue(2,writer,executor);
            releaseFirst.countDown();
            assertTrue(secondStarted.await(5,TimeUnit.SECONDS));
            assertFalse(later.isDone());
            releaseSecond.countDown();
            later.get(5,TimeUnit.SECONDS);
            assertEquals(2,queue.writtenGeneration());
        } finally {
            releaseFirst.countDown(); releaseSecond.countDown();
            executor.shutdownNow();
        }
    }

    @Test void executorRejectionFailsFutureAndCanBeRetried() throws Exception {
        CoalescedPersistQueue queue=new CoalescedPersistQueue();
        Executor rejected=task -> { throw new RejectedExecutionException("closed"); };
        var failed=queue.enqueue(1,snapshot -> {},rejected);
        assertInstanceOf(RejectedExecutionException.class,
                assertThrows(ExecutionException.class, () -> failed.get()).getCause());
        queue.enqueue(2,snapshot -> assertEquals(2,snapshot),Runnable::run).get();
        assertEquals(0,queue.pendingGenerations());
    }
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
            CompletableFuture<Void> first = queue.enqueue(0, writer, executor);
            for (int i = 1; i < 20; i++) queue.enqueue(i, writer, executor);
            holdFirstWrite.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertFalse(writes.isEmpty());
            assertEquals(19, writes.getLast());
            assertTrue(writes.size() <= 2, "expected coalesced writes, got " + writes.size());
            assertEquals(20, queue.writtenGeneration());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void writeFailureIsPropagatedAndRetried() throws Exception {
        CoalescedPersistQueue queue = new CoalescedPersistQueue();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            List<Integer> writes = Collections.synchronizedList(new ArrayList<>());
            CoalescedPersistQueue.PersistWriter failing = snapshot -> { throw new RuntimeException("disk full"); };
            CompletableFuture<Void> first = queue.enqueue(1, failing, executor);
            ExecutionException failed = assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
            assertInstanceOf(RuntimeException.class, failed.getCause());
            assertEquals(0, queue.writtenGeneration());
            assertEquals(1, queue.pendingGenerations());

            CoalescedPersistQueue.PersistWriter ok = snapshot -> writes.add((Integer) snapshot);
            CompletableFuture<Void> retry = queue.enqueue(2, ok, executor);
            retry.get(5, TimeUnit.SECONDS);
            assertEquals(2, writes.getLast());
            assertEquals(2, queue.writtenGeneration());
        } finally {
            executor.shutdownNow();
        }
    }
}
