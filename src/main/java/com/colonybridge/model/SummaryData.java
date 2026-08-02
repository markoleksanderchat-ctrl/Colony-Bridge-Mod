package com.colonybridge.model;

public record SummaryData(
        int citizenCount,
        Integer citizenCapacity,
        int employedCitizens,
        int unemployedCitizens,
        int children,
        int guards,
        int buildingCount,
        int staffedBuildings,
        int unstaffedBuildings,
        int activeRequests,
        int activeConstructionProjects
) {
}
