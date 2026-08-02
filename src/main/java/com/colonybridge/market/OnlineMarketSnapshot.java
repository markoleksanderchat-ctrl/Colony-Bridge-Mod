package com.colonybridge.market;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record OnlineMarketSnapshot(long asOfEpochMillis, Map<String, Double> changes, List<OnlineMarketEvent> events) {
    public OnlineMarketSnapshot {
        changes = Map.copyOf(changes);
        events = events == null ? List.of() : List.copyOf(events);
    }

    public boolean usable(long now, OnlineMarketConfig config) {
        long maximumAge = config.maximumAgeMinutes() * 60_000L;
        return asOfEpochMillis <= now + 5 * 60_000L && now - asOfEpochMillis <= maximumAge;
    }

    public Optional<OnlineMarketInfluence> influence(String itemId, Set<String> tags,
                                                      long now, OnlineMarketConfig config) {
        if (!config.enabled() || !usable(now, config)) return Optional.empty();
        String ticker = OnlineIssuerMapper.issuerFor(itemId, tags);
        Double change = changes.get(ticker);
        if (change == null || !Double.isFinite(change)) return Optional.empty();
        double multiplier = PriceCalculator.clamp(0.75, 1.25,
                1 + change / 100.0 * config.influenceStrength());
        return Optional.of(new OnlineMarketInfluence(ticker, change, multiplier));
    }
}
