package com.colonybridge.utility;

import java.util.Map;

public final class DefenseStatisticsCalculatorTests {
    private DefenseStatisticsCalculatorTests() {
    }

    public static void run() {
        reconcilesExactDetailWithoutDoubleCounting();
        preservesLegacyGapsAsUnclassified();
        prefersDetailedEvidenceWhenItExceedsTheSummary();
        omitsZeroValueEntityNoise();
    }

    private static void reconcilesExactDetailWithoutDoubleCounting() {
        var result = DefenseStatisticsCalculator.calculate(10,
                Map.of("entity.minecolonies.barbarian", 2, "entity.minecraft.zombie", 7, "entity.minecraft.cow", 1),
                Map.of(
                        "entity.minecolonies.barbarian", DefenseStatisticsCalculator.MINECOLONIES_RAIDER,
                        "entity.minecraft.zombie", DefenseStatisticsCalculator.MONSTER_OR_HOSTILE,
                        "entity.minecraft.cow", DefenseStatisticsCalculator.NEUTRAL_OR_PEACEFUL));
        requireEquals(10, result.total(), "authoritative total");
        requireEquals(10, result.detailedTotal(), "detailed total");
        requireEquals(2, result.raiders(), "raiders");
        requireEquals(7, result.hostile(), "hostile");
        requireEquals(1, result.peacefulOrOther(), "neutral or peaceful");
        requireEquals(0, result.unclassified(), "unclassified");
        require(result.reconciled(), "exact detail should reconcile");
    }

    private static void preservesLegacyGapsAsUnclassified() {
        var result = DefenseStatisticsCalculator.calculate(12,
                Map.of("entity.minecraft.zombie", 8),
                Map.of("entity.minecraft.zombie", DefenseStatisticsCalculator.MONSTER_OR_HOSTILE));
        requireEquals(12, result.total(), "legacy total");
        requireEquals(8, result.detailedTotal(), "legacy detailed total");
        requireEquals(4, result.unclassified(), "legacy gap");
        require(!result.reconciled(), "legacy gap must not claim exact reconciliation");
    }

    private static void prefersDetailedEvidenceWhenItExceedsTheSummary() {
        var result = DefenseStatisticsCalculator.calculate(4,
                Map.of("entity.minecraft.zombie", 5),
                Map.of("entity.minecraft.zombie", DefenseStatisticsCalculator.MONSTER_OR_HOSTILE));
        requireEquals(5, result.total(), "detail should prevent undercounting");
        requireEquals(5, result.hostile(), "detail category count");
        require(!result.reconciled(), "mismatched evidence must not reconcile");
    }

    private static void omitsZeroValueEntityNoise() {
        var result = DefenseStatisticsCalculator.calculate(0,
                Map.of("entity.minecraft.zombie", 0),
                Map.of("entity.minecraft.zombie", DefenseStatisticsCalculator.MONSTER_OR_HOSTILE));
        require(result.byEntity().isEmpty(), "zero entity rows should be omitted");
        require(result.byEntityCategory().isEmpty(), "zero entity categories should be omitted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
