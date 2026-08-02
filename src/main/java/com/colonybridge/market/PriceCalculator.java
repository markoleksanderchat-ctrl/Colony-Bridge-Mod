package com.colonybridge.market;

public final class PriceCalculator {
    public static final double DIAMOND_INTRINSIC_VALUE = 65.7;
    private static final int[] PRACTICAL_QUANTITIES = {1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 128, 256, 512, 1024};

    private PriceCalculator() {
    }

    public static TradeValueResult calculate(TradeValueInput input) {
        double intrinsic = intrinsic(input);
        double bulk = bulkModifier(input.quantity(), input.wholesaleSuitability());
        double price = Math.max(0, input.quantity()) * (intrinsic / DIAMOND_INTRINSIC_VALUE) * bulk;
        double confidence = clamp(0, 1, input.confidence());
        double uncertainty = 0.25 * (1 - confidence);
        return new TradeValueResult(intrinsic, price, bulk, price * (1 - uncertainty),
                price * (1 + uncertainty), classify(price / Math.max(1, input.quantity())));
    }

    public static double intrinsic(TradeValueInput input) {
        double r = clamp(0, 7, input.rarity());
        double l = clamp(0, 7, input.labor());
        double h = clamp(0, 7, input.hazard());
        double c = clamp(0, 7, input.craftingComplexity());
        double x = clamp(0, 4, input.dimensionalBurden());
        double g = clamp(0, 6, input.equipmentGate());
        double t = clamp(0, 6, input.travelBurden());
        double base = 2 + 1.35 * Math.pow(r, 1.8) + 0.90 * Math.pow(l, 1.65)
                + 1.10 * Math.pow(h, 1.55) + 0.75 * Math.pow(c, 1.45)
                + 1.25 * x + 0.65 * Math.pow(g, 1.40) + 0.45 * t;
        return base * positive(input.renewability()) * positive(input.automationResistance())
                * positive(input.utility()) * positive(input.replaceability());
    }

    public static double bulkModifier(int quantity, double wholesaleSuitability) {
        double q = clamp(1, 64, quantity);
        double modifier = 1 - 0.18 * clamp(0, 1, wholesaleSuitability) * (Math.log(q) / Math.log(64));
        return clamp(0.82, 1, modifier);
    }

    public static int practicalItemsPerDiamond(double unitDiamondValue) {
        if (!Double.isFinite(unitDiamondValue) || unitDiamondValue <= 0) return 1;
        if (unitDiamondValue >= 1) return 1;
        double target = 1 / unitDiamondValue;
        int nearest = PRACTICAL_QUANTITIES[0];
        for (int option : PRACTICAL_QUANTITIES) {
            if (Math.abs(option - target) < Math.abs(nearest - target)) nearest = option;
        }
        return nearest;
    }

    public static String classify(double value) {
        if (value < 0.002) return "Abundant bulk material";
        if (value < 0.01) return "Common commodity";
        if (value < 0.05) return "Standard material";
        if (value < 0.20) return "Skilled-trade good";
        if (value < 0.75) return "Valuable resource";
        if (value < 2) return "Luxury or strategic good";
        if (value < 8) return "Rare treasure";
        if (value < 32) return "Royal asset";
        if (value < 128) return "National treasure";
        return "Irreplaceable artifact";
    }

    public static double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    private static double positive(double value) {
        return Math.max(0.01, value);
    }
}
