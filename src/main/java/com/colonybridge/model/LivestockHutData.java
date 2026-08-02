package com.colonybridge.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record LivestockHutData(
        String buildingId,
        String buildingType,
        String name,
        PositionData position,
        List<Integer> workerIds,
        Integer total,
        Map<String, Integer> byType
) {
    public LivestockHutData {
        workerIds = workerIds == null ? List.of() : List.copyOf(workerIds);
        byType = byType == null || byType.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(byType));
    }
}
