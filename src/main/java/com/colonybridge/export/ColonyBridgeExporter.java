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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ColonyBridgeExporter {
    private static final RemoteSnapshotPublisher REMOTE_PUBLISHER = new RemoteSnapshotPublisher();
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "colonybridge-io");
        thread.setDaemon(true);
        return thread;
    });

    private final Logger logger;
    private final MineColoniesAdapter adapter;
    private volatile ExportStatus status;
    private volatile ExportTrigger lastSuccessfulTrigger;
    private CompletableFuture<ExportStatus> activeExport;

    public ColonyBridgeExporter(Logger logger, MineColoniesAdapter adapter) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.status = new ExportStatus(adapter.status().available(), adapter.status().mineColoniesVersion(), 0,
                null, null, 0, null, adapter.status().adapterName(), 0, 0, List.of());
    }

    public ExportStatus status() {
        return status;
    }

    public synchronized boolean exportInProgress() {
        return activeExport != null && !activeExport.isDone();
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
        return export(server, config, trigger, Optional.empty());
    }

    public CompletableFuture<ExportStatus> exportOne(MinecraftServer server, BridgeConfigValues config,
                                                     ExportTrigger trigger, int colonyId) {
        return export(server, config, trigger, Optional.of(colonyId));
    }

    private CompletableFuture<ExportStatus> export(MinecraftServer server, BridgeConfigValues config,
                                                   ExportTrigger trigger, Optional<Integer> colonyId) {
        if (!config.enabled()) return CompletableFuture.completedFuture(status);

        final CompletableFuture<ExportStatus> future;
        synchronized (this) {
            if (activeExport != null && !activeExport.isDone()) {
                if (trigger == ExportTrigger.MANUAL) {
                    CompletableFuture<ExportStatus> running = activeExport;
                    logger.debug("Colony Bridge manual export queued behind the active export.");
                    return running.handle((ignored, failure) -> null)
                            .thenCompose(ignored -> export(server, config, trigger, colonyId));
                }
                logger.debug("Colony Bridge export coalesced because an export is already running.");
                return activeExport;
            }
            future = new CompletableFuture<>();
            activeExport = future;
        }

        Runnable collect = () -> {
            Instant started = Instant.now();
            Path outputRoot = outputRoot(server);
            ServerLevelContext context = new ServerLevelContext(server, outputRoot);
            final List<ColonySnapshot> snapshots;
            try {
                Instant generatedAt = Instant.now();
                snapshots = colonyId.isPresent()
                        ? adapter.collectById(context, config, colonyId.get(), trigger, generatedAt)
                                .map(List::of).orElseGet(List::of)
                        : adapter.collectAll(context, config, trigger, generatedAt);
            } catch (Exception failure) {
                clearActiveExport(future);
                future.completeExceptionally(failure);
                logger.error("Colony Bridge collection failed", failure);
                return;
            }

            if (colonyId.isPresent() && snapshots.isEmpty()) {
                ExportStatus notFound = new ExportStatus(adapter.status().available(),
                        adapter.status().mineColoniesVersion(), 0, started, status.lastSuccessfulExportAt(),
                        Duration.between(started, Instant.now()).toMillis(), outputRoot,
                        adapter.status().adapterName(), 0, 0, List.of());
                status = notFound;
                clearActiveExport(future);
                future.complete(notFound);
                return;
            }

            CompletableFuture.runAsync(() -> writeSnapshots(outputRoot, config, trigger, started, snapshots, future),
                    IO_EXECUTOR);
        };
        if (server.isSameThread()) collect.run();
        else server.execute(collect);
        return future;
    }

    private void writeSnapshots(Path outputRoot, BridgeConfigValues config, ExportTrigger trigger, Instant started,
                                List<ColonySnapshot> snapshots, CompletableFuture<ExportStatus> future) {
        List<Path> latestFiles = new ArrayList<>();
        int warnings = 0;
        int errors = 0;
        try {
            SnapshotStore store = new SnapshotStore(outputRoot, config.prettyPrintJson());
            for (ColonySnapshot snapshot : snapshots) {
                SnapshotWriteResult result = store.writeSnapshot(snapshot, config.retainSnapshots());
                latestFiles.add(result.latestPath());
                if (config.remoteSyncConfigured()) {
                    try {
                        RemotePublishResult remote = REMOTE_PUBLISHER.publish(snapshot,
                                config.remoteEndpoint(), config.remoteToken());
                        if (ExportTrigger.INTERVAL.jsonName().equals(snapshot.trigger()) && remote.attempts() == 1) {
                            logger.debug("Colony Bridge uploaded periodic snapshot ({} bytes).", remote.payloadBytes());
                        } else {
                            logger.info("Colony Bridge uploaded a sanitized snapshot to Kingdom Chronicle ({} bytes, {} attempt{}).",
                                    remote.payloadBytes(), remote.attempts(), remote.attempts() == 1 ? "" : "s");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        errors++;
                        logger.warn("Colony Bridge remote sync was interrupted; the local snapshot was saved normally.");
                    } catch (Exception remoteError) {
                        errors++;
                        logger.warn("Colony Bridge remote sync failed; the local snapshot was saved normally: {}",
                                remoteError.getMessage());
                        logger.debug("Colony Bridge remote sync failure details", remoteError);
                    }
                } else if (config.remoteSyncEnabled()) {
                    errors++;
                    logger.warn("Colony Bridge remote sync is enabled but its HTTPS endpoint or token is missing or invalid.");
                }
                warnings += snapshot.warnings().size();
                errors += snapshot.errors().size();
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
            store.writeBridgeInfo(info);

            ExportStatus next = new ExportStatus(adapter.status().available(), adapter.status().mineColoniesVersion(),
                    snapshots.size(), started, lastSuccess, durationMs, outputRoot, adapter.status().adapterName(),
                    warnings, errors, List.copyOf(latestFiles));
            status = next;
            lastSuccessfulTrigger = trigger;
            clearActiveExport(future);
            future.complete(next);
        } catch (Exception failure) {
            long durationMs = Duration.between(started, Instant.now()).toMillis();
            status = new ExportStatus(adapter.status().available(), adapter.status().mineColoniesVersion(),
                    snapshots.size(), started, null, durationMs, outputRoot, adapter.status().adapterName(),
                    warnings, errors + 1, List.copyOf(latestFiles));
            logger.error("Colony Bridge write failed", failure);
            clearActiveExport(future);
            future.completeExceptionally(failure);
        } finally {
            clearActiveExport(future);
        }
    }

    private synchronized void clearActiveExport(CompletableFuture<ExportStatus> completed) {
        if (activeExport == completed) activeExport = null;
    }

    public Path outputRoot(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").getServerDirectory().resolve("colonybridge").normalize();
    }
}
