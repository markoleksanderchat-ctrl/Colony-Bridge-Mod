package com.colonybridge.market;

public record TradeValueResult(double intrinsicValue, double rawDiamondValue, double bulkModifier,
                               double lowerEstimate, double upperEstimate, String treasuryClass) {
}
