package com.treefactory.light.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CommitWriteTest {
    @Test void queuedSnapshotMustReachDiskBeforeCommitSucceeds(@TempDir Path directory) throws Exception {
        var executor=Executors.newSingleThreadExecutor();
        var release=new CountDownLatch(1);
        var started=new CountDownLatch(1);
        try {
            var queue=new CoalescedPersistQueue();
            Path file=directory.resolve("snapshot");
            var future=queue.enqueue("committed", snapshot -> {
                started.countDown();
                if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("write timed out");
                Files.writeString(file,(String)snapshot);
            },executor);
            var commit=new CommitWrite(future);
            assertTrue(started.await(5,TimeUnit.SECONDS));
            assertEquals(CommitWrite.Status.WAITING,commit.poll(0,10,() -> fail("unexpected retry")));
            assertFalse(Files.exists(file));
            release.countDown();
            future.get(5,TimeUnit.SECONDS);
            assertEquals(CommitWrite.Status.SAVED,commit.poll(1,10,() -> fail("unexpected retry")));
            assertEquals("committed",Files.readString(file));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void commitDoesNotCompleteBeforeSnapshotWrite() {
        var future=new CompletableFuture<Void>();
        var commit=new CommitWrite(future);
        assertEquals(CommitWrite.Status.WAITING,commit.poll(0,10,() -> fail("unexpected retry")));
        future.complete(null);
        assertEquals(CommitWrite.Status.SAVED,commit.poll(1,10,() -> fail("unexpected retry")));
    }

    @Test void failedWriteWaitsAndRetriesBeforeSuccess() {
        var future=new CompletableFuture<Void>();
        var commit=new CommitWrite(future);
        future.completeExceptionally(new IllegalStateException("disk full"));
        assertEquals(CommitWrite.Status.FAILED,commit.poll(0,10,() -> fail("unexpected retry")));
        assertNotNull(commit.error());
        var retry=new CompletableFuture<Void>();
        var attempts=new AtomicInteger();
        assertEquals(CommitWrite.Status.WAITING,commit.poll(9,10,() -> fail("too early")));
        assertEquals(CommitWrite.Status.WAITING,commit.poll(10,10,() -> { attempts.incrementAndGet(); return retry; }));
        assertEquals(CommitWrite.Status.WAITING,commit.poll(11,10,() -> fail("retry already running")));
        retry.complete(null);
        assertEquals(CommitWrite.Status.SAVED,commit.poll(12,10,() -> fail("unexpected retry")));
        assertEquals(1,attempts.get());
    }
}
