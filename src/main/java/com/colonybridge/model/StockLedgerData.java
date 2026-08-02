package com.colonybridge.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record StockLedgerData(
        Integer currentColonyDay,
        Integer refreshedColonyDay,
        Integer nextRefreshColonyDay,
        Integer cacheAgeDays,
        int refreshIntervalDays,
        Integer totalItems,
        int distinctItemTypes,
        int exportedItemTypes,
        int omittedItemTypes,
        Map<String, Integer> itemsById,
        int scannedBuildings,
        int scannedHandlers,
        int scannedSlots,
        Map<String, Integer> handlersByBuildingType,
        Map<String, Integer> slotsByBuildingType,
        boolean startupScan,
        boolean truncated
) {
    public StockLedgerData {
        itemsById = itemsById == null || itemsById.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(itemsById));
        handlersByBuildingType = handlersByBuildingType == null || handlersByBuildingType.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(handlersByBuildingType));
        slotsByBuildingType = slotsByBuildingType == null || slotsByBuildingType.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(slotsByBuildingType));
    }
}
