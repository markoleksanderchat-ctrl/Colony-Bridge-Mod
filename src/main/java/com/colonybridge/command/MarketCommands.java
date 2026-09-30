package com.colonybridge.command;

import com.colonybridge.config.ColonyBridgeConfig;
import com.colonybridge.market.MarketEventManager;
import com.colonybridge.market.MarketManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.io.IOException;

final class MarketCommands {
    private MarketCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("market")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("events").executes(context -> events(context.getSource())))
                .then(Commands.literal("contracts").executes(context -> contracts(context.getSource())))
                .then(Commands.literal("reset").executes(context -> reset(context.getSource())))
                .then(Commands.literal("forceevent")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    MarketEventManager.TEMPLATES.forEach(template -> builder.suggest(template.id()));
                                    return builder.buildFuture();
                                })
                                .executes(context -> forceEvent(context.getSource(),
                                        StringArgumentType.getString(context, "event")))))
                .then(Commands.literal("quote")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 1024))
                                        .executes(context -> quote(context.getSource(),
                                                StringArgumentType.getString(context, "item"),
                                                IntegerArgumentType.getInteger(context, "quantity"))))));
    }

    private static int status(CommandSourceStack source) {
        MarketManager manager = MarketManager.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Royal Exchange: " + manager.recordCount() + " active goods, "
                + manager.quoteCount() + " saved quotes, " + manager.events().size() + " active events."), false);
        source.sendSuccess(() -> Component.literal("Online Exchange: " + manager.onlineMarketStatus() + "."), false);
        return 1;
    }

    private static int events(CommandSourceStack source) {
        MarketManager manager = MarketManager.get(source.getServer());
        var events = manager.events();
        if (events.isEmpty()) source.sendSuccess(() -> Component.literal("The Royal Exchange reports a quiet market."), false);
        events.forEach(event -> source.sendSuccess(() -> Component.literal(event.title() + ": " + event.description()), false));
        return events.size();
    }

    private static int contracts(CommandSourceStack source) {
        var contracts = MarketManager.get(source.getServer()).contracts();
        if (contracts.isEmpty()) source.sendSuccess(() -> Component.literal("The Crown has posted no active contracts."), false);
        contracts.forEach(contract -> source.sendSuccess(() -> Component.literal(contract.title() + ": deliver "
                + contract.quantity() + " " + contract.itemId() + " for " + contract.rewardDiamonds()
                + " diamonds."), false));
        return contracts.size();
    }

    private static int forceEvent(CommandSourceStack source, String eventId) {
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
        } catch (IOException failure) {
            source.sendFailure(Component.literal("Market event could not be saved; no event was started."));
            return 0;
        }
    }

    private static int reset(CommandSourceStack source) {
        if (!ColonyBridgeConfig.marketValues().operatorEventControls()) {
            source.sendFailure(Component.literal("Royal Exchange operator controls are disabled."));
            return 0;
        }
        try {
            MarketManager.get(source.getServer()).reset();
            source.sendSuccess(() -> Component.literal("Royal Exchange market reset with a new deterministic seed."), true);
            return 1;
        } catch (IOException failure) {
            source.sendFailure(Component.literal("Royal Exchange market could not be saved; the reset was cancelled."));
            return 0;
        }
    }

    private static int quote(CommandSourceStack source, String itemId, int quantity) {
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("A player must request a market quote."));
            return 0;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
        var item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
        if (item == Items.AIR || key == null || !com.colonybridge.market.MarketItemIds.isTradable(key.toString())) {
            source.sendFailure(Component.literal("Only vanilla goods other than diamond currency, blocks and ores may be quoted."));
            return 0;
        }
        var quote = MarketManager.get(source.getServer()).requestQuote(source.getPlayer(), item, quantity);
        source.sendSuccess(() -> Component.literal("Royal Exchange quote requested. " + quote.totalDiamondCost()
                + " diamonds when ready; valid for " + ((quote.expirationTime() - quote.readyTime()) / 1000)
                + " seconds."), false);
        return 1;
    }
}
