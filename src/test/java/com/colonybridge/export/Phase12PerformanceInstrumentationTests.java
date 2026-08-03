package com.colonybridge.export;

import com.colonybridge.api.CollectionProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Phase12PerformanceInstrumentationTests {
    @Test
    void collectionProfilesAggregateCountsAndNamedStages() {
        CollectionProfile first = new CollectionProfile(1, 20, 10, 4, 3, 50, 1, 0,
                8, 2, 7, 10, Map.of("capture", 100L, "inventory", 400L), 800);
        CollectionProfile second = new CollectionProfile(1, 12, 6, 2, 4, 70, 0, 1,
                5, 1, 9, 6, Map.of("capture", 80L, "inventory", 600L), 900);

        CollectionProfile aggregate = CollectionProfile.aggregate(List.of(first, second), 2_000);

        assertEquals(2, aggregate.colonies());
        assertEquals(32, aggregate.citizens());
        assertEquals(7, aggregate.inventoryHandlers());
        assertEquals(1, aggregate.inventoryCacheHits());
        assertEquals(1_000L, aggregate.stageMicros().get("inventory"));
        assertEquals(2_000, aggregate.totalMicros());
        assertThrows(UnsupportedOperationException.class, () -> aggregate.stageMicros().put("new", 1L));
    }

    @Test
    void mainThreadSummaryIsBoundedAndUsesNearestRankPercentiles() {
        BoundedDurationSamples samples = new BoundedDurationSamples(4);
        for (long value : List.of(2L, 8L, 4L, 20L, 6L)) samples.add(value);

        assertEquals(new MainThreadTimingSummary(4, 6, 20, 20), samples.summary());
        assertThrows(IllegalArgumentException.class, () -> samples.add(-1));
    }
}
