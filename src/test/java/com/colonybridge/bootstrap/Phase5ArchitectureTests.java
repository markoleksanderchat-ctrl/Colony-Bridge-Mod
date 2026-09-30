package com.colonybridge.bootstrap;

import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.config.BridgeSettings;
import com.colonybridge.market.MarketConfig;
import com.colonybridge.market.OnlineMarketConfig;
import com.electronwill.nightconfig.core.file.FileConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Phase5ArchitectureTests {
    private Phase5ArchitectureTests() {
    }

    public static void run() throws Exception {
        bootstrapIsAThinNeoForgeEntrypoint();
        lifecyclePreservesEveryExportTrigger();
        commandTreeAndPermissionsRemainStable();
        notificationPresentationRemainsStable();
        typedSettingsRoundTripLegacyValues();
        currentTomlFixtureLoadsWithoutMigration();
        configSpecRetainsCurrentSectionsKeysAndDefaults();
        timingConstantsRetainUnitsAndValues();
    }

    private static void bootstrapIsAThinNeoForgeEntrypoint() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/colonybridge/ColonyBridge.java"));
        require(source.lines().count() < 100, "NeoForge entrypoint must remain a thin composition root");
        for (String delegate : List.of("lifecycle.serverStarted", "lifecycle.serverStopping", "lifecycle.serverTick",
                "lifecycle.playerLoggedIn", "lifecycle.playerLoggedOut", "ColonyBridgeCommand.register")) {
            require(source.contains(delegate), "entrypoint must delegate " + delegate);
        }
        require(!source.contains("IColonyManager") && !source.contains("DayCounterState")
                        && !source.contains("Component.literal"),
                "entrypoint must not own collection, notification state, or presentation");
    }

    private static void lifecyclePreservesEveryExportTrigger() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonybridge/bootstrap/ServerLifecycleCoordinator.java"));
        for (String invocation : List.of("exporter.exportAll(server, config, ExportTrigger.STARTUP)",
                "exporter.exportAll(server, config, ExportTrigger.INTERVAL)",
                "exporter.exportAll(player.getServer(), config, ExportTrigger.PLAYER_JOIN)",
                "exporter.exportAll(server, settings.bridgeValues(), ExportTrigger.DISCONNECT)",
                "exporter.exportAll(server, config, ExportTrigger.SHUTDOWN)")) {
            require(count(source, invocation) == 1, "lifecycle trigger must occur exactly once: " + invocation);
        }
        require(source.contains("getPlayerCount() == 0") && source.contains("getPlayerCount() <= 1"),
                "periodic and last-player trigger conditions must remain unchanged");
        require(source.contains("JOIN_EXPORT_DEDUP_WINDOW") && source.contains("SHUTDOWN_EXPORT_DEDUP_WINDOW"),
                "join and shutdown deduplication windows must remain explicit");
        String login = source.substring(source.indexOf("void playerLoggedIn"), source.indexOf("void playerLoggedOut"));
        require(!login.contains("dayTracker"), "a simple player join must never trigger a colony-day title");
    }

    private static void commandTreeAndPermissionsRemainStable() throws Exception {
        String root = Files.readString(Path.of("src/main/java/com/colonybridge/command/ColonyBridgeCommand.java"));
        require(root.contains("literal(\"colonybridge\")") && root.contains("literal(\"cb\")"),
                "both command roots must remain registered");
        require(root.contains("!source.getServer().isDedicatedServer() || source.hasPermission(2)"),
                "integrated servers and operator level two must retain access");
        require(count(root, "StatusCommands.status") == 2 && count(root, "ExportCommands.export") == 3,
                "long and short command trees must retain status and export routes");
        require(count(root, "MarketCommands.register") == 1,
                "Royal Exchange commands must remain under /colonybridge market only");

        String all = root
                + Files.readString(Path.of("src/main/java/com/colonybridge/command/StatusCommands.java"))
                + Files.readString(Path.of("src/main/java/com/colonybridge/command/ExportCommands.java"))
                + Files.readString(Path.of("src/main/java/com/colonybridge/command/MarketCommands.java"));
        for (String wording : List.of("Colony Bridge status", "Colony Bridge export queued.",
                "Colony Bridge exports are disabled in the server config.", "Royal Exchange operator controls are disabled.",
                "Only vanilla goods other than diamond currency, blocks and ores may be quoted.", "No latest snapshot files have been written yet.")) {
            require(all.contains(wording), "command wording drifted: " + wording);
        }
    }

    private static void notificationPresentationRemainsStable() throws Exception {
        String day = Files.readString(Path.of(
                "src/main/java/com/colonybridge/notification/ColonyDayCelebration.java"));
        for (String marker : List.of("ClientboundSetTitlesAnimationPacket(20, 80, 30)", "COLONY DAY ",
                "ChatFormatting.GOLD", "ChatFormatting.BOLD", "ChatFormatting.YELLOW",
                "FIREWORK_ROCKET_LAUNCH", "0.8F", "1.0F")) {
            require(day.contains(marker), "colony-day presentation drifted: " + marker);
        }
        String exports = Files.readString(Path.of(
                "src/main/java/com/colonybridge/notification/ExportNotificationService.java"));
        require(exports.contains("Colony Bridge export failed - check the log")
                        && exports.contains("Colony Bridge snapshot saved (")
                        && exports.contains("EXPERIENCE_ORB_PICKUP"),
                "export progress wording or sound drifted");
    }

    private static void typedSettingsRoundTripLegacyValues() {
        BridgeConfigValues legacy = new BridgeConfigValues(true, false, true, false, true, true, 300, 75,
                false, true, true, false, true, true, " https://example.com/snapshot ", " token ").validated();
        BridgeSettings settings = BridgeSettings.from(legacy, MarketConfig.defaults(), OnlineMarketConfig.defaults());
        require(legacy.equals(settings.bridgeValues()), "typed settings must preserve every legacy Bridge value");
        require(settings.export().periodicSeconds() == 300 && settings.privacy().includeCitizenPositions()
                        && settings.notifications().dayCounterEnabled() && settings.remoteSync().enabled(),
                "typed settings views must retain their owned values");
    }

    private static void currentTomlFixtureLoadsWithoutMigration() {
        Path fixture = Path.of("src/test/resources/phase5-current-config.toml");
        try (FileConfig config = FileConfig.of(fixture)) {
            config.load();
            for (String path : List.of("export.enabled", "export.exportOnStartup", "export.exportOnPlayerJoin",
                    "export.exportOnShutdown", "export.exportOnLastPlayerDisconnect", "export.periodicExportEnabled",
                    "export.periodicExportSeconds", "export.retainSnapshots", "export.prettyPrintJson",
                    "export.showExportProgress", "export.includeCitizenPositions", "export.includeOwnerUuid",
                    "remoteSync.enabled", "remoteSync.endpoint", "remoteSync.token",
                    "notifications.dayCounterEnabled", "royalExchange.quoteDelaySeconds",
                    "royalExchange.onlinePricesEnabled", "royalExchange.onlineInfluenceStrength")) {
                require(config.contains(path), "current TOML key must load unchanged: " + path);
            }
            require(Integer.valueOf(300).equals(config.get("export.periodicExportSeconds")),
                    "current periodic interval must load unchanged");
            require(Boolean.TRUE.equals(config.get("export.includeCitizenPositions")),
                    "current privacy setting must load unchanged");
            require("https://example.com/snapshot".equals(config.get("remoteSync.endpoint")),
                    "current remote endpoint must load unchanged");
        }
    }

    private static void configSpecRetainsCurrentSectionsKeysAndDefaults() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/colonybridge/config/ColonyBridgeConfig.java"));
        for (String section : List.of("export", "remoteSync", "notifications", "royalExchange")) {
            require(source.contains("push(\"" + section + "\")"), "config section must remain unchanged: " + section);
        }
        for (String key : List.of("enabled", "exportOnStartup", "exportOnPlayerJoin", "exportOnShutdown",
                "exportOnLastPlayerDisconnect", "periodicExportEnabled", "periodicExportSeconds", "retainSnapshots",
                "prettyPrintJson", "showExportProgress", "includeCitizenPositions", "includeOwnerUuid", "endpoint",
                "token", "dayCounterEnabled", "quoteDelaySeconds", "quoteValiditySeconds", "eventFrequencyMinutes",
                "volatilityStrength", "minimumPriceMultiplier", "maximumPriceMultiplier", "buyingEnabled",
                "operatorEventControls", "sellingEnabled", "sellPriceRatio", "dailySellDiamondLimit",
                "activeContractCount", "contractDurationMinutes", "contractRewardPremium", "onlinePricesEnabled",
                "onlineRefreshSeconds", "onlineMaximumAgeMinutes", "onlineInfluenceStrength")) {
            require(source.contains("\"" + key + "\""), "config key must remain unchanged: " + key);
        }
        require(BridgeSettings.defaults().bridgeValues().equals(BridgeConfigValues.defaults()),
                "typed default settings must preserve every legacy default");
        require(MarketConfig.defaults().equals(BridgeSettings.defaults().market().local())
                        && OnlineMarketConfig.defaults().equals(BridgeSettings.defaults().market().online()),
                "typed market settings must preserve local and online defaults");
    }

    private static void timingConstantsRetainUnitsAndValues() {
        require(LifecycleTiming.TICKS_PER_SECOND == 20, "tick rate must remain 20 per second");
        require(LifecycleTiming.CONFIG_REFRESH_INTERVAL_TICKS == 20, "config refresh must remain one second");
        require(LifecycleTiming.DAY_CHECK_INTERVAL_TICKS == 20, "day checks must remain one second");
        require(LifecycleTiming.MARKET_REFRESH_INTERVAL_TICKS == 20, "market checks must remain one second");
        require(LifecycleTiming.ticksForSeconds(120) == 2_400, "two-minute exports must remain 2400 ticks");
        require(LifecycleTiming.SHUTDOWN_EXPORT_TIMEOUT.toSeconds() == 20,
                "shutdown export timeout must remain twenty seconds");
    }

    private static int count(String source, String needle) {
        int result = 0;
        for (int offset = 0; (offset = source.indexOf(needle, offset)) >= 0; offset += needle.length()) result++;
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
