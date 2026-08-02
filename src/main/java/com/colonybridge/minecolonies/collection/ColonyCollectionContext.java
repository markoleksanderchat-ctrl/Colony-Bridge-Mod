package com.colonybridge.minecolonies.collection;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.api.ServerLevelContext;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.model.BridgeMessage;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.workorders.IWorkOrder;

import java.time.Instant;
import java.util.*;

public record ColonyCollectionContext(
        ServerLevelContext levelContext,
        BridgeConfigValues config,
        ExportTrigger trigger,
        Instant generatedAt,
        IColony colony,
        List<ICitizenData> citizens,
        List<ICommonBuilding> buildings,
        List<IWorkOrder> workOrders,
        Integer currentDay,
        Map<ICommonBuilding, CollectionSupport.BuildingAccess> buildingAccess,
        Map<Integer, IBuilding> inferredWorkplaces,
        List<BridgeMessage> warnings,
        List<BridgeMessage> errors,
        CollectionThreadGuard threadGuard
) {
    public static ColonyCollectionContext capture(ServerLevelContext levelContext, BridgeConfigValues config,
                                                   ExportTrigger trigger, Instant generatedAt, IColony colony) {
        List<ICitizenData> citizens = List.copyOf(colony.getCitizenManager().getCitizens());
        Collection<?> candidates = colony.getCommonBuildingManager().getBuildings().values();
        List<ICommonBuilding> buildings = new ArrayList<>(candidates.size());
        for (Object candidate : candidates) if (candidate instanceof ICommonBuilding building) buildings.add(building);
        List<IWorkOrder> workOrders = colony.getWorkManager().getWorkOrders().values().stream()
                .filter(IWorkOrder.class::isInstance).map(IWorkOrder.class::cast).toList();
        Map<ICommonBuilding, CollectionSupport.BuildingAccess> access = CollectionSupport.captureBuildingAccess(buildings);
        return new ColonyCollectionContext(
                levelContext, config, trigger, generatedAt, colony,
                citizens, List.copyOf(buildings), workOrders,
                CollectionSupport.safe(colony::getDay, null),
                access, CollectionSupport.inferredWorkplaces(buildings, access),
                new ArrayList<>(), new ArrayList<>(), CollectionThreadGuard.captureCurrent());
    }

    public void requireServerThread() {
        threadGuard.requireOwnerThread();
    }

    public String colonyId() {
        return String.valueOf(colony.getID());
    }
}
