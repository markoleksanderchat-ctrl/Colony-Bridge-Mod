package com.colonybridge.command;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.config.ColonyBridgeConfig;
import com.colonybridge.export.ColonyBridgeExporter;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletionException;

final class ExportCommands {
    private ExportCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> export(ColonyBridgeExporter exporter) {
        return Commands.literal("export")
                .executes(context -> exportAll(context.getSource(), exporter))
                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                        .executes(context -> exportOne(context.getSource(), exporter,
                                IntegerArgumentType.getInteger(context, "colonyId"))));
    }

    static int exportAll(CommandSourceStack source, ColonyBridgeExporter exporter) {
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
                        source.sendSuccess(() -> Component.literal("Colony Bridge exported "
                                + status.coloniesDetected() + " colonies."), false);
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
                && cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
