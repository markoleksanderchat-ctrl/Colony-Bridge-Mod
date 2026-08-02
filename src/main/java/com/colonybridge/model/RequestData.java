package com.colonybridge.model;

import java.util.List;
import java.util.Map;

public record RequestData(
        String id,
        String type,
        Integer requestingCitizenId,
        String requestingBuildingId,
        String requestedItemRegistryId,
        String requestedItemDisplayName,
        Integer requestedQuantity,
        Integer remainingQuantity,
        String state,
        String parentRequestId,
        List<String> childRequestIds,
        String resolver,
        Boolean complete,
        Boolean cancelled,
        Boolean assigned,
        Boolean inProgress,
        Boolean unresolved,
        Map<String, Object> details
) {
}
