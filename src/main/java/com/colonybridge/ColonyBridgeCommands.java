package com.colonybridge;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.config.ColonyBridgeConfig;
import com.colonybridge.export.ColonyBridgeExporter;
import com.colonybridge.export.ExportStatus;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.colonybridge.market.MarketEventManager;
import com.colonybridge.market.MarketManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionException;

public final class ColonyBridgeCommands {
    private ColonyBridgeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ColonyBridgeExporter exporter) {
        dispatcher.register(Commands.literal("colonybridge")
                .requires(ColonyBridgeCommands::canUseBridgeCommands)
                .then(Commands.literal("status").executes(context -> status(context.getSource(), exporter)))
                .then(Commands.literal("paths").executes(context -> paths(context.getSource(), exporter)))
                .then(Commands.literal("version").executes(context -> version(context.getSource(), exporter)))
                .then(Commands.literal("export")
                        .executes(context -> exportAll(context.getSource(), exporter))
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .executes(context -> exportOne(context.getSource(), exporter, IntegerArgumentType.getInteger(context, "colonyId")))))
                .then(marketCommands()));
        dispatcher.register(Commands.literal("cb")
                .requires(ColonyBridgeCommands::canUseBridgeCommands)
                .executes(context -> exportAll(context.getSource(), exporter))
                .then(Commands.literal("status").executes(context -> status(context.getSource(), exporter)))
                .then(Commands.literal("paths").executes(context -> paths(context.getSource(), exporter)))
                .then(Commands.literal("path").executes(context -> paths(context.getSource(), exporter)))
                .then(Commands.literal("version").executes(context -> version(context.getSource(), exporter)))
                .then(Commands.literal("export")
                        .executes(context -> exportAll(context.getSource(), exporter))
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .executes(context -> exportOne(context.getSource(), exporter, IntegerArgumentType.getInteger(context, "colonyId"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> marketCommands() {
        return Commands.literal("market")
                .then(Commands.literal("status").executes(context -> marketStatus(context.getSource())))
                .then(Commands.literal("events").executes(context -> marketEvents(context.getSource())))
                .then(Commands.literal("contracts").executes(context -> marketContracts(context.getSource())))
                .then(Commands.literal("reset").executes(context -> marketReset(context.getSource())))
                .then(Commands.literal("forceevent")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    MarketEventManager.TEMPLATES.forEach(template -> builder.suggest(template.id()));
                                    return builder.buildFuture();
                                })
                                .executes(context -> marketForceEvent(context.getSource(), StringArgumentType.getString(context, "event")))))
                .then(Commands.literal("quote")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 1024))
                                        .executes(context -> marketQuote(context.getSource(), StringArgumentType.getString(context, "item"),
                                                IntegerArgumentType.getInteger(context, "quantity"))))));
    }

    private static int marketStatus(CommandSourceStack source) {
        MarketManager manager = MarketManager.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Royal Exchange: " + manager.recordCount() + " active goods, "
                + manager.quoteCount() + " saved quotes, " + manager.events().size() + " active events."), false);
        source.sendSuccess(() -> Component.literal("Online Exchange: " + manager.onlineMarketStatus() + "."), false);
        return 1;
    }

    private static int marketEvents(CommandSourceStack source) {
        MarketManager manager = MarketManager.get(source.getServer());
        var events = manager.events();
        if (events.isEmpty()) source.sendSuccess(() -> Component.literal("The Royal Exchange reports a quiet market."), false);
        events.forEach(event -> source.sendSuccess(() -> Component.literal(event.title() + ": " + event.description()), false));
        return events.size();
    }

    private static int marketContracts(CommandSourceStack source) {
        var contracts = MarketManager.get(source.getServer()).contracts();
        if (contracts.isEmpty()) source.sendSuccess(() -> Component.literal("The Crown has posted no active contracts."), false);
        contracts.forEach(contract -> source.sendSuccess(() -> Component.literal(contract.title() + ": deliver "
                + contract.quantity() + " " + contract.itemId() + " for " + contract.rewardDiamonds() + " diamonds."), false));
        return contracts.size();
    }

    private static int marketForceEvent(CommandSourceStack source, String eventId) {
        if (!ColonyBridgeConfig.marketValues().operatorEventControls()) {
            source.sendFailure(Component.literal("Royal Exchange operator event controls are disabled."));
            return 0;
        }
        try {
            var event = MarketManager.get(source.getServer()).forceEvent(eventId);
            source.sendSuccess(() -> Component.literal("Market event started: " + event.title()), true);
            return 1;
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
    }

    private static int marketReset(CommandSourceStack source) {
        if (!ColonyBridgeConfig.marketValues().operatorEventControls()) {
            source.sendFailure(Component.literal("Royal Exchange operator controls are disabled."));
            return 0;
        }
        MarketManager.get(source.getServer()).reset();
        source.sendSuccess(() -> Component.literal("Royal Exchange market reset with a new deterministic seed."), true);
        return 1;
    }

    private static int marketQuote(CommandSourceStack source, String itemId, int quantity) {
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("A player must request a market quote."));
            return 0;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
        var item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
        if (item == Items.AIR || key == null || !"minecraft".equals(key.getNamespace())) {
            source.sendFailure(Component.literal("Only vanilla minecraft: items may be quoted."));
            return 0;
        }
        var quote = MarketManager.get(source.getServer()).requestQuote(source.getPlayer(), item, quantity);
        source.sendSuccess(() -> Component.literal("Royal Exchange quote requested. " + quote.totalDiamondCost()
                + " diamonds when ready; valid for " + ((quote.expirationTime() - quote.readyTime()) / 1000) + " seconds."), false);
        return 1;
    }

    private static boolean canUseBridgeCommands(CommandSourceStack source) {
        return !source.getServer().isDedicatedServer() || source.hasPermission(2);
    }

    private static int status(CommandSourceStack source, ColonyBridgeExporter exporter) {
        ExportStatus status = exporter.status();
        BridgeConfigValues config = ColonyBridgeConfig.values();
        source.sendSuccess(() -> Component.literal("Colony Bridge status"), false);
        source.sendSuccess(() -> Component.literal("Exports: " + exportSchedule(config)), false);
        source.sendSuccess(() -> Component.literal("Export running: " + exporter.exportInProgress()), false);
        source.sendSuccess(() -> Component.literal("Remote sync: " + remoteSyncStatus(config)), false);
        source.sendSuccess(() -> Component.literal("MineColonies detected: " + status.mineColoniesDetected()), false);
        source.sendSuccess(() -> Component.literal("MineColonies version: " + nullable(status.mineColoniesVersion())), false);
        source.sendSuccess(() -> Component.literal("Colonies detected: " + status.coloniesDetected()), false);
        source.sendSuccess(() -> Component.literal("Last export: " + instant(status.lastExportAt())), false);
        source.sendSuccess(() -> Component.literal("Last successful export: " + instant(status.lastSuccessfulExportAt())), false);
        source.sendSuccess(() -> Component.literal("Last duration ms: " + status.lastExportDurationMs()), false);
        source.sendSuccess(() -> Component.literal("Export directory: " + nullablePath(status.outputRoot())), false);
        source.sendSuccess(() -> Component.literal("Adapter: " + status.adapterName()), false);
        source.sendSuccess(() -> Component.literal("Last warnings/errors: " + status.lastWarningCount() + "/" + status.lastErrorCount()), false);
        return 1;
    }

    private static String exportSchedule(BridgeConfigValues config) {
        if (!config.enabled()) {
            return "disabled";
        }
        if (!config.periodicExportEnabled()) {
            return "automatic interval disabled";
        }
        return "automatic every " + config.periodicExportSeconds() + " seconds";
    }

    private static String remoteSyncStatus(BridgeConfigValues config) {
        if (!config.remoteSyncEnabled()) return "disabled";
        return config.remoteSyncConfigured() ? "enabled" : "misconfigured";
    }

    private static int paths(CommandSourceStack source, ColonyBridgeExporter exporter) {
        ExportStatus status = exporter.status();
        Path root = status.outputRoot() == null ? exporter.outputRoot(source.getServer()) : status.outputRoot();
        source.sendSuccess(() -> Component.literal("Output root: " + root.toAbsolutePath().normalize()), false);
        if (status.latestFiles().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No latest snapshot files have been written yet."), false);
        } else {
            status.latestFiles().forEach(path -> source.sendSuccess(() -> Component.literal("Latest: " + path.toAbsolutePath().normalize()), false));
        }
        return 1;
    }

    private static int version(CommandSourceStack source, ColonyBridgeExporter exporter) {
        ExportStatus status = exporter.status();
        source.sendSuccess(() -> Component.literal("Colony Bridge " + ColonyBridgeConstants.VERSION), false);
        source.sendSuccess(() -> Component.literal("Schema " + ColonyBridgeConstants.SCHEMA_VERSION), false);
        source.sendSuccess(() -> Component.literal("Minecraft " + source.getServer().getServerVersion()), false);
        source.sendSuccess(() -> Component.literal("NeoForge loader: see mod list"), false);
        source.sendSuccess(() -> Component.literal("MineColonies " + nullable(status.mineColoniesVersion())), false);
        return 1;
    }

    private static int exportAll(CommandSourceStack source, ColonyBridgeExporter exporter) {
        BridgeConfigValues config = ColonyBridgeConfig.values();
        if (!config.enabled()) {
            source.sendFailure(Component.literal("Colony Bridge exports are disabled in the server config."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Colony Bridge export queued."), false);
        exporter.exportAll(source.getServer(), config, ExportTrigger.MANUAL)
                .whenComplete((status, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Colony Bridge export failed: " + errorMessage(error)));
                    } else {
                        source.sendSuccess(() -> Component.literal("Colony Bridge exported " + status.coloniesDetected() + " colonies."), false);
                    }
                }));
        return 1;
    }

    private static int exportOne(CommandSourceStack source, ColonyBridgeExporter exporter, int colonyId) {
        BridgeConfigValues config = ColonyBridgeConfig.values();
        if (!config.enabled()) {
            source.sendFailure(Component.literal("Colony Bridge exports are disabled in the server config."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Colony Bridge export queued for colony " + colonyId + "."), false);
        exporter.exportOne(source.getServer(), config, ExportTrigger.MANUAL, colonyId)
                .whenComplete((status, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Colony Bridge export failed: " + errorMessage(error)));
                    } else if (status.coloniesDetected() == 0) {
                        source.sendFailure(Component.literal("Colony " + colonyId + " was not found."));
                    } else {
                        source.sendSuccess(() -> Component.literal("Colony Bridge exported colony " + colonyId + "."), false);
                    }
                }));
        return 1;
    }

    private static String errorMessage(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static String nullable(String value) {
        return value == null ? "unknown" : value;
    }

    private static String nullablePath(Path path) {
        return path == null ? "unknown" : path.toAbsolutePath().normalize().toString();
    }

    private static String instant(java.time.Instant instant) {
        return instant == null ? "never" : DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
