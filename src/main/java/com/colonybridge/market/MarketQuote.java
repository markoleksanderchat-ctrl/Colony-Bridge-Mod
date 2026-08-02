package com.colonybridge.market;

public record MarketQuote(String id, String playerId, String itemId, int quantity, TradeDirection direction, double basePrice,
                          double currentPrice, int totalDiamondCost, String marketCondition,
                          String explanation, long creationTime, long readyTime, long expirationTime,
                          boolean completed) {
    public boolean ready(long now) { return now >= readyTime; }
    public boolean expired(long now) { return now >= expirationTime; }
    public MarketQuote completedCopy() {
        return new MarketQuote(id, playerId, itemId, quantity, direction, basePrice, currentPrice, totalDiamondCost,
                marketCondition, explanation, creationTime, readyTime, expirationTime, true);
    }
}
