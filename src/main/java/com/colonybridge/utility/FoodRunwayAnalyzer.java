package com.colonybridge.utility;

import com.colonybridge.model.FoodSupplyData;

import java.util.List;
import java.util.Map;

public final class FoodRunwayAnalyzer {
    private FoodRunwayAnalyzer() {
    }

    public static FoodSupplyData analyze(
            int storedServings,
            int distinctFoodTypes,
            Map<String, Integer> servingsByItem,
            int mealsServedToday,
            int mealsServedSample,
            int sampleDays,
            Integer currentColonyDay,
            int citizenCount,
            int diningHallsScanned,
            int menuApprovedFoodTypes,
            List<String> approvedMenuItems,
            int scannedBuildings,
            int scannedSlots,
            boolean truncated
    ) {
        Double averageMealsPerDay = sampleDays > 0 && mealsServedSample > 0
                ? round((double) mealsServedSample / sampleDays)
                : null;
        boolean menuReady = diningHallsScanned > 0 && menuApprovedFoodTypes > 0;
        Double estimatedDaysRemaining = truncated || !menuReady || averageMealsPerDay == null || averageMealsPerDay <= 0
                ? null
                : round(storedServings / averageMealsPerDay);
        Integer estimatedRunoutColonyDay = estimatedDaysRemaining == null || currentColonyDay == null
                ? null
                : currentColonyDay + (int) Math.ceil(estimatedDaysRemaining);

        String status;
        if (diningHallsScanned == 0) {
            status = "no_dining_hall";
        } else if (menuApprovedFoodTypes == 0) {
            status = "menu_empty";
        } else if (truncated) {
            status = "incomplete";
        } else if (averageMealsPerDay == null) {
            status = "learning";
        } else if (estimatedDaysRemaining < 1) {
            status = "critical";
        } else if (estimatedDaysRemaining < 3) {
            status = "low";
        } else if (estimatedDaysRemaining < 7) {
            status = "watch";
        } else {
            status = "stable";
        }

        String confidence;
        if (truncated || sampleDays < 2 || mealsServedSample < Math.max(5, citizenCount)) {
            confidence = "low";
        } else if (sampleDays < 5 || mealsServedSample < Math.max(15, citizenCount * 2)) {
            confidence = "medium";
        } else {
            confidence = "high";
        }

        return new FoodSupplyData(
                storedServings,
                distinctFoodTypes,
                servingsByItem,
                mealsServedToday,
                mealsServedSample,
                sampleDays,
                averageMealsPerDay,
                estimatedDaysRemaining,
                estimatedRunoutColonyDay,
                status,
                confidence,
                diningHallsScanned,
                menuApprovedFoodTypes,
                approvedMenuItems,
                scannedBuildings,
                scannedSlots,
                truncated
        );
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
