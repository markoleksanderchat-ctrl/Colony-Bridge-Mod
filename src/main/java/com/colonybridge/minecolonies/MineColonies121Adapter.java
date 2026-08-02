package com.colonybridge.minecolonies;

import com.colonybridge.api.AdapterStatus;
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

    public MineColonies121Adapter(String mineColoniesVersion) {
        status = new AdapterStatus(true, "minecolonies-1.21.1-api", mineColoniesVersion,
                "MineColonies API adapter ready.");
        gameData = metadataCollector.game(status);
    }

    @Override
    public AdapterStatus status() {
        return status;
    }

    @Override
    public List<ColonySnapshot> collectAll(ServerLevelContext levelContext, BridgeConfigValues config,
                                           ExportTrigger trigger, Instant generatedAt) {
        List<IColony> colonies = IColonyManager.getInstance().getAllColonies().stream()
                .sorted(Comparator.comparingInt(IColony::getID)).toList();
        List<ColonySnapshot> snapshots = new ArrayList<>(colonies.size());
        for (IColony colony : colonies) {
            snapshots.add(collectColony(levelContext, config, trigger, generatedAt, colony));
        }
        return snapshots;
    }

    @Override
    public Optional<ColonySnapshot> collectById(ServerLevelContext levelContext, BridgeConfigValues config,
                                                int colonyId, ExportTrigger trigger, Instant generatedAt) {
        return IColonyManager.getInstance().getAllColonies().stream()
                .filter(colony -> colony.getID() == colonyId)
                .findFirst()
                .map(colony -> collectColony(levelContext, config, trigger, generatedAt, colony));
    }

    private ColonySnapshot collectColony(ServerLevelContext levelContext, BridgeConfigValues config,
                                          ExportTrigger trigger, Instant generatedAt, IColony colony) {
        ColonyCollectionContext context = ColonyCollectionContext.capture(
                levelContext, config, trigger, generatedAt, colony);

        CitizenBuildingCollector.Result people = citizenBuildingCollector.collect(context);
        RequestConstructionCollector.Result work = requestConstructionCollector.collect(context);
        ColonyData colonyData = metadataCollector.colony(context);
        SummaryData summary = summaryCollector.collect(context, people.citizens(), people.buildings(),
                work.requests(), work.construction());
        EnvironmentData environment = environmentTerritoryCollector.environment(context, people.buildings());
        TerritoryData territory = environmentTerritoryCollector.territory(context);
        LivestockData livestock = livestockCollector.collect(context);
        ResearchStatisticsCollector.Result research = researchStatisticsCollector.collect(context);
        DefenseStatisticsData defense = defenseCollector.collect(context, research.statistics());
        InventoryFoodStockCollector.Result inventory = inventoryCollector.collect(context, summary.citizenCount());
        WorldData world = metadataCollector.world(context);

        return assembler.assemble(context, gameData, new CollectedSections(
                colonyData, summary, people.citizens(), people.buildings(), work.requests(), work.construction(),
                environment, territory, livestock, research.research(), research.statistics().lifetime(),
                research.statistics().recent(), defense, inventory.foodSupply(), inventory.stockLedger(), world),
                CAPABILITIES);
    }
}
