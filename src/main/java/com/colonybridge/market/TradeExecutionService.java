package com.colonybridge.market;

public final class TradeExecutionService {
    private TradeExecutionService() {
    }

    public static String validate(PlayerInventoryPort inventory, MarketQuote quote, boolean alreadyCompleted,
                                  String itemId, int quantity, long now, MarketConfig config,
                                  boolean itemAvailable, int remainingSellAllowance) {
        if (inventory.restrictedGameMode()) return "Royal Exchange trades require Survival or Adventure mode.";
        if (alreadyCompleted || quote != null && quote.completed()) return "That quote has already been completed.";
        if (!QuoteManager.canComplete(quote, inventory.playerId(), itemId, quantity, now)) {
            return quote != null && quote.expired(now) ? "That quote has expired." : "That quote is no longer valid.";
        }
        if (!MarketItemIds.isTradable(itemId)) return "Currency cannot be traded as goods.";
        if (!itemAvailable) return "That item is not available.";
        if (quote.direction() == TradeDirection.BUY) {
            if (!config.buyingEnabled()) return "Royal Exchange buying is disabled.";
            if (!QuoteManager.canAfford(inventory.countDiamonds(), quote.totalDiamondCost())) {
                return "You do not have enough diamonds.";
            }
        } else {
            if (!config.sellingEnabled()) return "Royal Exchange selling is disabled.";
            if (quote.totalDiamondCost() > remainingSellAllowance) {
                return "Your daily Exchange limit has " + remainingSellAllowance + " diamonds remaining.";
            }
            if (inventory.countPlainItems(itemId) < quantity) return "You do not have enough unmodified items.";
        }
        return null;
    }

    public static InventoryMutation mutation(MarketQuote quote) {
        if (!MarketItemIds.isTradable(quote.itemId())) throw new IllegalArgumentException("Currency cannot be traded as goods.");
        return quote.direction() == TradeDirection.BUY
                ? new InventoryMutation("minecraft:diamond", quote.totalDiamondCost(), quote.itemId(), quote.quantity())
                : new InventoryMutation(quote.itemId(), quote.quantity(), "minecraft:diamond", quote.totalDiamondCost());
    }
}
