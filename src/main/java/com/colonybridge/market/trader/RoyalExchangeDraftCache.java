package com.colonybridge.market.trader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

final class RoyalExchangeDraftCache<K, V> {
    static final long RETENTION_MILLIS = 30_000;
    private final Map<K, Entry<V>> entries = new HashMap<>();
    private final LongSupplier clock;
    private final int maximumEntries;

    RoyalExchangeDraftCache(LongSupplier clock, int maximumEntries) {
        this.clock = clock;
        this.maximumEntries = maximumEntries;
    }

    synchronized void put(K key, V value) {
        prune();
        if (entries.size() >= maximumEntries && !entries.containsKey(key)) {
            K oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (var entry : entries.entrySet()) {
                if (entry.getValue().savedAt() < oldestTime) {
                    oldest = entry.getKey();
                    oldestTime = entry.getValue().savedAt();
                }
            }
            if (oldest != null) entries.remove(oldest);
        }
        entries.put(key, new Entry<>(value, clock.getAsLong()));
    }

    synchronized Optional<V> get(K key) {
        prune();
        Entry<V> entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    private void prune() {
        long now = clock.getAsLong();
        entries.values().removeIf(entry -> now - entry.savedAt() >= RETENTION_MILLIS);
    }

    private record Entry<V>(V value, long savedAt) {
    }
}
