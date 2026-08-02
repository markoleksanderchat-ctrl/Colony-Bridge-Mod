package com.colonybridge.market;

public record OnlineMarketConfig(boolean enabled, int refreshSeconds, int maximumAgeMinutes,
                                 double influenceStrength) {
    public static OnlineMarketConfig defaults() {
        return new OnlineMarketConfig(true, 60, 360, 1.0);
    }

    public OnlineMarketConfig validated() {
        return new OnlineMarketConfig(enabled, clamp(refreshSeconds, 30, 900),
                clamp(maximumAgeMinutes, 5, 1440),
                PriceCalculator.clamp(0, 3, influenceStrength));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
