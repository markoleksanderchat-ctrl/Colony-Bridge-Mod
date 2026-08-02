package com.colonybridge.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record LivestockData(
        Integer total,
        Integer housed,
        Integer withoutHome,
        Map<String, Integer> byType,
        List<LivestockHutData> huts
) {
    public LivestockData {
        byType = byType == null || byType.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(byType));
        huts = huts == null ? List.of() : List.copyOf(huts);
    }
}
