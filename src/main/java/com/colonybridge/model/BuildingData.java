package com.colonybridge.model;

import java.util.List;
import java.util.Map;

public record BuildingData(
        String id,
        String registryId,
        String type,
        Integer level,
        Integer maxLevel,
        PositionData position,
        String dimension,
        List<Integer> assignedWorkerIds,
        List<Integer> workplaceWorkerIds,
        Integer workerCapacity,
        Boolean staffed,
        Boolean active,
        Boolean upgrading,
        Boolean beingBuilt,
        Boolean structurallyComplete,
        Integer targetLevel,
        Integer assignedBuilderCitizenId,
        List<String> openRequestIds,
        Map<String, Object> state
) {
}
