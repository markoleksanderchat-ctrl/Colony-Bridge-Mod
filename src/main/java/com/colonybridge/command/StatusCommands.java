package com.colonybridge.command;

import com.colonybridge.ColonyBridgeConstants;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.config.ColonyBridgeConfig;
import com.colonybridge.export.ColonyBridgeExporter;
import com.colonybridge.export.ExportStatus;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

final class StatusCommands {
    private StatusCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> status(ColonyBridgeExporter exporter) {
        return Commands.literal("status").executes(context -> status(context.getSource(), exporter));
    }

    static LiteralArgumentBuilder<CommandSourceStack> paths(String literal, ColonyBridgeExporter exporter) {
        return Commands.literal(literal).executes(context -> paths(context.getSource(), exporter));
    }

    static LiteralArgumentBuilder<CommandSourceStack> version(ColonyBridgeExporter exporter) {
        return Commands.literal("version").executes(context -> version(context.getSource(), exporter));
    }

    private static int status(CommandSourceStack source, ColonyBridgeExporter exporter) {
        ExportStatus status = exporter.status();
        BridgeConfigValues config = ColonyBridgeConfig.values();
        source.sendSuccess(() -> Component.literal("Colony Bridge status"), false);
        source.sendSuccess(() -> Component.literal("Exports: " + exportSchedule(config)), false);
        source.sendSuccess(() -> Component.literal("Export running: " + exporter.exportInProgress()), false);
        source.sendSuccess(() -> Component.literal("Manual exports queued: " + exporter.queuedManualExports()), false);
        source.sendSuccess(() -> Component.literal("Automatic exports coalesced: " + exporter.coalescedAutomaticExports()), false);
        source.sendSuccess(() -> Component.literal("Remote active/queued: " + exporter.remotePublishInProgress()
                + "/" + exporter.remotePublishQueued()), false);
        source.sendSuccess(() -> Component.literal("Remote sync: " + remoteSyncStatus(config)), false);
        source.sendSuccess(() -> Component.literal("MineColonies detected: " + status.mineColoniesDetected()), false);
        source.sendSuccess(() -> Component.literal("MineColonies version: " + nullable(status.mineColoniesVersion())), false);
        source.sendSuccess(() -> Component.literal("Colonies detected: " + status.coloniesDetected()), false);
        source.sendSuccess(() -> Component.literal("Last export: " + instant(status.lastExportAt())), false);
        source.sendSuccess(() -> Component.literal("Last successful export: " + instant(status.lastSuccessfulExportAt())), false);
        source.sendSuccess(() -> Component.literal("Last duration ms: " + status.lastExportDurationMs()), false);
        source.sendSuccess(() -> Component.literal("Last local/remote outcome: " + status.lastLocalOutcome()
                + "/" + status.lastRemoteOutcome()), false);
        source.sendSuccess(() -> Component.literal("Last stage ms: collect=" + status.lastTimings().collectionMs()
                + " serialize=" + status.lastTimings().serializationMs()
                + " fingerprint=" + status.lastTimings().fingerprintMs()
                + " disk=" + status.lastTimings().diskWriteMs()
                + " retention=" + status.lastTimings().retentionMs()
                + " sanitize=" + status.lastTimings().sanitizationMs()
                + " remote=" + status.lastTimings().remotePublishMs()), false);
        source.sendSuccess(() -> Component.literal("Export directory: " + nullablePath(status.outputRoot())), false);
        source.sendSuccess(() -> Component.literal("Adapter: " + status.adapterName()), false);
        source.sendSuccess(() -> Component.literal("Last warnings/errors: " + status.lastWarningCount()
                + "/" + status.lastErrorCount()), false);
        return 1;
    }

    private static String exportSchedule(BridgeConfigValues config) {
        if (!config.enabled()) return "disabled";
        if (!config.periodicExportEnabled()) return "automatic interval disabled";
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
            status.latestFiles().forEach(path -> source.sendSuccess(() -> Component.literal("Latest: "
                    + path.toAbsolutePath().normalize()), false));
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
