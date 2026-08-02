package com.colonybridge.market;

public final class QuoteManager {
    private QuoteManager() {
    }

    public static boolean canComplete(MarketQuote quote, String playerId, String itemId, int quantity, long now) {
        return quote != null && !quote.completed() && quote.ready(now) && !quote.expired(now)
                && quote.playerId().equals(playerId) && quote.itemId().equals(itemId) && quote.quantity() == quantity;
    }

    public static boolean canAfford(int availableDiamonds, int cost) {
        return cost > 0 && availableDiamonds >= cost;
    }
}
