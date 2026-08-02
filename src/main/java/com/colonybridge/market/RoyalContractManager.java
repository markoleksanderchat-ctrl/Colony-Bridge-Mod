package com.colonybridge.market;

import java.util.List;

public final class RoyalContractManager {
    private static final List<ContractTemplate> TEMPLATES = List.of(
            new ContractTemplate("minecraft:stone", 256, "Mason's Commission", "Stone is required for the Crown's current works."),
            new ContractTemplate("minecraft:oak_log", 128, "Timber Writ", "Royal builders require seasoned timber."),
            new ContractTemplate("minecraft:iron_ingot", 64, "Armorer's Requisition", "The guard requires iron for arms and repairs."),
            new ContractTemplate("minecraft:wheat", 256, "Granary Order", "The royal granary is replenishing its reserves."),
            new ContractTemplate("minecraft:bread", 128, "Provisioning Order", "Fresh provisions are needed for workers and patrols."),
            new ContractTemplate("minecraft:arrow", 256, "Fletcher's Requisition", "The realm's defensive stores require arrows."),
            new ContractTemplate("minecraft:bricks", 128, "Civic Works Order", "Masons are expanding the settlement's public works."),
            new ContractTemplate("minecraft:glass", 128, "Glazier's Commission", "New royal buildings require finished glass."),
            new ContractTemplate("minecraft:coal", 128, "Foundry Fuel Order", "Workshops require dependable fuel reserves."),
            new ContractTemplate("minecraft:paper", 128, "Clerk's Commission", "The royal offices require paper for new ledgers."),
            new ContractTemplate("minecraft:leather", 64, "Quartermaster's Order", "The quartermaster is replenishing durable goods."),
            new ContractTemplate("minecraft:copper_ingot", 96, "Minting Commission", "Copper is required for fittings and civic works.")
    );

    private RoyalContractManager() {
    }

    public static ContractTemplate template(long seed, long bucket, int index) {
        int mixed = MarketEventManager.mix(seed ^ bucket ^ ((long) index * 0x9e3779b97f4a7c15L));
        return TEMPLATES.get(Math.floorMod(mixed, TEMPLATES.size()));
    }

    public record ContractTemplate(String itemId, int quantity, String title, String description) {
    }
}
