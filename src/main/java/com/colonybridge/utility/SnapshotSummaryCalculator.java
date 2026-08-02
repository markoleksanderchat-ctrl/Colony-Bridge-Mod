package com.colonybridge.utility;

import com.colonybridge.model.BuildingData;
import com.colonybridge.model.CitizenData;
import com.colonybridge.model.ConstructionData;
import com.colonybridge.model.RequestData;
import com.colonybridge.model.SummaryData;

import java.util.List;

public final class SnapshotSummaryCalculator {
    private SnapshotSummaryCalculator() {
    }

    public static SummaryData summarize(Integer capacity, List<CitizenData> citizens, List<BuildingData> buildings,
                                        List<RequestData> requests, List<ConstructionData> construction) {
        int employed = (int) citizens.stream().filter(citizen -> Boolean.TRUE.equals(citizen.employed())).count();
        int children = (int) citizens.stream().filter(citizen -> Boolean.TRUE.equals(citizen.child())).count();
        int guards = (int) citizens.stream().filter(citizen -> Boolean.TRUE.equals(citizen.guard())).count();
        int staffed = (int) buildings.stream().filter(building -> Boolean.TRUE.equals(building.staffed())).count();
        return new SummaryData(
                citizens.size(),
                capacity,
                employed,
                Math.max(0, citizens.size() - employed - children),
                children,
                guards,
                buildings.size(),
                staffed,
                Math.max(0, buildings.size() - staffed),
                requests.size(),
                construction.size()
        );
    }
}
