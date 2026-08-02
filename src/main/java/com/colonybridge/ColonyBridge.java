package com.colonybridge;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.config.ColonyBridgeConfig;
import com.colonybridge.export.ColonyBridgeExporter;
import com.colonybridge.export.ExportStatus;
import com.colonybridge.minecolonies.MineColoniesAdapterFactory;
import com.colonybridge.market.MarketManager;
import com.colonybridge.market.trader.MarketRegistries;
import com.colonybridge.utility.DayCounterState;
import com.colonybridge.utility.DayCelebrationMessages;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Mod(ColonyBridgeConstants.MOD_ID)
public final class ColonyBridge {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final int TICKS_PER_SECOND = 20;
    private static final int CONFIG_REFRESH_TICKS = TICKS_PER_SECOND;
    private static final int DAY_CHECK_TICKS = TICKS_PER_SECOND;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final Duration BACKGROUND_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration JOIN_EXPORT_DEDUP_WINDOW = Duration.ofSeconds(10);
    private static final Duration SHUTDOWN_EXPORT_DEDUP_WINDOW = Duration.ofSeconds(10);

    private final ColonyBridgeExporter exporter;
    private final List<ExportNotification> exportNotifications = new ArrayList<>();
    private final Map<Integer, DayCounterState> colonyDayCounters = new HashMap<>();
    private int ticksUntilPeriodicExport = ticksForSeconds(BridgeConfigValues.defaults().periodicExportSeconds());
    private int ticksUntilDayCheck = DAY_CHECK_TICKS;
    private int ticksUntilConfigRefresh;
    private int ticksUntilMarketRefresh = TICKS_PER_SECOND;
    private BridgeConfigValues cachedConfig = BridgeConfigValues.defaults();
    private boolean dayCounterWasEnabled;

    public ColonyBridge(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ColonyBridgeConfig.SERVER_SPEC);
        MarketRegistries.register(modEventBus);
        modEventBus.addListener(MarketRegistries::addCreativeTabContents);
        exporter = new ColonyBridgeExporter(LOGGER, MineColoniesAdapterFactory.create());
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Colony Bridge initialized with adapter {}", exporter.status().adapterName());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        ColonyBridgeCommands.register(event.getDispatcher(), exporter);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        exporter.start();
        MarketManager.get(event.getServer());
        BridgeConfigValues config = refreshConfig();
        ticksUntilPeriodicExport = ticksForSeconds(config.periodicExportSeconds());
        ticksUntilDayCheck = DAY_CHECK_TICKS;
        dayCounterWasEnabled = config.enabled() && config.dayCounterEnabled();
        resetColonyDayCounters();
        if (config.enabled() && config.exportOnStartup()) {
            exporter.exportAll(event.getServer(), config, ExportTrigger.STARTUP);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BridgeConfigValues config = refreshConfig();
        if (config.enabled() && config.exportOnShutdown()
                && !exporter.hasRecentSuccessfulExport(ExportTrigger.DISCONNECT, SHUTDOWN_EXPORT_DEDUP_WINDOW)) {
            try {
                exporter.exportAll(event.getServer(), config, ExportTrigger.SHUTDOWN)
                        .get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Colony Bridge shutdown export was interrupted; server shutdown will continue.");
            } catch (TimeoutException timeout) {
                LOGGER.warn("Colony Bridge shutdown export exceeded {} seconds; server shutdown will continue.", SHUTDOWN_TIMEOUT_SECONDS);
            } catch (Exception failure) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                LOGGER.error("Colony Bridge shutdown export failed; server shutdown will continue.", cause);
            }
        } else if (config.enabled() && config.exportOnShutdown()) {
            LOGGER.debug("Colony Bridge skipped a redundant shutdown export after a recent successful disconnect export.");
        }
        exporter.shutdown(BACKGROUND_SHUTDOWN_TIMEOUT);
        MarketManager.close(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        updateExportNotifications(event.getServer());
        BridgeConfigValues config = tickConfig();
        updateDayCounter(event.getServer(), config);
        if (--ticksUntilMarketRefresh <= 0) {
            ticksUntilMarketRefresh = TICKS_PER_SECOND;
            MarketManager.get(event.getServer()).tickOnlineMarket();
        }
        if (!config.periodicExportEnabled() || !config.enabled() || event.getServer().getPlayerList().getPlayerCount() == 0) {
            return;
        }
        ticksUntilPeriodicExport--;
        if (ticksUntilPeriodicExport <= 0) {
            ticksUntilPeriodicExport = ticksForSeconds(config.periodicExportSeconds());
            CompletableFuture<ExportStatus> export = exporter.exportAll(event.getServer(), config, ExportTrigger.INTERVAL);
            if (config.showExportProgress()) {
                queueExportNotifications(event.getServer(), export);
            }
        }
    }

    private void updateDayCounter(MinecraftServer server, BridgeConfigValues config) {
        boolean enabled = config.enabled() && config.dayCounterEnabled();
        if (!enabled) {
            dayCounterWasEnabled = false;
            return;
        }
        if (!dayCounterWasEnabled) {
            resetColonyDayCounters();
            dayCounterWasEnabled = true;
        }
        if (--ticksUntilDayCheck > 0) {
            return;
        }
        ticksUntilDayCheck = DAY_CHECK_TICKS;

        Map<Integer, Long> changedColonies = new HashMap<>();
        Set<Integer> seenColonies = new HashSet<>();
        IColonyManager manager;
        try {
            manager = IColonyManager.getInstance();
            for (var colony : manager.getAllColonies()) {
                int colonyId = colony.getID();
                seenColonies.add(colonyId);
                try {
                    DayCounterState counter = colonyDayCounters.computeIfAbsent(colonyId, ignored -> new DayCounterState());
                    if (!counter.initialized()) {
                        counter.reset(colony.getDay());
                    } else {
                        counter.observe(colony.getDay()).ifPresent(day -> changedColonies.put(colonyId, day));
                    }
                } catch (RuntimeException failure) {
                    LOGGER.debug("Colony Bridge could not inspect colony day for colony {}.", colonyId, failure);
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.debug("Colony Bridge could not inspect MineColonies day state.", failure);
            return;
        }
        colonyDayCounters.keySet().retainAll(seenColonies);

        if (changedColonies.isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IColony colony;
            try {
                colony = manager.getIColony(player.serverLevel(), player.blockPosition());
            } catch (RuntimeException failure) {
                LOGGER.debug("Colony Bridge could not resolve the colony for player {}.", player.getGameProfile().getName(), failure);
                continue;
            }
            Long colonyDay = colony == null ? null : changedColonies.get(colony.getID());
            if (colonyDay != null) {
                showDayCelebration(player, colonyDay);
            }
        }
    }

    private void resetColonyDayCounters() {
        colonyDayCounters.clear();
        try {
            for (var colony : IColonyManager.getInstance().getAllColonies()) {
                try {
                    DayCounterState counter = new DayCounterState();
                    counter.reset(colony.getDay());
                    colonyDayCounters.put(colony.getID(), counter);
                } catch (RuntimeException failure) {
                    LOGGER.debug("Colony Bridge could not initialize a colony day counter.", failure);
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.debug("Colony Bridge could not initialize colony day counters.", failure);
        }
    }

    private static void showDayCelebration(ServerPlayer player, long day) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("COLONY DAY " + day).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(DayCelebrationMessages.forDay(day)).withStyle(ChatFormatting.YELLOW)));
        player.playNotifySound(SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    @SubscribeEvent
    public void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BridgeConfigValues config = refreshConfig();
        if (!config.enabled() || !config.exportOnPlayerJoin()) {
            return;
        }
        if (exporter.hasRecentSuccessfulExport(JOIN_EXPORT_DEDUP_WINDOW)) {
            LOGGER.debug("Colony Bridge skipped a redundant player-join export after a recent successful export.");
            return;
        }

        CompletableFuture<ExportStatus> export = exporter.exportAll(player.getServer(), config, ExportTrigger.PLAYER_JOIN);
        ticksUntilPeriodicExport = ticksForSeconds(config.periodicExportSeconds());
        if (config.showExportProgress()) {
            queueExportNotification(player, export);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        BridgeConfigValues config = refreshConfig();
        if (!config.enabled() || !config.exportOnLastPlayerDisconnect() || event.getEntity().getServer() == null) {
            return;
        }
        var server = event.getEntity().getServer();
        if (server.getPlayerList().getPlayerCount() <= 1) {
            exporter.exportAll(server, config, ExportTrigger.DISCONNECT);
        }
    }

    private void updateExportNotifications(MinecraftServer server) {
        Iterator<ExportNotification> iterator = exportNotifications.iterator();
        while (iterator.hasNext()) {
            ExportNotification display = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(display.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }

            if (display.export.isCompletedExceptionally()) {
                player.displayClientMessage(Component.literal("Colony Bridge export failed - check the log").withStyle(ChatFormatting.RED), false);
                iterator.remove();
                continue;
            }

            if (display.export.isDone()) {
                ExportStatus status = display.export.getNow(null);
                int colonies = status == null ? 0 : status.coloniesDetected();
                String colonyLabel = colonies == 1 ? "colony" : "colonies";
                player.displayClientMessage(Component.literal("Colony Bridge snapshot saved (" + colonies + " " + colonyLabel + ")").withStyle(ChatFormatting.GREEN), false);
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.2F);
                iterator.remove();
            }
        }
    }

    private void queueExportNotifications(MinecraftServer server, CompletableFuture<ExportStatus> export) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            queueExportNotification(player, export);
        }
    }

    private void queueExportNotification(ServerPlayer player, CompletableFuture<ExportStatus> export) {
        exportNotifications.removeIf(display -> display.playerId.equals(player.getUUID()));
        exportNotifications.add(new ExportNotification(player.getUUID(), export));
    }

    private BridgeConfigValues tickConfig() {
        if (--ticksUntilConfigRefresh <= 0) return refreshConfig();
        return cachedConfig;
    }

    private BridgeConfigValues refreshConfig() {
        BridgeConfigValues next = ColonyBridgeConfig.values();
        if (next.periodicExportSeconds() != cachedConfig.periodicExportSeconds()) {
            ticksUntilPeriodicExport = Math.min(ticksUntilPeriodicExport, ticksForSeconds(next.periodicExportSeconds()));
        }
        cachedConfig = next;
        ticksUntilConfigRefresh = CONFIG_REFRESH_TICKS;
        return next;
    }

    private static int ticksForSeconds(int seconds) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, (long) Math.max(1, seconds) * TICKS_PER_SECOND));
    }

    private record ExportNotification(UUID playerId, CompletableFuture<ExportStatus> export) {
    }
}
