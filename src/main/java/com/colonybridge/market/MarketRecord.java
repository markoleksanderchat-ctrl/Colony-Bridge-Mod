package com.colonybridge.market;

import java.util.List;

public record MarketRecord(String itemId, double baseValue, double volatility, double currentTrend,
                           long lastUpdateTime, List<Double> priceHistory) {
    public MarketRecord {
        priceHistory = priceHistory == null ? List.of() : List.copyOf(priceHistory);
    }
}
