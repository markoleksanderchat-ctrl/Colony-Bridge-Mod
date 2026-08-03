package com.colonybridge.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MarketReplayTests {
    @Test
    void fixedSeedClockAndInputsReplayIdentically() {
        MarketRecord initial = new MarketRecord("minecraft:iron_ingot", 0.25, 0.30, 0.0,
                1_700_000_000_000L, List.of(0.25));

        assertEquals(replay(initial), replay(initial));
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
