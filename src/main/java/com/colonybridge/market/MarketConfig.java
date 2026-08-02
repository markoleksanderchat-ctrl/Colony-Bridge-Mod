package com.colonybridge.market;

public record MarketConfig(int quoteDelaySeconds, int quoteValiditySeconds, int eventFrequencyMinutes,
                           double volatilityStrength, double minimumPriceMultiplier,
                           double maximumPriceMultiplier, boolean buyingEnabled,
                           boolean operatorEventControls, boolean sellingEnabled, double sellPriceRatio,
                           int dailySellDiamondLimit, int activeContractCount, int contractDurationMinutes,
                           double contractRewardPremium) {
    public static MarketConfig defaults() {
        return new MarketConfig(3, 120, 90, 0.16, 0.60, 1.80, true, true,
                true, 0.72, 64, 3, 360, 1.25);
    }

    public MarketConfig validated() {
        return new MarketConfig(clamp(quoteDelaySeconds, 0, 30), clamp(quoteValiditySeconds, 15, 3600),
                clamp(eventFrequencyMinutes, 5, 1440), PriceCalculator.clamp(0, 0.75, volatilityStrength),
                PriceCalculator.clamp(0.10, 1, minimumPriceMultiplier),
                PriceCalculator.clamp(1, 5, maximumPriceMultiplier), buyingEnabled, operatorEventControls,
                sellingEnabled, PriceCalculator.clamp(0.10, 0.95, sellPriceRatio),
                clamp(dailySellDiamondLimit, 1, 4096), clamp(activeContractCount, 1, 5),
                clamp(contractDurationMinutes, 30, 10080), PriceCalculator.clamp(1.0, 3.0, contractRewardPremium));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
