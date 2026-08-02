package com.colonybridge.model;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;

public record ColonySnapshot(
        int schemaVersion,
        String bridgeVersion,
        String generatedAt,
        String trigger,
        String fingerprint,
        GameData game,
        WorldData world,
        ColonyData colony,
        SummaryData summary,
        List<CitizenData> citizens,
        List<BuildingData> buildings,
        List<RequestData> requests,
        List<ConstructionData> construction,
        EnvironmentData environment,
        TerritoryData territory,
        LivestockData livestock,
        ResearchData research,
        Map<String, Integer> statistics,
        RecentStatisticsData recentStatistics,
        DefenseStatisticsData defenseStatistics,
        FoodSupplyData foodSupply,
        StockLedgerData stockLedger,
        Map<String, CapabilityData> capabilities,
        List<BridgeMessage> warnings,
        List<BridgeMessage> errors
) {
    public ColonySnapshot {
        citizens = citizens == null ? List.of() : List.copyOf(citizens);
        buildings = buildings == null ? List.of() : List.copyOf(buildings);
        requests = requests == null ? List.of() : List.copyOf(requests);
        construction = construction == null ? List.of() : List.copyOf(construction);
        statistics = immutableSortedMap(statistics);
        capabilities = immutableSortedMap(capabilities);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    private static <T> Map<String, T> immutableSortedMap(Map<String, T> values) {
        return values == null || values.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public ColonySnapshot withFingerprint(String fingerprint) {
        return new ColonySnapshot(
                schemaVersion, bridgeVersion, generatedAt, trigger, fingerprint, game, world, colony, summary,
                citizens, buildings, requests, construction, environment, territory, livestock, research, statistics,
                recentStatistics, defenseStatistics, foodSupply, stockLedger,
                capabilities, warnings, errors
        );
    }
}
