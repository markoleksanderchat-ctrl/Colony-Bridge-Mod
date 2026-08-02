package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.*;
import com.colonybridge.utility.SnapshotSummaryCalculator;

import java.util.List;

public final class SummaryCollector {
    public SummaryData collect(ColonyCollectionContext context, List<CitizenData> citizens,
                               List<BuildingData> buildings, List<RequestData> requests,
                               List<ConstructionData> construction) {
        context.requireServerThread();
        Integer capacity = CollectionSupport.safe(() -> context.colony().getCitizenManager().getMaxCitizens(), null);
        return SnapshotSummaryCalculator.summarize(capacity, citizens, buildings, requests, construction);
    }
}
