package com.colonybridge.market;

import java.util.ArrayList;
import java.util.List;

public final class MarketDynamics {
    private static final long TREND_PERIOD_MILLIS = 30L * 60_000L;

    private MarketDynamics() {
    }

    public static MarketRecord rebase(MarketRecord source, double baseValue, double volatility) {
        if (source.baseValue() == baseValue && source.volatility() == volatility) return source;
        return new MarketRecord(source.itemId(), baseValue, volatility, source.currentTrend(),
                source.lastUpdateTime(), List.of(baseValue));
    }

    public static MarketRecord advance(MarketRecord source, long seed, long now, double strength,
                                       double eventModifier, double minimum, double maximum) {
        long elapsed = Math.max(0, now - source.lastUpdateTime());
        long periods = elapsed / TREND_PERIOD_MILLIS;
        long skipped = Math.max(0, periods - 96);
        double trend = source.currentTrend() * Math.pow(0.88, skipped);
        long basePeriod = Math.floorDiv(source.lastUpdateTime(), TREND_PERIOD_MILLIS);
        for (long step = skipped + 1; step <= periods; step++) {
            int mixed = MarketEventManager.mix(seed ^ source.itemId().hashCode() ^ (basePeriod + step));
            double impulse = ((mixed & 0xffff) / 32767.5) - 1;
            trend = trend * 0.88 + impulse * source.volatility() * strength * 0.12;
        }
        double multiplier = PriceCalculator.clamp(minimum, maximum, (1 + trend) * eventModifier);
        double price = Math.max(0.000001, source.baseValue() * multiplier);
        List<Double> history = new ArrayList<>(source.priceHistory());
        if (history.isEmpty() || periods > 0 || Math.abs(history.get(history.size() - 1) - price) > 0.000001) {
            history.add(price);
            while (history.size() > 16) history.remove(0);
        }
        return new MarketRecord(source.itemId(), source.baseValue(), source.volatility(), trend,
                periods > 0 ? source.lastUpdateTime() + periods * TREND_PERIOD_MILLIS : source.lastUpdateTime(), history);
    }

    public static MarketRecord afterTrade(MarketRecord source, TradeDirection direction, int quantity, long now) {
        double pressure = Math.min(0.12, Math.log1p(Math.max(1, quantity)) * source.volatility() * 0.01);
        double trend = PriceCalculator.clamp(-0.5, 0.5,
                source.currentTrend() + (direction == TradeDirection.BUY ? pressure : -pressure));
        List<Double> history = new ArrayList<>(source.priceHistory());
        history.add(Math.max(0.000001, source.baseValue() * (1 + trend)));
        while (history.size() > 16) history.remove(0);
        return new MarketRecord(source.itemId(), source.baseValue(), source.volatility(), trend, now, history);
    }
}
