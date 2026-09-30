package com.colonybridge.market;

import java.util.List;

public final class QuoteLifecycleService {
    private QuoteLifecycleService() {
    }

    public static void retain(java.util.Map<String, MarketQuote> quotes, MarketQuote next, long now) {
        quotes.values().removeIf(quote -> quote.expired(now) || quote.completed() || quote.playerId().equals(next.playerId()));
        if (quotes.size() >= 1024) {
            quotes.values().stream().min(java.util.Comparator.comparingLong(MarketQuote::creationTime).thenComparing(MarketQuote::id))
                    .ifPresent(oldest -> quotes.remove(oldest.id()));
        }
        quotes.put(next.id(), next);
    }

    public static MarketQuote create(String quoteId, String playerId, ClassifiedItem classified,
                                     String itemId, int requestedQuantity, TradeDirection direction,
                                     double baseUnit, double currentUnit, OnlineMarketInfluence onlineInfluence,
                                     List<MarketEvent> activeEvents, long now, MarketConfig config) {
        if (!MarketItemIds.isTradable(itemId)) throw new IllegalArgumentException("Currency cannot be traded as goods.");
        int quantity = Math.max(1, Math.min(1024, requestedQuantity));
        double ratio = direction == TradeDirection.SELL ? config.sellPriceRatio() : 1.0;
        double tradeUnit = currentUnit * ratio;
        if (tradeUnit < 1) quantity = Math.max(quantity, PriceCalculator.practicalItemsPerDiamond(tradeUnit));
        double bulk = PriceCalculator.bulkModifier(quantity, classified.input().wholesaleSuitability());
        if (direction == TradeDirection.BUY) bulk = Math.max(bulk, config.sellPriceRatio());
        double baseTotal = baseUnit * ratio * quantity * bulk;
        double currentTotal = tradeUnit * quantity * bulk;
        int diamonds = Math.max(1, direction == TradeDirection.SELL
                ? (int) Math.floor(currentTotal) : (int) Math.round(currentTotal));
        String eventText = activeEvents.stream()
                .filter(event -> event.affects(itemId, classified.tags()) && event.strengthAt(now) > 0)
                .map(MarketEvent::title).findFirst().map(title -> " Active influence: " + title + ".").orElse("");
        String explanation = "Primary increase: " + classified.strongestIncrease() + "; primary restraint: "
                + classified.strongestDecrease() + "." + eventText;
        if (onlineInfluence != null) explanation += " Online Exchange: " + onlineInfluence.summary() + ".";
        long ready = now + config.quoteDelaySeconds() * 1000L;
        double validityFactor = 1.25 - classified.volatility() * 0.5;
        long expires = ready + Math.max(15_000L,
                Math.round(config.quoteValiditySeconds() * 1000L * validityFactor));
        return new MarketQuote(quoteId, playerId, itemId, quantity, direction, baseTotal, currentTotal, diamonds,
                MarketDomainOperations.condition(currentUnit, baseUnit), explanation, now, ready, expires, false);
    }
}
