package com.colonybridge.model;

import java.util.Map;

public record ConstructionData(
        String buildingId,
        String projectType,
        Integer currentLevel,
        Integer targetLevel,
        Integer assignedBuilderCitizenId,
        String builderHutId,
        String projectState,
        Map<String, Integer> requiredResources,
        Map<String, Integer> deliveredResources,
        Map<String, Integer> missingResources,
        Double progress,
        Map<String, Object> details
) {
}
