package com.colonybridge.model;

import java.util.List;

public record ResearchData(
        List<String> completed,
        List<ResearchProjectData> inProgress,
        List<ResearchEffectData> effects
) {
    public ResearchData {
        completed = completed == null ? List.of() : List.copyOf(completed);
        inProgress = inProgress == null ? List.of() : List.copyOf(inProgress);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
