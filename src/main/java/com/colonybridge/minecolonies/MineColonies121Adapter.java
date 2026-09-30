package com.colonybridge.minecolonies;

import com.colonybridge.api.AdapterStatus;
import com.colonybridge.api.CollectionProfile;
import com.colonybridge.api.ExportTrigger;
import com.colonybridge.api.MineColoniesAdapter;
import com.colonybridge.api.ServerLevelContext;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.minecolonies.collection.*;
import com.colonybridge.model.*;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

public final class MineColonies121Adapter implements MineColoniesAdapter {
    private static final Map<String, CapabilityData> CAPABILITIES = MetadataCollector.capabilities();

    private final AdapterStatus status;
    private final GameData gameData;
    private final MetadataCollector metadataCollector = new MetadataCollector();
    private final CitizenBuildingCollector citizenBuildingCollector = new CitizenBuildingCollector();
    private final RequestConstructionCollector requestConstructionCollector = new RequestConstructionCollector();
    private final SummaryCollector summaryCollector = new SummaryCollector();
    private final EnvironmentTerritoryCollector environmentTerritoryCollector = new EnvironmentTerritoryCollector();
    private final LivestockCollector livestockCollector = new LivestockCollector();
    private final ResearchStatisticsCollector researchStatisticsCollector = new ResearchStatisticsCollector();
    private final DefenseCollector defenseCollector = new DefenseCollector();
    private final InventoryFoodStockCollector inventoryCollector = new InventoryFoodStockCollector();
    private final ColonySnapshotAssembler assembler = new ColonySnapshotAssembler();
    private volatile CollectionProfile lastCollectionProfile = CollectionProfile.EMPTY;

    public MineColonies121Adapter(String mineColoniesVersion) {
        status = new AdapterStatus(true, "minecolonies-1.21.1-api", mineColoniesVersion,
                "MineColonies API adapter ready.");
        gameData = metadataCollector.game(status);
    }

    @Override
    public void resetSession() {
        inventoryCollector.clear();
        defenseCollector.clear();
        environmentTerritoryCollector.beginCollection();
        livestockCollector.clear();
        lastCollectionProfile = CollectionProfile.EMPTY;
    }

    @Override
    public AdapterStatus status() {
        return status;
    }

    @Override
    public CollectionProfile lastCollectionProfile() {
        return lastCollectionProfile;
    }

    @Override
    public List<ColonySnapshot> collectAll(ServerLevelContext levelContext, BridgeConfigValues config,
                                           ExportTrigger trigger, Instant generatedAt) {
        environmentTerritoryCollector.beginCollection();
        long started = System.nanoTime();
        List<IColony> colonies = IColonyManager.getInstance().getAllColonies().stream()
                .sorted(Comparator.comparingInt(IColony::getID)).toList();
        List<ColonySnapshot> snapshots = new ArrayList<>(colonies.size());
        List<CollectionProfile> profiles = new ArrayList<>(colonies.size());
        for (IColony colony : colonies) {
            ProfiledSnapshot result = collectColony(levelContext, config, trigger, generatedAt, colony);
            snapshots.add(result.snapshot());
            profiles.add(result.profile());
        }
        lastCollectionProfile = CollectionProfile.aggregate(profiles, micros(System.nanoTime() - started));
        return snapshots;
    }

    @Override
    public Optional<ColonySnapshot> collectById(ServerLevelContext levelContext, BridgeConfigValues config,
                                                int colonyId, ExportTrigger trigger, Instant generatedAt) {
        environmentTerritoryCollector.beginCollection();
        long started = System.nanoTime();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getID() != colonyId) continue;
            ProfiledSnapshot result = collectColony(levelContext, config, trigger, generatedAt, colony);
            lastCollectionProfile = CollectionProfile.aggregate(List.of(result.profile()), micros(System.nanoTime() - started));
            return Optional.of(result.snapshot());
        }
        lastCollectionProfile = CollectionProfile.aggregate(List.of(), micros(System.nanoTime() - started));
        return Optional.empty();
    }

    private ProfiledSnapshot collectColony(ServerLevelContext levelContext, BridgeConfigValues config,
                                            ExportTrigger trigger, Instant generatedAt, IColony colony) {
        long started = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        ColonyCollectionContext context = measure(stages, "capture", () -> ColonyCollectionContext.capture(
                levelContext, config, trigger, generatedAt, colony));

        CitizenBuildingCollector.Result people = measure(stages, "citizensBuildings", () -> citizenBuildingCollector.collect(context));
        RequestConstructionCollector.Result work = measure(stages, "requestsConstruction", () -> requestConstructionCollector.collect(context));
        ColonyData colonyData = measure(stages, "colonyMetadata", () -> metadataCollector.colony(context));
        SummaryData summary = measure(stages, "summary", () -> summaryCollector.collect(context, people.citizens(), people.buildings(),
                work.requests(), work.construction()));
        EnvironmentData environment = measure(stages, "environment", () -> environmentTerritoryCollector.environment(context, people.buildings()));
        TerritoryData territory = measure(stages, "territory", () -> environmentTerritoryCollector.territory(context));
        LivestockCollector.Result livestockResult = measure(stages, "livestock", () -> livestockCollector.collect(context));
        LivestockData livestock = livestockResult.data();
        ResearchStatisticsCollector.Result research = measure(stages, "researchStatistics", () -> researchStatisticsCollector.collect(context));
        DefenseCollector.Result defenseResult = measure(stages, "defense", () -> defenseCollector.collect(context, research.statistics()));
        DefenseStatisticsData defense = defenseResult.data();
        InventoryFoodStockCollector.Result inventory = measure(stages, "inventoryFoodStock", () -> inventoryCollector.collect(context, summary.citizenCount()));
        stages.putAll(inventoryCollector.timings());
        WorldData world = measure(stages, "worldMetadata", () -> metadataCollector.world(context));

        ColonySnapshot snapshot = measure(stages, "assembly", () -> assembler.assemble(context, gameData, new CollectedSections(
                colonyData, summary, people.citizens(), people.buildings(), work.requests(), work.construction(),
                environment, territory, livestock, research.research(), research.statistics().lifetime(),
                research.statistics().recent(), defense, inventory.foodSupply(), inventory.stockLedger(), world),
                CAPABILITIES));
        CollectionProfile profile = new CollectionProfile(1, context.citizens().size(), context.buildings().size(),
                context.workOrders().size(), inventory.cacheHit() ? 0 : inventory.stockLedger().scannedHandlers(),
                inventory.cacheHit() ? 0 : inventory.stockLedger().scannedSlots(),
                inventory.cacheHit() ? 1 : 0, inventory.cacheHit() ? 0 : 1,
                livestockResult.entitiesConsidered(), livestockResult.hutsConsidered(),
                research.statistics().typesConsidered(), defenseResult.buildingsConsidered(), stages,
                micros(System.nanoTime() - started));
        return new ProfiledSnapshot(snapshot, profile);
    }

    private static <T> T measure(Map<String, Long> stages, String name, Supplier<T> action) {
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            stages.put(name, micros(System.nanoTime() - started));
        }
    }

    private static long micros(long nanos) {
        return TimeUnit.NANOSECONDS.toMicros(Math.max(0, nanos));
    }

    private record ProfiledSnapshot(ColonySnapshot snapshot, CollectionProfile profile) { }
}
