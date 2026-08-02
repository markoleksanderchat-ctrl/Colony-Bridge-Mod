package com.colonybridge.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record FoodSupplyData(
        Integer storedServings,
        int distinctFoodTypes,
        Map<String, Integer> servingsByItem,
        Integer mealsServedToday,
        Integer mealsServedSample,
        int sampleDays,
        Double averageMealsPerDay,
        Double estimatedDaysRemaining,
        Integer estimatedRunoutColonyDay,
        String status,
        String confidence,
        int diningHallsScanned,
        int menuApprovedFoodTypes,
        List<String> approvedMenuItems,
        int scannedBuildings,
        int scannedSlots,
        boolean truncated
) {
    public FoodSupplyData {
        servingsByItem = servingsByItem == null || servingsByItem.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(servingsByItem));
        approvedMenuItems = approvedMenuItems == null ? List.of() : List.copyOf(approvedMenuItems);
    }
}
