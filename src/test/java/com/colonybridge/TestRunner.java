package com.colonybridge;

import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.export.SnapshotStore;
import com.colonybridge.export.SnapshotWriteResult;
import com.colonybridge.utility.FilenameSanitizer;
import com.colonybridge.utility.JsonSupport;
import com.colonybridge.utility.SnapshotFingerprinter;
import com.colonybridge.utility.DayCounterState;
import com.colonybridge.utility.DayCelebrationMessages;
import com.colonybridge.utility.FoodRunwayAnalyzer;
import com.colonybridge.utility.BuilderHutProgressCalculator;
import com.colonybridge.utility.StockRefreshPolicy;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TestRunner {
    private TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        sanitizesUnsafeNames();
        validatesMinimums();
        ignoresTimestampAndTrigger();
        ignoresVolatileObservations();
        serializesNullsAndSchemaVersion();
        writesLatestAndAvoidsDuplicateHistory();
        appliesRetentionToDuplicateSnapshots();
        repairsMalformedLatestSnapshot();
        estimatesTerritoryFromTicketedChunks();
        announcesOnlyNewDays();
        rotatesDayCelebrations();
        estimatesFoodRunway();
        matchesBuilderHutProgress();
        refreshesStockEveryTwoColonyDays();
        com.colonybridge.minecolonies.collection.CollectionArchitectureTests.run();
        com.colonybridge.utility.DefenseStatisticsCalculatorTests.run();
        com.colonybridge.utility.SnapshotSummaryCalculatorTests.run();
        com.colonybridge.export.ExportPipelineTests.run();
        com.colonybridge.bootstrap.Phase5ArchitectureTests.run();
        com.colonybridge.market.MarketLogicTests.run();
        com.colonybridge.market.Phase6MarketArchitectureTests.run();
        System.out.println("Colony Bridge logic tests passed.");
    }

    private static void matchesBuilderHutProgress() {
        requireEquals(0, BuilderHutProgressCalculator.percent(0, 0), "empty work order progress");
        requireEquals(0, BuilderHutProgressCalculator.percent(100, 100), "unstarted work order progress");
        requireEquals(65, BuilderHutProgressCalculator.percent(100, 35), "builder hut percentage");
        requireEquals(100, BuilderHutProgressCalculator.percent(100, 0), "completed work order progress");
        requireEquals(0, BuilderHutProgressCalculator.percent(100, 150), "overreported remaining resources");
    }

    private static void sanitizesUnsafeNames() {
        requireEquals("minecraft-overworld", FilenameSanitizer.sanitize("minecraft:overworld"), "dimension filename");
        requireEquals("unicode-name", FilenameSanitizer.sanitize("Unicode Name"), "plain filename");
        requireEquals("unknown", FilenameSanitizer.sanitize(".."), "unsafe filename");
    }

    private static void validatesMinimums() {
        BridgeConfigValues config = new BridgeConfigValues(true, true, true, true, true, true, 1, 0, true, true, false, false, true, false, "", "").validated();
        requireEquals(30, config.periodicExportSeconds(), "periodic minimum");
        requireEquals(1, config.retainSnapshots(), "retention minimum");
        BridgeConfigValues fiveMinutes = new BridgeConfigValues(true, true, true, true, true, true, 300, 50, true, true, false, false, true, false, "", "").validated();
        requireEquals(300, fiveMinutes.periodicExportSeconds(), "five-minute interval should be preserved");
        BridgeConfigValues tenMinutes = new BridgeConfigValues(true, true, true, true, true, true, 600, 50, true, true, false, false, true, false, "", "").validated();
        requireEquals(600, tenMinutes.periodicExportSeconds(), "ten-minute interval should be preserved");
        BridgeConfigValues twoMinutes = new BridgeConfigValues(true, true, true, true, true, true, 120, 50, true, false, false, false, true, false, "", "").validated();
        requireEquals(120, twoMinutes.periodicExportSeconds(), "two-minute interval should be preserved");
        BridgeConfigValues tooLarge = new BridgeConfigValues(true, true, true, true, true, true, Integer.MAX_VALUE,
                Integer.MAX_VALUE, true, false, false, false, true, true, " https://example.com/snapshot ", " token ").validated();
        requireEquals(BridgeConfigValues.MAX_PERIODIC_SECONDS, tooLarge.periodicExportSeconds(), "periodic maximum");
        requireEquals(BridgeConfigValues.MAX_RETAINED_SNAPSHOTS, tooLarge.retainSnapshots(), "retention maximum");
        require(tooLarge.remoteSyncConfigured(), "trimmed remote configuration should be usable");
        BridgeConfigValues unsafeRemote = new BridgeConfigValues(true, true, true, true, true, true, 60, 50,
                true, false, false, false, true, true, "http://example.com/snapshot", "token").validated();
        require(!unsafeRemote.remoteSyncConfigured(), "unsafe HTTP remote configuration must not be usable");
        BridgeConfigValues malformedRemote = new BridgeConfigValues(true, true, true, true, true, true, 60, 50,
                true, false, false, false, true, true, "https://not a host/snapshot", "token").validated();
        require(!malformedRemote.remoteSyncConfigured(), "malformed remote configuration must not be usable");
    }

    private static void announcesOnlyNewDays() {
        DayCounterState counter = new DayCounterState();
        counter.reset(12);
        require(counter.observe(12).isEmpty(), "same day should not announce");
        requireEquals(13L, counter.observe(13).orElseThrow(), "next day should announce");
        require(counter.observe(13).isEmpty(), "day should announce only once");
        require(counter.observe(4).isEmpty(), "time reset should not announce");
        requireEquals(5L, counter.observe(5).orElseThrow(), "day after time reset should announce");
    }

    private static void rotatesDayCelebrations() {
        requireEquals(25, DayCelebrationMessages.count(), "day celebration variation count");
        java.util.Set<String> firstCycle = new java.util.HashSet<>();
        for (long day = 1; day <= 25; day++) {
            firstCycle.add(DayCelebrationMessages.forDay(day));
        }
        requireEquals(25, firstCycle.size(), "day celebrations should be unique within a cycle");
        requireEquals(DayCelebrationMessages.forDay(1), DayCelebrationMessages.forDay(26), "day celebrations should repeat after 25 days");
    }

    private static void ignoresTimestampAndTrigger() {
        String first = SnapshotFingerprinter.fingerprint(SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "manual"));
        String second = SnapshotFingerprinter.fingerprint(SnapshotFixtures.smallColony("2026-07-10T02:00:00Z", "interval"));
        requireEquals(first, second, "fingerprint stable across timing fields");
    }

    private static void ignoresVolatileObservations() {
        var first = SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "interval");
        var changed = new com.colonybridge.model.ColonySnapshot(
                first.schemaVersion(), first.bridgeVersion(), first.generatedAt(), first.trigger(), first.fingerprint(),
                first.game(), first.world(), first.colony(), first.summary(), first.citizens(), first.buildings(),
                first.requests(), first.construction(),
                new com.colonybridge.model.EnvironmentData("minecraft:plains", java.util.List.of("minecraft:plains"),
                        42L, 23000L, 900000L, 0, true, false, false),
                first.territory(), first.livestock(), first.research(), java.util.Map.of("citizens_born", 999),
                first.recentStatistics(),
                first.defenseStatistics(),
                new com.colonybridge.model.FoodSupplyData(12, 1, java.util.Map.of("minecraft:bread", 12),
                        2, 14, 7, 2.0, 6.0, 48, "watch", "medium",
                        1, 1, java.util.List.of("minecraft:bread"), 1, 12, false),
                first.stockLedger(),
                first.capabilities(), first.warnings(), first.errors());
        requireEquals(SnapshotFingerprinter.fingerprint(first), SnapshotFingerprinter.fingerprint(changed),
                "volatile observations should not create history");
    }

    private static void serializesNullsAndSchemaVersion() {
        String json = JsonSupport.toJson(SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "manual"), true);
        var root = JsonParser.parseString(json).getAsJsonObject();
        requireEquals(2, root.get("schemaVersion").getAsInt(), "schema version");
        require(json.contains("\"ownerUuid\": null"), "null owner UUID should be serialized");
        require(root.getAsJsonObject("capabilities").has("warehouseInventoryTotals"), "capability map should include unsupported feature");
        require(root.has("livestock"), "livestock overview should be serialized");
        requireEquals(1, root.getAsJsonObject("livestock").getAsJsonArray("huts").size(),
                "livestock should be grouped by staffed animal hut");
        require(root.has("recentStatistics"), "recent productivity should be serialized");
        require(root.has("defenseStatistics"), "defense statistics should be serialized");
        requireEquals(14, root.getAsJsonObject("defenseStatistics").getAsJsonObject("lifetime").get("total").getAsInt(),
                "lifetime guard kills should be serialized");
        require(root.getAsJsonObject("defenseStatistics").getAsJsonObject("today").get("reconciled").getAsBoolean(),
                "exact guard kill detail should be marked reconciled");
        require(root.has("foodSupply"), "food runway should be serialized");
        require(root.has("stockLedger"), "stock ledger should be serialized");
        require(root.getAsJsonObject("research").has("effects"), "research benefits should be serialized");
        requireEquals("minecraft:plains", root.getAsJsonObject("environment").get("centerBiome").getAsString(), "center biome");
    }

    private static void writesLatestAndAvoidsDuplicateHistory() throws Exception {
        Path dir = Files.createTempDirectory("colonybridge-test");
        SnapshotStore store = new SnapshotStore(dir, true);
        SnapshotWriteResult first = store.writeSnapshot(SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "manual"), 50);
        SnapshotWriteResult second = store.writeSnapshot(SnapshotFixtures.smallColony("2026-07-10T02:00:00Z", "interval"), 50);
        require(Files.isRegularFile(first.latestPath()), "latest file should exist");
        require(first.wroteHistoricalSnapshot(), "first write should create history");
        require(!second.wroteHistoricalSnapshot(), "duplicate should not create history");
        require(second.duplicate(), "second write should be marked duplicate");
    }

    private static void repairsMalformedLatestSnapshot() throws Exception {
        Path dir = Files.createTempDirectory("colonybridge-corrupt-latest-test");
        SnapshotStore store = new SnapshotStore(dir, true);
        SnapshotWriteResult first = store.writeSnapshot(SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "manual"), 50);
        Files.writeString(first.latestPath(), "{not valid json");
        SnapshotWriteResult repaired = store.writeSnapshot(SnapshotFixtures.smallColony("2026-07-10T02:00:00Z", "interval"), 50);
        require(JsonParser.parseString(Files.readString(repaired.latestPath())).isJsonObject(), "malformed latest snapshot should be replaced");
    }

    private static void appliesRetentionToDuplicateSnapshots() throws Exception {
        Path dir = Files.createTempDirectory("colonybridge-retention-test");
        SnapshotStore store = new SnapshotStore(dir, false);
        SnapshotWriteResult first = store.writeSnapshot(SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "manual"), 50);
        Path historyDir = first.historicalPath().getParent();
        Files.writeString(historyDir.resolve("legacy-a.json"), "{}");
        Files.writeString(historyDir.resolve("legacy-b.json"), "{}");
        SnapshotWriteResult duplicate = store.writeSnapshot(SnapshotFixtures.smallColony("2026-07-10T02:00:00Z", "interval"), 1);
        require(duplicate.duplicate(), "retention fixture must use an unchanged snapshot");
        try (var history = Files.list(historyDir)) {
            requireEquals(1L, history.filter(path -> path.getFileName().toString().endsWith(".json")).count(),
                    "lowered retention must apply even when the snapshot is unchanged");
        }
    }

    private static void estimatesTerritoryFromTicketedChunks() {
        var territory = com.colonybridge.model.TerritoryData.estimatedFromTickets(151, 117);
        requireEquals(117, territory.claimedChunks(), "ticketed chunks should provide the claim fallback");
        requireEquals(117 * 256, territory.approximateClaimedBlocks(), "claim fallback should report approximate block area");
        require(territory.widthChunks() == null, "estimated territory should not invent a bounding width");
        requireEquals(151, territory.loadedChunks(), "loaded chunk observation should be preserved");
    }

    private static void estimatesFoodRunway() {
        var stable = FoodRunwayAnalyzer.analyze(84, 3, java.util.Map.of("minecraft:bread", 84),
                4, 70, 7, 42, 15, 1, 3, java.util.List.of("minecraft:bread"), 2, 36, false);
        requireEquals(10.0, stable.averageMealsPerDay(), "seven-day meal average");
        requireEquals(8.4, stable.estimatedDaysRemaining(), "food runway");
        requireEquals(51, stable.estimatedRunoutColonyDay(), "estimated runout colony day");
        requireEquals("stable", stable.status(), "stable food status");
        requireEquals("high", stable.confidence(), "high confidence after a full sample");

        var learning = FoodRunwayAnalyzer.analyze(20, 1, java.util.Map.of("minecraft:apple", 20),
                0, 0, 1, 1, 4, 1, 1, java.util.List.of("minecraft:apple"), 1, 8, false);
        require(learning.estimatedDaysRemaining() == null, "zero-meal sample should remain in learning state");
        requireEquals("learning", learning.status(), "learning status");

        var critical = FoodRunwayAnalyzer.analyze(3, 1, java.util.Map.of("minecraft:bread", 3),
                2, 35, 7, 42, 15, 1, 1, java.util.List.of("minecraft:bread"), 2, 12, false);
        requireEquals(0.6, critical.estimatedDaysRemaining(), "critical runway");
        requireEquals("critical", critical.status(), "critical food status");

        var noDiningHall = FoodRunwayAnalyzer.analyze(0, 0, java.util.Map.of(),
                2, 35, 7, 42, 15, 0, 0, java.util.List.of(), 0, 0, false);
        require(noDiningHall.estimatedDaysRemaining() == null, "no dining hall should not produce a runway");
        requireEquals("no_dining_hall", noDiningHall.status(), "missing dining hall status");

        var emptyMenu = FoodRunwayAnalyzer.analyze(0, 0, java.util.Map.of(),
                2, 35, 7, 42, 15, 1, 0, java.util.List.of(), 0, 0, false);
        require(emptyMenu.estimatedDaysRemaining() == null, "empty menu should not produce a runway");
        requireEquals("menu_empty", emptyMenu.status(), "empty restaurant menu status");
    }

    private static void refreshesStockEveryTwoColonyDays() {
        require(StockRefreshPolicy.shouldRefresh(42, null, 2), "missing stock cache should refresh");
        require(!StockRefreshPolicy.shouldRefresh(43, 42, 2), "one-day-old stock cache should be reused");
        require(StockRefreshPolicy.shouldRefresh(44, 42, 2), "two-day-old stock cache should refresh");
        require(StockRefreshPolicy.shouldRefresh(10, 42, 2), "a colony-day reset should refresh");
        require(!StockRefreshPolicy.shouldRefresh(42, 42, 2, true, true), "repeated startup exports may reuse the startup scan");
        require(StockRefreshPolicy.shouldRefresh(42, 42, 2, true, false), "the first normal export must revalidate a startup scan");
        require(!StockRefreshPolicy.shouldRefresh(43, 42, 2, false, false), "a validated one-day-old scan should remain cached");
        requireEquals(44, StockRefreshPolicy.nextRefreshDay(42, 2), "next stock refresh day");
        requireEquals(1, StockRefreshPolicy.ageDays(43, 42), "stock cache age");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }
}
