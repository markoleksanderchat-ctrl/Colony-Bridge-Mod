package com.colonybridge;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class BridgeBehaviorTests {
    private static final Map<Class<?>, List<String>> BEHAVIORS = behaviors();

    @TestFactory
    Stream<DynamicNode> discoversEveryRetainedBehaviorBySubsystem() {
        return BEHAVIORS.entrySet().stream().map(entry -> DynamicContainer.dynamicContainer(
                subsystemName(entry.getKey()),
                entry.getValue().stream().map(name -> DynamicTest.dynamicTest(behaviorName(name),
                        () -> invoke(entry.getKey(), name)))
        ));
    }

    static int expectedBehaviorCount() {
        return BEHAVIORS.values().stream().mapToInt(List::size).sum();
    }

    private static Map<Class<?>, List<String>> behaviors() {
        Map<Class<?>, List<String>> tests = new LinkedHashMap<>();
        tests.put(CoreLogicBehaviorTests.class, List.of(
                "sanitizesUnsafeNames", "validatesMinimums", "ignoresTimestampAndTrigger",
                "ignoresVolatileObservations", "serializesNullsAndSchemaVersion",
                "writesLatestAndAvoidsDuplicateHistory", "appliesRetentionToDuplicateSnapshots",
                "repairsMalformedLatestSnapshot", "estimatesTerritoryFromTicketedChunks",
                "announcesOnlyNewDays", "rotatesDayCelebrations", "estimatesFoodRunway",
                "matchesBuilderHutProgress", "refreshesStockEveryTwoColonyDays"));
        tests.put(com.colonybridge.minecolonies.collection.CollectionArchitectureTests.class, List.of(
                "assemblyPreservesCharacterizedSnapshot", "safeSectionsPreserveDegradedIssueCodes",
                "contextRejectsOffThreadCollection", "livestockCachePreservesLastCompleteObservation",
                "adapterRemainsAThinOrderedFacade", "contextOwnsSingleSourceTraversal"));
        tests.put(com.colonybridge.utility.DefenseStatisticsCalculatorTests.class, List.of(
                "reconcilesExactDetailWithoutDoubleCounting", "preservesLegacyGapsAsUnclassified",
                "prefersDetailedEvidenceWhenItExceedsTheSummary", "omitsZeroValueEntityNoise"));
        tests.put(com.colonybridge.utility.SnapshotSummaryCalculatorTests.class, List.of(
                "countsAuthoritativeGuardFlags", "countsActualWorkplacesInsteadOfAssociatedCitizens"));
        tests.put(com.colonybridge.export.ExportPipelineTests.class, List.of(
                "preparedMaterialPreservesBytesAndFingerprint", "fingerprintCacheFallsBackToDiskAfterRestart",
                "remoteQueueIsOneFlightAndBounded", "remoteQueueShutdownIsBounded",
                "bridgeInfoAdvertisesDesktopContract", "remoteRetryDelayIsBounded",
                "sanitizesWithoutMutatingSource", "rejectsUnsafeRemoteEndpoints",
                "statusDefensivelyCopiesPaths", "malformedTimestampCannotEscapeHistoryDirectory",
                "snapshotMapsSerializeDeterministically"));
        tests.put(com.colonybridge.bootstrap.Phase5ArchitectureTests.class, List.of(
                "bootstrapIsAThinNeoForgeEntrypoint", "lifecyclePreservesEveryExportTrigger",
                "commandTreeAndPermissionsRemainStable", "notificationPresentationRemainsStable",
                "typedSettingsRoundTripLegacyValues", "currentTomlFixtureLoadsWithoutMigration",
                "configSpecRetainsCurrentSectionsKeysAndDefaults", "timingConstantsRetainUnitsAndValues"));
        tests.put(com.colonybridge.market.MarketLogicTests.class, List.of(
                "calculatesBasePrices", "advancesDeterministicallyWithinBounds", "decaysEvents",
                "appliesTradePressure", "validatesQuotesAndPayment", "rejectsModdedIds",
                "mapsOnlineIssuersExactly", "validatesOnlineConnectionContract", "validatesMarketScreenBounds",
                "appliesValidatedOnlineMovements", "persistsConsistently", "migratesVersionOneState",
                "rotatesContractsDeterministically", "validatesTraderRecipeFormat", "validatesExchangeResources"));
        tests.put(com.colonybridge.market.Phase6MarketArchitectureTests.class, List.of(
                "matchesFrozenDomainFixtures", "commitsTransactionsExactlyOnce",
                "rollsBackInventoryAndStateWhenPersistenceFails", "validatesTradeAndContractFailuresBeforeMutation",
                "isolatesOnlineParsingFailoverCacheInfluenceAndAnomalies", "validatesIssuerCatalogCoverageAndChecksum",
                "preservesMenuAndPresentationContracts", "preservesClientInputAndJeiBoundaries"));
        return Map.copyOf(tests);
    }

    private static void invoke(Class<?> type, String methodName) throws Throwable {
        Method method = type.getDeclaredMethod(methodName);
        method.setAccessible(true);
        try {
            method.invoke(null);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static String subsystemName(Class<?> type) {
        String packageName = type.getPackageName().replace("com.colonybridge", "bridge");
        return packageName + ": " + type.getSimpleName();
    }

    private static String behaviorName(String methodName) {
        return methodName.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
    }
}
