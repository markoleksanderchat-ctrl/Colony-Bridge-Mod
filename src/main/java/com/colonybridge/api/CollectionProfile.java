package com.colonybridge.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CollectionProfile(
        int colonies,
        int citizens,
        int buildings,
        int workOrders,
        int inventoryHandlers,
        int inventorySlots,
        int inventoryCacheHits,
        int inventoryCacheMisses,
        int livestockEntities,
        int livestockHuts,
        int statisticTypes,
        int statisticBuildings,
        Map<String, Long> stageMicros,
        long totalMicros
) {
    public static final CollectionProfile EMPTY = new CollectionProfile(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), 0);

    public CollectionProfile {
        if (colonies < 0 || citizens < 0 || buildings < 0 || workOrders < 0
                || inventoryHandlers < 0 || inventorySlots < 0 || inventoryCacheHits < 0
                || inventoryCacheMisses < 0 || livestockEntities < 0 || livestockHuts < 0
                || statisticTypes < 0 || statisticBuildings < 0 || totalMicros < 0) {
            throw new IllegalArgumentException("Collection profile values must not be negative.");
        }
        stageMicros = Collections.unmodifiableMap(new LinkedHashMap<>(stageMicros == null ? Map.of() : stageMicros));
        if (stageMicros.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("Collection stage timings must not be negative.");
        }
    }

    public static CollectionProfile aggregate(List<CollectionProfile> profiles, long totalMicros) {
        Map<String, Long> stages = new LinkedHashMap<>();
        int colonies = 0, citizens = 0, buildings = 0, workOrders = 0;
        int handlers = 0, slots = 0, hits = 0, misses = 0;
        int livestockEntities = 0, livestockHuts = 0, statisticTypes = 0, statisticBuildings = 0;
        for (CollectionProfile profile : profiles) {
            colonies += profile.colonies;
            citizens += profile.citizens;
            buildings += profile.buildings;
            workOrders += profile.workOrders;
            handlers += profile.inventoryHandlers;
            slots += profile.inventorySlots;
            hits += profile.inventoryCacheHits;
            misses += profile.inventoryCacheMisses;
            livestockEntities += profile.livestockEntities;
            livestockHuts += profile.livestockHuts;
            statisticTypes += profile.statisticTypes;
            statisticBuildings += profile.statisticBuildings;
            profile.stageMicros.forEach((stage, micros) -> stages.merge(stage, micros, Long::sum));
        }
        return new CollectionProfile(colonies, citizens, buildings, workOrders, handlers, slots, hits, misses,
                livestockEntities, livestockHuts, statisticTypes, statisticBuildings, stages, totalMicros);
    }
}
