package com.colonybridge;

import com.colonybridge.model.*;

import java.util.List;
import java.util.Map;

public final class SnapshotFixtures {
    private SnapshotFixtures() {
    }

    public static ColonySnapshot smallColony(String generatedAt, String trigger) {
        return new ColonySnapshot(
                ColonyBridgeConstants.SCHEMA_VERSION,
                ColonyBridgeConstants.VERSION,
                generatedAt,
                trigger,
                null,
                new GameData("1.21.1", "NeoForge", "21.1.238", "1.1.1319-1.21.1"),
                new WorldData("Create Adventures", "world", "minecraft:overworld", false),
                new ColonyData(1, "Oakwatch", null, "Oleksander1000", "minecraft:overworld",
                        new PositionData(52, 80, -1595), null, 2, 10, 7.5, true, false, false, false, "ACTIVE",
                        Map.of("nested", Map.of("count", 1, "ratio", 1.0))),
                new SummaryData(2, 10, 1, 1, 0, 0, 1, 1, 0, 1, 0),
                List.of(
                        new CitizenData(1, null, "Kyro H. Colthurst", "adult", "male", "Builder", "minecolonies:builder", false,
                                "52,80,-1595", "50,80,-1590", null, null, 7.5, 12.0, "working", true,
                                false, true, false, false, false, false, true, true, Map.of("strength", 3), null, null, Map.of()),
                        new CitizenData(2, null, "Nancy R. Chetwood", "adult", "female", null, null, false,
                                null, null, null, null, 6.0, 10.0, "idle", false,
                                false, true, false, false, false, true, false, false, Map.of(), null, null, Map.of())
                ),
                List.of(new BuildingData("52,80,-1595", "minecolonies:townhall", "Town Hall", 1, null,
                        new PositionData(52, 80, -1595), "minecraft:overworld", List.of(1), List.of(1), 1,
                        true, true, false, false, true, null, null, List.of("abc"), Map.of())),
                List.of(new RequestData("abc", "item", 1, "52,80,-1595", "minecraft:oak_log", "Oak Log",
                        32, 12, "ASSIGNED", null, List.of(), null, false, false, true, false, false, Map.of())),
                List.of(),
                new EnvironmentData("minecraft:plains", List.of("minecraft:plains"), 42L, 1000L, 100000L, 0, true, false, false),
                new TerritoryData(4, 1024, 0, 1, 0, 1, 2, 2, 4, 4),
                new LivestockData(3, 3, 0, Map.of("minecraft:cow", 2, "minecraft:sheep", 1),
                        List.of(new LivestockHutData("60,80,-1590", "minecolonies:cowboy", "Cowboy's Hut",
                                new PositionData(60, 80, -1590), List.of(1), 3,
                                Map.of("minecraft:cow", 2, "minecraft:sheep", 1)))),
                new ResearchData(List.of("minecolonies:civilian/stamina"), List.of(),
                        List.of(new ResearchEffectData("minecolonies:citizen_stamina", "research.effect.stamina", null, 1.0))),
                Map.of("citizens_born", 2),
                new RecentStatisticsData(42, 7, Map.of("logs_gathered", 4), Map.of("logs_gathered", 19)),
                new DefenseStatisticsData(
                        new DefenseKillBreakdownData(14, 13, 3, 9, 1, 1, false,
                                Map.of("entity.minecraft.zombie", 9, "entity.minecolonies.barbarian", 3,
                                        "entity.minecraft.cow", 1),
                                Map.of("entity.minecraft.zombie", "monster_or_hostile",
                                        "entity.minecolonies.barbarian", "minecolonies_raider",
                                        "entity.minecraft.cow", "neutral_or_peaceful")),
                        new DefenseKillBreakdownData(2, 2, 0, 2, 0, 0, true,
                                Map.of("entity.minecraft.zombie", 2),
                                Map.of("entity.minecraft.zombie", "monster_or_hostile")),
                        new DefenseKillBreakdownData(6, 6, 1, 5, 0, 0, true,
                                Map.of("entity.minecraft.zombie", 5, "entity.minecolonies.barbarian", 1),
                                Map.of("entity.minecraft.zombie", "monster_or_hostile",
                                        "entity.minecolonies.barbarian", "minecolonies_raider")),
                        42, 7, 8, 1, 3),
                new FoodSupplyData(84, 3, Map.of("minecraft:bread", 48, "minecraft:apple", 24, "minecraft:carrot", 12),
                        4, 70, 7, 10.0, 8.4, 51, "stable", "high",
                        1, 3, List.of("minecraft:apple", "minecraft:bread", "minecraft:carrot"), 1, 36, false),
                new StockLedgerData(42, 42, 44, 0, 2, 340, 4, 4, 0,
                        Map.of("minecraft:bread", 48, "minecraft:oak_log", 192, "minecraft:cobblestone", 96, "minecraft:iron_ingot", 4),
                        1, 2, 36, Map.of("minecolonies:warehouse", 2), Map.of("minecolonies:warehouse", 36), false, false),
                Map.of(
                        "colonies", CapabilityData.available(),
                        "foodSupply", CapabilityData.available(),
                        "stockLedger", CapabilityData.available(),
                        "warehouseInventoryTotals", CapabilityData.unsupported("The stock ledger covers known colony building storage, not a warehouse-only authoritative inventory")
                ),
                List.of(),
                List.of()
        );
    }
}
