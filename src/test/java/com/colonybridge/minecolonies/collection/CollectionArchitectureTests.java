package com.colonybridge.minecolonies.collection;

import com.colonybridge.SnapshotFixtures;
import com.colonybridge.api.ExportTrigger;
import com.colonybridge.model.BridgeMessage;
import com.colonybridge.utility.JsonSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class CollectionArchitectureTests {
    private CollectionArchitectureTests() {
    }

    public static void run() throws Exception {
        assemblyPreservesCharacterizedSnapshot();
        safeSectionsPreserveDegradedIssueCodes();
        contextRejectsOffThreadCollection();
        livestockCachePreservesLastCompleteObservation();
        adapterRemainsAThinOrderedFacade();
        contextOwnsSingleSourceTraversal();
    }

    private static void assemblyPreservesCharacterizedSnapshot() {
        var expected = SnapshotFixtures.smallColony("2026-08-02T12:00:00Z", "manual");
        List<BridgeMessage> warnings = new ArrayList<>();
        List<BridgeMessage> errors = new ArrayList<>();
        var sections = new CollectedSections(
                expected.colony(), expected.summary(), expected.citizens(), expected.buildings(), expected.requests(),
                expected.construction(), expected.environment(), expected.territory(), expected.livestock(),
                expected.research(), expected.statistics(), expected.recentStatistics(), expected.defenseStatistics(),
                expected.foodSupply(), expected.stockLedger(), expected.world());
        var actual = new ColonySnapshotAssembler().assemble(Instant.parse(expected.generatedAt()), ExportTrigger.MANUAL,
                expected.game(), sections, expected.capabilities(), warnings, errors);
        require(JsonSupport.toJson(expected, false).equals(JsonSupport.toJson(actual, false)),
                "snapshot assembly must preserve exact nulls, arrays, maps, and field order");
    }

    private static void safeSectionsPreserveDegradedIssueCodes() {
        List<BridgeMessage> errors = new ArrayList<>();
        List<Object> result = CollectionIssues.safeList("colony", "7", "CITIZENS_FAILED", errors,
                () -> { throw new IllegalStateException("fixture failure"); });
        require(result.isEmpty(), "failed sections must degrade to an empty list");
        require(errors.size() == 1 && "CITIZENS_FAILED".equals(errors.getFirst().code()),
                "failed sections must retain their exact issue code");
        require("colony".equals(errors.getFirst().scope()) && "7".equals(errors.getFirst().entityId()),
                "failed sections must retain issue scope and entity");
    }

    private static void contextRejectsOffThreadCollection() throws InterruptedException {
        CollectionThreadGuard guard = CollectionThreadGuard.captureCurrent();
        guard.requireOwnerThread();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try { guard.requireOwnerThread(); } catch (Throwable throwable) { failure.set(throwable); }
        }, "contract-off-thread");
        other.start();
        other.join();
        require(failure.get() instanceof IllegalStateException,
                "MineColonies API collection must reject a thread other than the captured server thread");
    }

    private static void livestockCachePreservesLastCompleteObservation() {
        var expected = SnapshotFixtures.smallColony("2026-08-02T12:00:00Z", "manual").livestock();
        LivestockObservationCache cache = new LivestockObservationCache();
        require(cache.get(1).isEmpty(), "livestock cache must begin empty");
        cache.put(1, expected);
        require(cache.get(1).orElseThrow().equals(expected),
                "disconnect and shutdown exports must recover the last complete livestock observation");
    }

    private static void adapterRemainsAThinOrderedFacade() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/colonybridge/minecolonies/MineColonies121Adapter.java"));
        require(source.lines().count() < 250, "adapter facade must remain below 250 lines");
        List<String> order = List.of(
                "ColonyCollectionContext.capture", "citizenBuildingCollector.collect", "requestConstructionCollector.collect",
                "metadataCollector.colony", "summaryCollector.collect", "environmentTerritoryCollector.environment",
                "environmentTerritoryCollector.territory", "livestockCollector.collect", "researchStatisticsCollector.collect",
                "defenseCollector.collect", "inventoryCollector.collect", "metadataCollector.world", "assembler.assemble");
        int previous = -1;
        for (String marker : order) {
            int current = source.indexOf(marker);
            require(current > previous, "collector order drifted at " + marker);
            previous = current;
        }
        require(!source.contains("Class.forName") && !source.contains("ServiceLoader") && !source.contains("CollectorRegistry"),
                "collection orchestration must not use reflection or a generic registry");
    }

    private static void contextOwnsSingleSourceTraversal() throws Exception {
        Path root = Path.of("src/main/java/com/colonybridge/minecolonies/collection");
        String context = Files.readString(root.resolve("ColonyCollectionContext.java"));
        require(count(context, "getCitizens()") == 1, "citizens must be captured once");
        require(count(context, "getBuildings().values()") == 1, "buildings must be captured once");
        require(count(context, "getWorkOrders().values()") == 1, "work orders must be captured once");
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("ColonyCollectionContext.java")) continue;
                String source = Files.readString(file);
                require(!source.contains("getCitizens()") && !source.contains("getBuildings().values()")
                                && !source.contains("getWorkOrders().values()"),
                        "collector repeated a captured MineColonies traversal: " + file.getFileName());
            }
        }
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
