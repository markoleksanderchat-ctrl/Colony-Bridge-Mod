package com.colonybridge.market;

public final class MarketItemIds {
    private MarketItemIds() {
    }

    public static boolean isVanilla(String itemId) {
        return itemId != null && itemId.startsWith("minecraft:") && itemId.length() > "minecraft:".length();
    }
}
