package com.colonybridge.model;

import java.util.List;

public record EnvironmentData(
        String centerBiome,
        List<String> sampledBiomes,
        Long worldDay,
        Long dayTimeTicks,
        Long gameTimeTicks,
        Integer moonPhase,
        Boolean daylight,
        Boolean raining,
        Boolean thundering
) {
}
