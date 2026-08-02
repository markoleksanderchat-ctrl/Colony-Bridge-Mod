package com.colonybridge.export;

import com.colonybridge.ColonyBridgeConstants;
import com.colonybridge.api.ExportTrigger;
import com.colonybridge.api.MineColoniesAdapter;
import com.colonybridge.api.ServerLevelContext;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.model.BridgeInfo;
import com.colonybridge.model.ColonySnapshot;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class ColonyBridgeExporter {
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    private final Logger logger;
    private final MineColoniesAdapter adapter;
    private final Map<StoreKey, SnapshotStore> stores = new HashMap<>();
    private volatile ExportStatus status;
    private volatile ExportTrigger lastSuccessfulTrigger;
    private CompletableFuture<ExportResult> activeExport;
    private int queuedManualExports;
    private int coalescedAutomaticExports;
    private ExecutorService ioExecutor;
    private RemotePublishQueue remoteQueue;
    private boolean accepting;

    public ColonyBridgeExporter(Logger logger, MineColoniesAdapter adapter) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.status = new ExportStatus(adapter.status().available(), adapter.status().mineColoniesVersion(), 0,
                null, null, 0, null, adapter.status().adapterName(), 0, 0, List.of());
        start();
    }

    public synchronized void start() {
        if (accepting && ioExecutor != null && !ioExecutor.isShutdown()) return;
        ioExecutor = Executors.newSingleThreadExecutor(task -> daemonThread(task, "colonybridge-io"));
        remoteQueue = new RemotePublishQueue();
        accepting = true;
    }

    public ExportStatus status() {
        return status;
    }

    public synchronized boolean exportInProgress() {
        return activeExport != null && !activeExport.isDone();
    }

    public synchronized int queuedManualExports() {
        return queuedManualExports;
    }

    public synchronized int coalescedAutomaticExports() {
        return coalescedAutomaticExports;
    }

    public synchronized boolean remotePublishInProgress() {
        return remoteQueue != null && remoteQueue.busy();
    }

    public synchronized boolean remotePublishQueued() {
        return remoteQueue != null && remoteQueue.queued();
    }

    public boolean hasRecentSuccessfulExport(Duration maxAge) {
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isNegative()) throw new IllegalArgumentException("maxAge must not be negative");
        ExportStatus current = status;
        Instant lastSuccess = current.lastSuccessfulExportAt();
        if (lastSuccess == null || current.coloniesDetected() == 0 || current.lastErrorCount() > 0) return false;
        Instant now = Instant.now();
        return !lastSuccess.isAfter(now) && Duration.between(lastSuccess, now).compareTo(maxAge) <= 0;
    }

    public boolean hasRecentSuccessfulExport(ExportTrigger trigger, Duration maxAge) {
        return trigger == lastSuccessfulTrigger && hasRecentSuccessfulExport(maxAge);
    }

    public CompletableFuture<ExportStatus> exportAll(MinecraftServer server, BridgeConfigValues config, ExportTrigger trigger) {
        return submit(ExportRequest.all(server, config, trigger)).thenApply(ExportResult::status);
    }

    public CompletableFuture<ExportStatus> exportOne(MinecraftServer server, BridgeConfigValues config,
                                                     ExportTrigger trigger, int colonyId) {
        return submit(ExportRequest.one(server, config, trigger, colonyId)).thenApply(ExportResult::status);
    }

    public CompletableFuture<ExportResult> submit(ExportRequest request) {
        if (!request.config().enabled()) {
            return CompletableFuture.completedFuture(new ExportResult(request, ExportResult.LocalOutcome.NOT_FOUND,
                    ExportResult.RemoteOutcome.DISABLED, status, ExportTimings.EMPTY));
        }

        final CompletableFuture<ExportResult> future;
        synchronized (this) {
            if (!accepting) return CompletableFuture.failedFuture(new IllegalStateException("Exporter is shutting down."));
            if (activeExport != null && !activeExport.isDone()) {
                if (request.trigger() == ExportTrigger.MANUAL) {
                    queuedManualExports++;
                    CompletableFuture<ExportResult> running = activeExport;
                    logger.debug("Colony Bridge manual export queued behind the active export.");
                    return running.handle((ignored, failure) -> null).thenCompose(ignored -> {
                        synchronized (this) { queuedManualExports--; }
                        return submit(request);
                    });
                }
                coalescedAutomaticExports++;
                logger.debug("Colony Bridge export coalesced because an export is already running.");
                return activeExport;
            }
            future = new CompletableFuture<>();
            activeExport = future;
        }

        Runnable collect = () -> collect(request, future);
        if (request.server().isSameThread()) collect.run();
        else request.server().execute(collect);
        return future;
    }

    private void collect(ExportRequest request, CompletableFuture<ExportResult> future) {
        if (!request.server().isSameThread()) {
            fail(request, future, new IllegalStateException("Snapshot collection must run on the Minecraft server thread."), 0);
            return;
        }
        Instant started = Instant.now();
        long collectionStarted = System.nanoTime();
        Path outputRoot = outputRoot(request.server());
        final List<ColonySnapshot> snapshots;
        try {
            ServerLevelContext context = new ServerLevelContext(request.server(), outputRoot);
            Instant generatedAt = Instant.now();
            snapshots = request.colonyId().isPresent()
                    ? adapter.collectById(context, request.config(), request.colonyId().getAsInt(), request.trigger(), generatedAt)
                            .map(List::of).orElseGet(List::of)
                    : adapter.collectAll(context, request.config(), request.trigger(), generatedAt);
        } catch (Exception failure) {
            fail(request, future, failure, elapsedMillis(collectionStarted));
            logger.error("Colony Bridge collection failed", failure);
            return;
        }
        long collectionMs = elapsedMillis(collectionStarted);

        if (request.colonyId().isPresent() && snapshots.isEmpty()) {
            ExportTimings timings = new ExportTimings(collectionMs, 0, 0, 0, 0, 0, 0,
                    Duration.between(started, Instant.now()).toMillis());
            ExportStatus next = statusFor(request, 0, started, status.lastSuccessfulExportAt(), outputRoot,
                    0, 0, List.of(), ExportResult.LocalOutcome.NOT_FOUND, ExportResult.RemoteOutcome.DISABLED, timings);
            status = next;
            complete(request, future, ExportResult.LocalOutcome.NOT_FOUND, ExportResult.RemoteOutcome.DISABLED, next, timings);
            return;
        }

        try {
            ioExecutor.execute(() -> writeSnapshots(request, outputRoot, started, collectionMs, snapshots, future));
        } catch (RejectedExecutionException rejected) {
            fail(request, future, rejected, collectionMs);
        }
    }

    private void writeSnapshots(ExportRequest request, Path outputRoot, Instant started, long collectionMs,
                                List<ColonySnapshot> snapshots, CompletableFuture<ExportResult> future) {
        List<Path> latestFiles = new ArrayList<>();
        List<PreparedSnapshot> prepared = new ArrayList<>();
        int warnings = 0;
        int errors = 0;
        long serializationNanos = 0;
        long fingerprintNanos = 0;
        long diskWriteNanos = 0;
        long retentionNanos = 0;
        try {
            SnapshotStore store = store(outputRoot, request.config().prettyPrintJson());
            SnapshotSerializer serializer = new SnapshotSerializer();
            for (ColonySnapshot snapshot : snapshots) {
                PreparedSnapshot material = serializer.prepare(snapshot, request.config().prettyPrintJson());
                prepared.add(material);
                SnapshotWriteResult result = store.writeSnapshot(material, request.config().retainSnapshots());
                latestFiles.add(result.latestPath());
                serializationNanos += result.serializationNanos();
                fingerprintNanos += result.fingerprintNanos();
                diskWriteNanos += result.diskWriteNanos();
                retentionNanos += result.retentionNanos();
                warnings += snapshot.warnings().size();
                errors += snapshot.errors().size();
            }

            ExportResult.RemoteOutcome remoteOutcome = ExportResult.RemoteOutcome.DISABLED;
            if (request.config().remoteSyncConfigured()) remoteOutcome = ExportResult.RemoteOutcome.QUEUED;
            else if (request.config().remoteSyncEnabled()) {
                remoteOutcome = ExportResult.RemoteOutcome.MISCONFIGURED;
                errors++;
                logger.warn("Colony Bridge remote sync is enabled but its HTTPS endpoint or token is missing or invalid.");
            }

            long durationMs = Duration.between(started, Instant.now()).toMillis();
            Instant lastSuccess = Instant.now();
            BridgeInfo info = new BridgeInfo(ColonyBridgeConstants.VERSION, ColonyBridgeConstants.PROTOCOL_ID,
                    ColonyBridgeConstants.SCHEMA_VERSION, ColonyBridgeConstants.OUTPUT_LAYOUT_VERSION,
                    "filesystem", true,
                    snapshots.isEmpty() ? null : snapshots.getFirst().game().minecraftVersion(),
                    snapshots.isEmpty() ? null : snapshots.getFirst().game().loaderVersion(),
                    adapter.status().mineColoniesVersion(), adapter.status().available() ? "ready" : "unavailable",
                    DateTimeFormatter.ISO_INSTANT.format(started), DateTimeFormatter.ISO_INSTANT.format(lastSuccess),
                    durationMs, snapshots.size(), outputRoot.toAbsolutePath().normalize().toString(),
                    latestFiles.stream().map(path -> path.toAbsolutePath().normalize().toString()).toList());
            long bridgeInfoStarted = System.nanoTime();
            store.writeBridgeInfo(info);
            diskWriteNanos += System.nanoTime() - bridgeInfoStarted;

            ExportTimings timings = new ExportTimings(collectionMs, millis(serializationNanos), millis(fingerprintNanos),
                    millis(diskWriteNanos), millis(retentionNanos), 0, 0, durationMs);
            ExportStatus next = statusFor(request, snapshots.size(), started, lastSuccess, outputRoot, warnings, errors,
                    latestFiles, ExportResult.LocalOutcome.SAVED, remoteOutcome, timings);
            status = next;
            lastSuccessfulTrigger = request.trigger();
            complete(request, future, ExportResult.LocalOutcome.SAVED, remoteOutcome, next, timings);
            if (remoteOutcome == ExportResult.RemoteOutcome.QUEUED) queueRemote(request, prepared, next, timings);
        } catch (Exception failure) {
            long durationMs = Duration.between(started, Instant.now()).toMillis();
            ExportTimings timings = new ExportTimings(collectionMs, millis(serializationNanos), millis(fingerprintNanos),
                    millis(diskWriteNanos), millis(retentionNanos), 0, 0, durationMs);
            ExportStatus failed = statusFor(request, snapshots.size(), started, null, outputRoot, warnings, errors + 1,
                    latestFiles, ExportResult.LocalOutcome.FAILED, ExportResult.RemoteOutcome.DISABLED, timings);
            status = failed;
            logger.error("Colony Bridge write failed", failure);
            clearActiveExport(future);
            future.completeExceptionally(failure);
        }
    }

    private void queueRemote(ExportRequest request, List<PreparedSnapshot> snapshots, ExportStatus localStatus,
                             ExportTimings localTimings) {
        remoteQueue.submit(snapshots, request.config().remoteEndpoint(), request.config().remoteToken())
                .thenAccept(result -> {
                    ExportTimings timings = localTimings.withRemote(millis(result.sanitizationNanos()), millis(result.publishNanos()));
                    int remoteErrors = result.outcome() == ExportResult.RemoteOutcome.FAILED
                            || result.outcome() == ExportResult.RemoteOutcome.CANCELLED ? 1 : 0;
                    synchronized (this) {
                        if (status == localStatus) {
                            status = new ExportStatus(localStatus.mineColoniesDetected(), localStatus.mineColoniesVersion(),
                                    localStatus.coloniesDetected(), localStatus.lastExportAt(), localStatus.lastSuccessfulExportAt(),
                                    localStatus.lastExportDurationMs(), localStatus.outputRoot(), localStatus.adapterName(),
                                    localStatus.lastWarningCount(), localStatus.lastErrorCount() + remoteErrors,
                                    localStatus.latestFiles(), localStatus.lastLocalOutcome(), result.outcome().name().toLowerCase(), timings);
                        }
                    }
                    logRemoteResult(request, result);
                });
    }

    private void logRemoteResult(ExportRequest request, RemotePublishQueue.BatchResult result) {
        if (result.outcome() == ExportResult.RemoteOutcome.SUCCEEDED) {
            if (request.trigger() == ExportTrigger.INTERVAL && result.attempts() == 1) {
                logger.debug("Colony Bridge uploaded periodic snapshot ({} bytes).", result.payloadBytes());
            } else {
                logger.info("Colony Bridge uploaded sanitized snapshot batch ({} bytes, {} attempts).",
                        result.payloadBytes(), result.attempts());
            }
        } else if (result.outcome() == ExportResult.RemoteOutcome.FAILED) {
            logger.warn("Colony Bridge remote sync failed; the local snapshot was saved normally: {}",
                    result.failure() == null ? "unknown failure" : result.failure().getMessage());
        } else if (result.outcome() == ExportResult.RemoteOutcome.CANCELLED) {
            logger.warn("Colony Bridge remote sync was cancelled during bounded shutdown; the local snapshot remains saved.");
        }
    }

    public void shutdown() {
        shutdown(DEFAULT_SHUTDOWN_TIMEOUT);
    }

    public void shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        ExecutorService localExecutor;
        RemotePublishQueue localRemote;
        synchronized (this) {
            accepting = false;
            localExecutor = ioExecutor;
            localRemote = remoteQueue;
        }
        long deadline = System.nanoTime() + Math.max(0, timeout.toNanos());
        if (localExecutor != null) {
            localExecutor.shutdown();
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                if (!localExecutor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) localExecutor.shutdownNow();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                localExecutor.shutdownNow();
            }
        }
        if (localRemote != null) localRemote.shutdown(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        synchronized (this) {
            if (activeExport != null && !activeExport.isDone()) {
                activeExport.completeExceptionally(new CancellationException("Exporter stopped before local completion."));
                activeExport = null;
            }
            stores.clear();
        }
    }

    private synchronized SnapshotStore store(Path outputRoot, boolean pretty) {
        StoreKey key = new StoreKey(outputRoot.toAbsolutePath().normalize(), pretty);
        return stores.computeIfAbsent(key, ignored -> new SnapshotStore(key.root, key.pretty));
    }

    private void complete(ExportRequest request, CompletableFuture<ExportResult> future,
                          ExportResult.LocalOutcome local, ExportResult.RemoteOutcome remote,
                          ExportStatus next, ExportTimings timings) {
        clearActiveExport(future);
        future.complete(new ExportResult(request, local, remote, next, timings));
    }

    private void fail(ExportRequest request, CompletableFuture<ExportResult> future, Exception failure, long collectionMs) {
        ExportTimings timings = new ExportTimings(collectionMs, 0, 0, 0, 0, 0, 0, collectionMs);
        ExportStatus failed = statusFor(request, 0, request.requestedAt(), null, outputRoot(request.server()),
                0, 1, List.of(), ExportResult.LocalOutcome.FAILED, ExportResult.RemoteOutcome.DISABLED, timings);
        status = failed;
        clearActiveExport(future);
        future.completeExceptionally(failure);
    }

    private ExportStatus statusFor(ExportRequest request, int colonies, Instant started, Instant success, Path root,
                                   int warnings, int errors, List<Path> latestFiles,
                                   ExportResult.LocalOutcome local, ExportResult.RemoteOutcome remote, ExportTimings timings) {
        return new ExportStatus(adapter.status().available(), adapter.status().mineColoniesVersion(), colonies,
                started, success, timings.localTotalMs(), root, adapter.status().adapterName(), warnings, errors,
                List.copyOf(latestFiles), local.name().toLowerCase(), remote.name().toLowerCase(), timings);
    }

    private synchronized void clearActiveExport(CompletableFuture<ExportResult> completed) {
        if (activeExport == completed) activeExport = null;
    }

    public Path outputRoot(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getServerDirectory().resolve("colonybridge").normalize();
    }

    private static Thread daemonThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static long elapsedMillis(long startedNanos) {
        return millis(System.nanoTime() - startedNanos);
    }

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, nanos));
    }

    private record StoreKey(Path root, boolean pretty) {
    }
}
