package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.*;

import java.util.List;
import java.util.Map;

public record CollectedSections(
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
        WorldData world
) {
}
