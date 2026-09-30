package com.colonybridge.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarketReplayTests {
    @Test
    void fixedSeedClockAndInputsReplayIdentically() {
        MarketRecord initial = new MarketRecord("minecraft:iron_ingot", 0.25, 0.30, 0.0,
                1_700_000_000_000L, List.of(0.25));

        assertEquals(replay(initial), replay(initial));
    }

    @Test
    void longIdlePeriodCatchesUpOnceWithBoundedWork() {
        long start = 1_700_000_000_000L;
        long period = 30L * 60_000L;
        MarketRecord initial = new MarketRecord("minecraft:iron_ingot", 0.25, 0.30, 0.1,
                start, List.of(0.25));
        long now = start + 500 * period;
        MarketRecord caughtUp = MarketDynamics.advance(initial, 42, now, 0.5, 1.0, 0.6, 1.8);
        assertEquals(now, caughtUp.lastUpdateTime());
        assertEquals(caughtUp, MarketDynamics.advance(caughtUp, 42, now, 0.5, 1.0, 0.6, 1.8));

        MarketRecord eachPeriod = initial;
        for (int step = 1; step <= 500; step++) {
            eachPeriod = MarketDynamics.advance(eachPeriod, 42, start + step * period, 0.5, 1.0, 0.6, 1.8);
        }
        assertTrue(Math.abs(eachPeriod.currentTrend() - caughtUp.currentTrend()) < 0.000001);

        long decadeLater = start + 10L * 365 * 24 * 2 * period;
        MarketRecord distant = MarketDynamics.advance(initial, 42, decadeLater, 0.5, 1.0, 0.6, 1.8);
        assertEquals(decadeLater, distant.lastUpdateTime());
        assertTrue(Double.isFinite(distant.currentTrend()));
        assertTrue(distant.priceHistory().size() <= 16);
    }

    @Test
    void bulkBuyingCannotUndercutSellingSinglesAtHighestAllowedSellRatio() {
        ClassifiedItem item = VanillaClassificationDomain.classify(new ItemClassificationFacts(
                "minecraft:netherite_ingot", "Netherite Ingot", 1, 0, 64,
                false, false, false, false, false, false, false, false, false, 0));
        MarketConfig defaults = MarketConfig.defaults();
        MarketConfig config = new MarketConfig(defaults.quoteDelaySeconds(), defaults.quoteValiditySeconds(),
                defaults.eventFrequencyMinutes(), defaults.volatilityStrength(), defaults.minimumPriceMultiplier(),
                defaults.maximumPriceMultiplier(), true, true, true, 0.95,
                defaults.dailySellDiamondLimit(), defaults.activeContractCount(), defaults.contractDurationMinutes(),
                defaults.contractRewardPremium()).validated();
        MarketQuote bulkBuy = QuoteLifecycleService.create("buy", "player", item, item.input().itemId(),
                512, TradeDirection.BUY, 4.3, 4.3, null, List.of(), 0, config);
        MarketQuote singleSale = QuoteLifecycleService.create("sell", "player", item, item.input().itemId(),
                1, TradeDirection.SELL, 4.3, 4.3, null, List.of(), 0, config);
        assertTrue(bulkBuy.totalDiamondCost() >= 512 * singleSale.totalDiamondCost());
    }

    @Test
    void changedBaseValuationRetainsTrendButResetsStalePriceHistory() {
        MarketRecord old = new MarketRecord("minecraft:stone", 0.25, 0.2, 0.1,
                1234, List.of(0.25, 0.3));
        assertEquals(old, MarketDynamics.rebase(old, 0.25, 0.2));
        MarketRecord updated = MarketDynamics.rebase(old, 0.5, 0.3);
        assertEquals(0.5, updated.baseValue());
        assertEquals(0.3, updated.volatility());
        assertEquals(old.currentTrend(), updated.currentTrend());
        assertEquals(old.lastUpdateTime(), updated.lastUpdateTime());
        assertEquals(List.of(0.5), updated.priceHistory());
    }

    private static MarketRecord replay(MarketRecord initial) {
        MarketRecord current = initial;
        long seed = 42L;
        for (int step = 1; step <= 24; step++) {
            long now = initial.lastUpdateTime() + step * 30L * 60_000L;
            current = MarketDynamics.advance(current, seed, now, 1.0, step % 5 == 0 ? 1.045 : 1.0,
                    0.65, 1.65);
            if (step == 8) current = MarketDynamics.afterTrade(current, TradeDirection.BUY, 16, now);
            if (step == 16) current = MarketDynamics.afterTrade(current, TradeDirection.SELL, 8, now);
        }
        return current;
    }
}
