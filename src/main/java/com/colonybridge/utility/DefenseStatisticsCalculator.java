package com.colonybridge.utility;

import com.colonybridge.model.DefenseKillBreakdownData;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class DefenseStatisticsCalculator {
    public static final String MINECOLONIES_RAIDER = "minecolonies_raider";
    public static final String MONSTER_OR_HOSTILE = "monster_or_hostile";
    public static final String NEUTRAL_OR_PEACEFUL = "neutral_or_peaceful";
    public static final String UNCLASSIFIED = "unclassified";

    private DefenseStatisticsCalculator() {
    }

    public static DefenseKillBreakdownData calculate(
            Integer authoritativeTotal,
            Map<String, Integer> rawByEntity,
            Map<String, String> categoryByEntity) {
        Map<String, Integer> byEntity = new TreeMap<>();
        Map<String, String> exportedCategories = new TreeMap<>();
        int raiders = 0;
        int hostile = 0;
        int neutralOrPeaceful = 0;
        int unknownDetails = 0;
        int detailedTotal = 0;

        for (Map.Entry<String, Integer> entry : safeMap(rawByEntity).entrySet()) {
            int count = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            if (count == 0) continue;
            String entityKey = entry.getKey();
            String category = safeMap(categoryByEntity).getOrDefault(entityKey, UNCLASSIFIED);
            byEntity.put(entityKey, count);
            exportedCategories.put(entityKey, category);
            detailedTotal += count;
            switch (category) {
                case MINECOLONIES_RAIDER -> raiders += count;
                case MONSTER_OR_HOSTILE -> hostile += count;
                case NEUTRAL_OR_PEACEFUL -> neutralOrPeaceful += count;
                default -> unknownDetails += count;
            }
        }

        int recordedTotal = Math.max(0, authoritativeTotal == null ? 0 : authoritativeTotal);
        int total = Math.max(recordedTotal, detailedTotal);
        int unclassified = unknownDetails + Math.max(0, total - detailedTotal);
        boolean reconciled = authoritativeTotal != null && recordedTotal == detailedTotal;
        return new DefenseKillBreakdownData(
                total,
                detailedTotal,
                raiders,
                hostile,
                neutralOrPeaceful,
                unclassified,
                reconciled,
                byEntity,
                exportedCategories);
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> values) {
        return values == null ? Collections.emptyMap() : values;
    }
}
