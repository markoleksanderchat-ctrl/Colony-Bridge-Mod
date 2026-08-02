package com.colonybridge.model;

public record ResearchProjectData(
        String id,
        String branch,
        Integer depth,
        Integer progress,
        String state
) {
}
