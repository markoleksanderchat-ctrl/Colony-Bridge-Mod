package com.colonybridge.market;

public final class MarketDomainOperations {
    private MarketDomainOperations() {
    }

    public static int remainingSellAllowance(DailySellVolume volume, long epochDay, int limit) {
        int used = volume != null && volume.epochDay() == epochDay ? volume.diamondsPaid() : 0;
        return Math.max(0, limit - used);
    }

    public static DailySellVolume recordSale(DailySellVolume previous, long epochDay, int diamonds) {
        int used = previous != null && previous.epochDay() == epochDay ? previous.diamondsPaid() : 0;
        return new DailySellVolume(epochDay, used + diamonds);
    }

    public static String condition(double currentUnit, double baseUnit) {
        return currentUnit > baseUnit * 1.08 ? "Demand is elevated"
                : currentUnit < baseUnit * 0.92 ? "Supply is favorable" : "Market is steady";
    }
}
