package com.colonybridge.market;

public record InventoryMutation(String removeItemId, int removeCount, String grantItemId, int grantCount) {
    public InventoryMutation {
        if (!MarketItemIds.isVanilla(removeItemId) || !MarketItemIds.isVanilla(grantItemId)
                || removeCount <= 0 || grantCount <= 0) {
            throw new IllegalArgumentException("Inventory mutations require positive vanilla item quantities.");
        }
    }
}
