package com.colonybridge.model;

public record ResearchEffectData(
        String id,
        String nameTranslationKey,
        String subtitleTranslationKey,
        Double strength
) {
}
