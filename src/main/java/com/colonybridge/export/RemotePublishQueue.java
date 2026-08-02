package com.colonybridge.export;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class RemotePublishQueue {
    private final RemoteOperation publisher;
    private final ExecutorService executor;
    private boolean running;
    private boolean closed;
    private Pending pending;

    RemotePublishQueue() {
        this((payload, endpoint, token) -> new RemoteSnapshotPublisher().publishPayload(payload, endpoint, token));
    }

    RemotePublishQueue(RemoteOperation publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "colonybridge-remote");
            thread.setDaemon(true);
            return thread;
        });
    }

    synchronized CompletableFuture<BatchResult> submit(List<PreparedSnapshot> snapshots, String endpoint, String token) {
        Pending task = new Pending(List.copyOf(snapshots), endpoint, token, new CompletableFuture<>());
        if (closed) {
            task.future.complete(BatchResult.cancelled());
            return task.future;
        }
        if (!running) {
            running = true;
            executor.execute(() -> run(task));
        } else {
            if (pending != null) pending.future.complete(BatchResult.coalesced());
            pending = task;
        }
        return task.future;
    }

    private void run(Pending task) {
        long sanitizationNanos = 0;
        long publishNanos = 0;
        int payloadBytes = 0;
        int attempts = 0;
        try {
            for (PreparedSnapshot prepared : task.snapshots) {
                long started = System.nanoTime();
                byte[] payload = SnapshotSanitizer.sanitizeCompact(prepared.remoteCompactJson(), task.token)
                        .toString().getBytes(StandardCharsets.UTF_8);
                sanitizationNanos += System.nanoTime() - started;
                started = System.nanoTime();
                RemotePublishResult result;
                try {
                    result = publisher.publish(payload, task.endpoint, task.token);
                } finally {
                    publishNanos += System.nanoTime() - started;
                }
                payloadBytes += result.payloadBytes();
                attempts += result.attempts();
            }
            task.future.complete(new BatchResult(ExportResult.RemoteOutcome.SUCCEEDED, sanitizationNanos,
                    publishNanos, payloadBytes, attempts, null));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            task.future.complete(new BatchResult(ExportResult.RemoteOutcome.CANCELLED, sanitizationNanos,
                    publishNanos, payloadBytes, attempts, interrupted));
        } catch (Exception failure) {
            task.future.complete(new BatchResult(ExportResult.RemoteOutcome.FAILED, sanitizationNanos,
                    publishNanos, payloadBytes, attempts, failure));
        } finally {
            startNext();
        }
    }

    private synchronized void startNext() {
        if (!closed && pending != null) {
            Pending next = pending;
            pending = null;
            executor.execute(() -> run(next));
        } else {
            if (pending != null) pending.future.complete(BatchResult.cancelled());
            pending = null;
            running = false;
        }
    }

    void shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        synchronized (this) {
            closed = true;
            if (pending != null) pending.future.complete(BatchResult.cancelled());
            pending = null;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(Math.max(0, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    synchronized boolean busy() {
        return running;
    }

    synchronized boolean queued() {
        return pending != null;
    }

    record BatchResult(ExportResult.RemoteOutcome outcome, long sanitizationNanos, long publishNanos,
                       int payloadBytes, int attempts, Exception failure) {
        static BatchResult coalesced() {
            return new BatchResult(ExportResult.RemoteOutcome.COALESCED, 0, 0, 0, 0, null);
        }

        static BatchResult cancelled() {
            return new BatchResult(ExportResult.RemoteOutcome.CANCELLED, 0, 0, 0, 0, null);
        }
    }

    @FunctionalInterface
    interface RemoteOperation {
        RemotePublishResult publish(byte[] payload, String endpoint, String token) throws Exception;
    }

    private record Pending(List<PreparedSnapshot> snapshots, String endpoint, String token,
                           CompletableFuture<BatchResult> future) {
    }
}
