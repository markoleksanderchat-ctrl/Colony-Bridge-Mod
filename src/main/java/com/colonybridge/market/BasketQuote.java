package com.colonybridge.market;

import java.util.List;

public record BasketQuote(String id, String playerId, List<MarketQuote> lines, int totalDiamonds,
                          long readyTime, long expirationTime) {
    public BasketQuote {
        lines = List.copyOf(lines);
        if (lines.isEmpty() || lines.size() > 9 || totalDiamonds < 1) {
            throw new IllegalArgumentException("A basket quote needs 1–9 sale lines and a positive payout.");
        }
    }

    public boolean ready(long now) { return now >= readyTime; }
    public boolean expired(long now) { return now >= expirationTime; }
}
