package com.colonybridge.market;

import java.io.IOException;
import java.util.*;

final class MarketStateValidation {
    private MarketStateValidation() { }
    static MarketState validate(MarketState state) throws IOException {
        try {
            Map<String, MarketRecord> records = copy(state.records());
            Map<String, MarketQuote> quotes = copy(state.quotes());
            Map<String, MarketContract> contracts = copy(state.contracts());
            Map<String, DailySellVolume> volumes = copy(state.dailySellVolumes());
            List<MarketEvent> events = state.activeEvents() == null ? List.of() : List.copyOf(state.activeEvents());
            require(records.size() <= 10000 && quotes.size() <= 100000 && contracts.size() <= 1000 && events.size() <= 1000);
            for (var entry : records.entrySet()) {
                MarketRecord value = entry.getValue();
                require(MarketItemIds.isVanilla(entry.getKey()) && entry.getKey().equals(value.itemId()));
                require(positive(value.baseValue()) && Double.isFinite(value.volatility()) && value.volatility() >= 0
                        && value.volatility() <= 1 && Double.isFinite(value.currentTrend()) && Math.abs(value.currentTrend()) <= 1);
                require(!value.priceHistory().isEmpty() && value.priceHistory().size() <= 16);
                for (Double price : value.priceHistory()) require(price != null && positive(price));
            }
            for (var entry : quotes.entrySet()) {
                MarketQuote value = entry.getValue();
                require(entry.getKey().equals(value.id()) && text(value.id()) && text(value.playerId())
                        && MarketItemIds.isVanilla(value.itemId()) && value.direction() != null);
                require(value.quantity() > 0 && value.quantity() <= 1024 && value.totalDiamondCost() > 0
                        && positive(value.basePrice()) && positive(value.currentPrice())
                        && value.expirationTime() > value.readyTime() && value.readyTime() >= value.creationTime());
            }
            for (var entry : contracts.entrySet()) {
                MarketContract value = entry.getValue();
                require(entry.getKey().equals(value.id()) && text(value.id()) && MarketItemIds.isVanilla(value.itemId())
                        && value.quantity() > 0 && value.quantity() <= 1024 && value.rewardDiamonds() > 0
                        && value.expirationTime() > value.creationTime() && text(value.title()) && text(value.description()));
            }
            for (var event : events) require(text(event.id()) && text(event.title()) && text(event.description())
                    && event.targets() != null && event.targets().stream().allMatch(MarketStateValidation::text)
                    && Double.isFinite(event.priceModifier()) && Math.abs(event.priceModifier()) <= 1
                    && event.durationMillis() > 0);
            for (var entry : volumes.entrySet()) require(text(entry.getKey()) && entry.getValue().diamondsPaid() >= 0);
            Set<String> completedQuotes = ids(state.completedQuoteIds());
            Set<String> completedContracts = ids(state.completedContractIds());
            return new MarketState(state.version(), state.seed(), records, events, quotes, completedQuotes,
                    state.configuration() == null ? MarketConfig.defaults() : state.configuration().validated(),
                    contracts, completedContracts, volumes);
        } catch (RuntimeException malformed) {
            throw new IOException("Market data contains invalid fields.", malformed);
        }
    }
    private static <T> Map<String, T> copy(Map<String, T> value) { return value == null ? Map.of() : Map.copyOf(value); }
    private static Set<String> ids(Set<String> values) {
        if (values == null) return Set.of();
        require(values.size() <= 100000 && values.stream().allMatch(MarketStateValidation::text));
        return Set.copyOf(values);
    }
    private static boolean text(String value) { return value != null && !value.isBlank() && value.length() <= 4096; }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException("Invalid market field"); }
}
