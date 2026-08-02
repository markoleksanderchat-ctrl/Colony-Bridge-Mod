package com.colonybridge.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MarketEventManager {
    public static final List<EventTemplate> TEMPLATES = List.of(
            new EventTemplate("failed_harvest", "Failed Harvest", "Poor yields tighten the realm's food supply.", List.of("#crops", "#food"), 0.24),
            new EventTemplate("construction_boom", "Construction Boom", "New works draw heavily on common building stock.", List.of("#building"), 0.20),
            new EventTemplate("undead_panic", "Undead Panic", "Night patrols are buying protective supplies.", List.of("#combat", "minecraft:golden_apple"), 0.22),
            new EventTemplate("nether_routes", "Nether Trade Routes Reopen", "Safer routes have eased the cost of Nether goods.", List.of("#nether"), -0.18),
            new EventTemplate("royal_commission", "Royal Building Commission", "A royal commission is consuming fine materials.", List.of("#building", "#precious"), 0.18),
            new EventTemplate("mining_collapse", "Mining Collapse", "A collapsed shaft has constrained mineral supply.", List.of("#mineral"), 0.25),
            new EventTemplate("artifact_speculation", "Artifact Speculation", "Collectors are bidding aggressively for rarities.", List.of("#artifact"), 0.28),
            new EventTemplate("mobilization", "Military Mobilization", "The guard is securing arms and provisions.", List.of("#combat", "#food"), 0.21),
            new EventTemplate("shipping_disruption", "Shipping Disruption", "Delayed caravans have raised transport costs.", List.of("#travel", "#ocean"), 0.19),
            new EventTemplate("oversupply", "Warehouse Oversupply", "Full storehouses are pressing commodity prices down.", List.of("#bulk"), -0.22),
            new EventTemplate("explorer_discoveries", "Explorer Discoveries", "New finds have reached the Exchange in quantity.", List.of("#artifact", "#travel"), -0.16),
            new EventTemplate("festival_demand", "Festival Demand", "Celebrations are lifting demand for food and finery.", List.of("#food", "#decorative"), 0.17)
    );

    private MarketEventManager() {
    }

    public static List<MarketEvent> advance(List<MarketEvent> existing, long seed, long now, int frequencyMinutes) {
        long frequency = Math.max(1, frequencyMinutes) * 60_000L;
        long bucket = Math.floorDiv(now, frequency);
        String id = "event-" + bucket;
        List<MarketEvent> result = new ArrayList<>();
        for (MarketEvent event : existing == null ? List.<MarketEvent>of() : existing) {
            if (event.startTime() + event.durationMillis() > now && !event.id().equals(id)) result.add(event);
        }
        int index = Math.floorMod(mix(seed ^ bucket), TEMPLATES.size());
        EventTemplate template = TEMPLATES.get(index);
        long start = bucket * frequency;
        result.add(new MarketEvent(id, template.title(), template.description(), template.targets(),
                template.modifier(), start, frequency * 2));
        return List.copyOf(result);
    }

    public static MarketEvent force(String name, long seed, long now, int durationMinutes) {
        String normalized = name.toLowerCase(Locale.ROOT).replace('-', '_');
        EventTemplate template = TEMPLATES.stream().filter(value -> value.id().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown market event: " + name));
        return new MarketEvent("forced-" + now + "-" + Math.abs(mix(seed ^ now)), template.title(),
                template.description(), template.targets(), template.modifier(), now,
                Math.max(1, durationMinutes) * 60_000L);
    }

    public static double modifier(List<MarketEvent> events, String itemId, java.util.Set<String> tags, long now) {
        double value = 1;
        for (MarketEvent event : events == null ? List.<MarketEvent>of() : events) {
            if (event.affects(itemId, tags)) value *= 1 + event.priceModifier() * event.strengthAt(now);
        }
        return value;
    }

    static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return (int) (value ^ (value >>> 32));
    }

    public record EventTemplate(String id, String title, String description, List<String> targets, double modifier) {
    }
}
