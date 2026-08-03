package com.colonybridge.command;

import com.colonybridge.export.ColonyBridgeExporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ColonyBridgeCommand {
    private ColonyBridgeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ColonyBridgeExporter exporter) {
        dispatcher.register(Commands.literal("colonybridge")
                .requires(ColonyBridgeCommand::canUseBridgeCommands)
                .then(StatusCommands.status(exporter))
                .then(StatusCommands.paths("paths", exporter))
                .then(StatusCommands.version(exporter))
                .then(ExportCommands.export(exporter))
                .then(MarketCommands.register()));
        dispatcher.register(Commands.literal("cb")
                .requires(ColonyBridgeCommand::canUseBridgeCommands)
                .executes(context -> ExportCommands.exportAll(context.getSource(), exporter))
                .then(StatusCommands.status(exporter))
                .then(StatusCommands.paths("paths", exporter))
                .then(StatusCommands.paths("path", exporter))
                .then(StatusCommands.version(exporter))
                .then(ExportCommands.export(exporter)));
    }

    static boolean canUseBridgeCommands(CommandSourceStack source) {
        return !source.getServer().isDedicatedServer() || source.hasPermission(2);
    }
}
