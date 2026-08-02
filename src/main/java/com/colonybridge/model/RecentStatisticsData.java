package com.colonybridge.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record RecentStatisticsData(
        Integer currentColonyDay,
        int windowDays,
        Map<String, Integer> today,
        Map<String, Integer> recentWindow
) {
    public RecentStatisticsData {
        today = immutableSortedMap(today);
        recentWindow = immutableSortedMap(recentWindow);
    }

    private static Map<String, Integer> immutableSortedMap(Map<String, Integer> values) {
        return values == null || values.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
