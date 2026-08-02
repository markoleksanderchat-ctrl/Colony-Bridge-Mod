package com.colonybridge.model;

import java.util.Map;
import java.util.UUID;

public record CitizenData(
        Integer id,
        UUID uuid,
        String name,
        String ageCategory,
        String sex,
        String currentJob,
        String jobRegistryId,
        Boolean guard,
        String workplaceBuildingId,
        String homeBuildingId,
        Double health,
        Double maxHealth,
        Double happiness,
        Double saturation,
        String activity,
        Boolean employed,
        Boolean child,
        Boolean alive,
        Boolean sick,
        Boolean injured,
        Boolean sleeping,
        Boolean idle,
        Boolean working,
        Boolean requestingItem,
        Map<String, Integer> skills,
        PositionData currentPosition,
        PositionData lastKnownPosition,
        Map<String, Object> details
) {
}
