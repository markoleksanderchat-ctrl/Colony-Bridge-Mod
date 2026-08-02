package com.colonybridge.market;

import java.util.List;

public record MarketEvent(String id, String title, String description, List<String> targets,
                          double priceModifier, long startTime, long durationMillis) {
    public double strengthAt(long now) {
        if (now < startTime || now >= startTime + durationMillis || durationMillis <= 0) return 0;
        return 1.0 - ((double) (now - startTime) / durationMillis);
    }

    public boolean affects(String itemId, java.util.Set<String> tags) {
        return targets.stream().anyMatch(target -> target.equals(itemId) || tags.contains(target));
    }
}
