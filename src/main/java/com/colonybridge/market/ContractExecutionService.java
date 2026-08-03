package com.colonybridge.market;

public final class ContractExecutionService {
    private ContractExecutionService() {
    }

    public static String validate(PlayerInventoryPort inventory, MarketContract contract, boolean alreadyCompleted,
                                  long now, boolean itemAvailable) {
        if (inventory.restrictedGameMode()) return "Royal contracts require Survival or Adventure mode.";
        if (contract == null || alreadyCompleted || contract.expired(now)) return "That contract is no longer available.";
        if (!itemAvailable || inventory.countPlainItems(contract.itemId()) < contract.quantity()) {
            return "You do not have the required unmodified goods.";
        }
        return null;
    }

    public static InventoryMutation mutation(MarketContract contract) {
        return new InventoryMutation(contract.itemId(), contract.quantity(), "minecraft:diamond", contract.rewardDiamonds());
    }
}
