package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.LivestockData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class LivestockObservationCache {
    private final Map<Integer, LivestockData> lastComplete = new HashMap<>();

    void clear() { lastComplete.clear(); }

    Optional<LivestockData> get(int colonyId) {
        return Optional.ofNullable(lastComplete.get(colonyId));
    }

    void put(int colonyId, LivestockData value) {
        lastComplete.put(colonyId, value);
    }
}
