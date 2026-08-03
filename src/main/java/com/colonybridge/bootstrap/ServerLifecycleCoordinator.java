package com.colonybridge.bootstrap;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.config.BridgeSettings;
import com.colonybridge.config.ColonyBridgeConfig;
import com.colonybridge.export.ColonyBridgeExporter;
import com.colonybridge.export.ExportStatus;
import com.colonybridge.market.MarketManager;
import com.colonybridge.notification.ColonyDayCelebration;
import com.colonybridge.notification.ColonyDayTracker;
import com.colonybridge.notification.ExportNotificationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerLifecycleCoordinator {
    private final Logger logger;
    private final ColonyBridgeExporter exporter;
    private final ColonyDayTracker dayTracker;
    private final ExportNotificationService exportNotifications;
    private int ticksUntilPeriodicExport = LifecycleTiming.ticksForSeconds(
            BridgeConfigValues.defaults().periodicExportSeconds());
    private int ticksUntilConfigRefresh;
    private int ticksUntilMarketRefresh = LifecycleTiming.MARKET_REFRESH_INTERVAL_TICKS;
    private BridgeSettings cachedSettings = BridgeSettings.defaults();

    public ServerLifecycleCoordinator(Logger logger, ColonyBridgeExporter exporter) {
        this(logger, exporter,
                new ColonyDayTracker(logger, new ColonyDayCelebration(), LifecycleTiming.DAY_CHECK_INTERVAL_TICKS),
                new ExportNotificationService());
    }

    ServerLifecycleCoordinator(Logger logger, ColonyBridgeExporter exporter, ColonyDayTracker dayTracker,
                               ExportNotificationService exportNotifications) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.dayTracker = Objects.requireNonNull(dayTracker, "dayTracker");
        this.exportNotifications = Objects.requireNonNull(exportNotifications, "exportNotifications");
    }

    public void serverStarted(MinecraftServer server) {
        exporter.start();
        MarketManager.get(server);
        BridgeSettings settings = refreshConfig();
        BridgeConfigValues config = settings.bridgeValues();
        ticksUntilPeriodicExport = LifecycleTiming.ticksForSeconds(settings.export().periodicSeconds());
        ticksUntilMarketRefresh = LifecycleTiming.MARKET_REFRESH_INTERVAL_TICKS;
        dayTracker.start(settings.enabled() && settings.notifications().dayCounterEnabled());
        if (settings.enabled() && settings.export().onStartup()) {
            exporter.exportAll(server, config, ExportTrigger.STARTUP);
        }
    }

    public void serverStopping(MinecraftServer server) {
        BridgeSettings settings = refreshConfig();
        BridgeConfigValues config = settings.bridgeValues();
        if (settings.enabled() && settings.export().onShutdown()
                && !exporter.hasRecentSuccessfulExport(ExportTrigger.DISCONNECT,
                LifecycleTiming.SHUTDOWN_EXPORT_DEDUP_WINDOW)) {
            try {
                exporter.exportAll(server, config, ExportTrigger.SHUTDOWN)
                        .get(LifecycleTiming.SHUTDOWN_EXPORT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                logger.warn("Colony Bridge shutdown export was interrupted; server shutdown will continue.");
            } catch (TimeoutException timeout) {
                logger.warn("Colony Bridge shutdown export exceeded {} seconds; server shutdown will continue.",
                        LifecycleTiming.SHUTDOWN_EXPORT_TIMEOUT.toSeconds());
            } catch (Exception failure) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure;
                logger.error("Colony Bridge shutdown export failed; server shutdown will continue.", cause);
            }
        } else if (settings.enabled() && settings.export().onShutdown()) {
            logger.debug("Colony Bridge skipped a redundant shutdown export after a recent successful disconnect export.");
        }
        exporter.shutdown(LifecycleTiming.BACKGROUND_SHUTDOWN_TIMEOUT);
        exportNotifications.clear();
        dayTracker.clear();
        MarketManager.close(server);
    }

    public void serverTick(MinecraftServer server) {
        exportNotifications.tick(server);
        BridgeSettings settings = tickConfig();
        BridgeConfigValues config = settings.bridgeValues();
        dayTracker.tick(server, settings.enabled() && settings.notifications().dayCounterEnabled());
        if (--ticksUntilMarketRefresh <= 0) {
            ticksUntilMarketRefresh = LifecycleTiming.MARKET_REFRESH_INTERVAL_TICKS;
            MarketManager.get(server).tickOnlineMarket();
        }
        if (!settings.export().periodicEnabled() || !settings.enabled()
                || server.getPlayerList().getPlayerCount() == 0) return;
        if (--ticksUntilPeriodicExport <= 0) {
            ticksUntilPeriodicExport = LifecycleTiming.ticksForSeconds(settings.export().periodicSeconds());
            CompletableFuture<ExportStatus> export = exporter.exportAll(server, config, ExportTrigger.INTERVAL);
            if (settings.export().showProgress()) exportNotifications.queueAll(server, export);
        }
    }

    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BridgeSettings settings = refreshConfig();
        BridgeConfigValues config = settings.bridgeValues();
        if (!settings.enabled() || !settings.export().onPlayerJoin()) return;
        if (exporter.hasRecentSuccessfulExport(LifecycleTiming.JOIN_EXPORT_DEDUP_WINDOW)) {
            logger.debug("Colony Bridge skipped a redundant player-join export after a recent successful export.");
            return;
        }
        CompletableFuture<ExportStatus> export = exporter.exportAll(player.getServer(), config, ExportTrigger.PLAYER_JOIN);
        ticksUntilPeriodicExport = LifecycleTiming.ticksForSeconds(settings.export().periodicSeconds());
        if (settings.export().showProgress()) exportNotifications.queue(player, export);
    }

    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BridgeSettings settings = refreshConfig();
        if (!settings.enabled() || !settings.export().onLastPlayerDisconnect()
                || event.getEntity().getServer() == null) return;
        MinecraftServer server = event.getEntity().getServer();
        if (server.getPlayerList().getPlayerCount() <= 1) {
            exporter.exportAll(server, settings.bridgeValues(), ExportTrigger.DISCONNECT);
        }
    }

    private BridgeSettings tickConfig() {
        if (--ticksUntilConfigRefresh <= 0) return refreshConfig();
        return cachedSettings;
    }

    private BridgeSettings refreshConfig() {
        BridgeSettings next = ColonyBridgeConfig.settings();
        if (next.export().periodicSeconds() != cachedSettings.export().periodicSeconds()) {
            ticksUntilPeriodicExport = Math.min(ticksUntilPeriodicExport,
                    LifecycleTiming.ticksForSeconds(next.export().periodicSeconds()));
        }
        cachedSettings = next;
        ticksUntilConfigRefresh = LifecycleTiming.CONFIG_REFRESH_INTERVAL_TICKS;
        return next;
    }
}
