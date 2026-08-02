package com.colonybridge.market;

public record TradeValueInput(
        String itemId, String displayName, int quantity,
        double rarity, double labor, double hazard, double craftingComplexity,
        double dimensionalBurden, double equipmentGate, double travelBurden,
        double renewability, double automationResistance, double utility,
        double replaceability, double wholesaleSuitability, double confidence) {
    public TradeValueInput withQuantity(int value) {
        return new TradeValueInput(itemId, displayName, value, rarity, labor, hazard, craftingComplexity,
                dimensionalBurden, equipmentGate, travelBurden, renewability, automationResistance,
                utility, replaceability, wholesaleSuitability, confidence);
    }
}
