package com.colonybridge.model;

import java.util.Map;
import java.util.UUID;

public record ColonyData(
        Integer id,
        String name,
        UUID ownerUuid,
        String ownerName,
        String dimension,
        PositionData center,
        Integer claimedChunkCount,
        Integer citizenCount,
        Integer maxCitizenCapacity,
        Double overallHappiness,
        Boolean active,
        Boolean underAttack,
        Boolean raided,
        Boolean abandoned,
        String state,
        Map<String, Object> flags
) {
}
