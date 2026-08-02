package com.colonybridge.utility;

import com.colonybridge.model.BuildingData;
import com.colonybridge.model.CitizenData;
import com.colonybridge.model.SummaryData;

import java.util.List;
import java.util.Map;

public final class SnapshotSummaryCalculatorTests {
    private SnapshotSummaryCalculatorTests() {
    }

    public static void run() {
        countsAuthoritativeGuardFlags();
        countsActualWorkplacesInsteadOfAssociatedCitizens();
    }

    private static void countsAuthoritativeGuardFlags() {
        CitizenData knight = citizen(19, "minecolonies:knight", true, "guard-tower");
        CitizenData builder = citizen(1, "minecolonies:builder", false, "builder-hut");
        SummaryData summary = SnapshotSummaryCalculator.summarize(10, List.of(knight, builder), List.of(), List.of(), List.of());

        requireEquals(1, summary.guards(), "MineColonies guard flag should count Knight jobs");
        requireEquals(2, summary.employedCitizens(), "guard counting must not alter employment");
    }

    private static void countsActualWorkplacesInsteadOfAssociatedCitizens() {
        BuildingData residence = building("residence", List.of(1, 2), List.of(), false);
        BuildingData warehouse = building("warehouse", List.of(2), List.of(), false);
        BuildingData guardTower = building("guard-tower", List.of(19), List.of(19), true);
        SummaryData summary = SnapshotSummaryCalculator.summarize(10, List.of(),
                List.of(residence, warehouse, guardTower), List.of(), List.of());

        requireEquals(1, summary.staffedBuildings(), "only buildings with actual workplace workers should be staffed");
        requireEquals(2, summary.unstaffedBuildings(), "housing occupants and linked couriers are not staff");
    }

    private static CitizenData citizen(int id, String jobId, boolean guard, String workplaceId) {
        return new CitizenData(id, null, "Citizen " + id, "adult", "unknown", jobId, jobId, guard,
                workplaceId, null, null, null, 10.0, 10.0, "working", true, false, true,
                false, false, false, false, true, false, Map.of(), null, null, Map.of());
    }

    private static BuildingData building(String id, List<Integer> associated, List<Integer> workers, boolean staffed) {
        return new BuildingData(id, "minecolonies:" + id, id, 1, null, null, "minecraft:overworld",
                associated, workers, null, staffed, true, false, false, true, null, null, List.of(), Map.of());
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }
}
