package com.colonybridge.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record DefenseKillBreakdownData(
        Integer total,
        Integer detailedTotal,
        Integer raiders,
        Integer hostile,
        Integer peacefulOrOther,
        Integer unclassified,
        Boolean reconciled,
        Map<String, Integer> byEntity,
        Map<String, String> byEntityCategory
) {
    public DefenseKillBreakdownData {
        byEntity = byEntity == null || byEntity.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(byEntity));
        byEntityCategory = byEntityCategory == null || byEntityCategory.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(byEntityCategory));
    }
}
