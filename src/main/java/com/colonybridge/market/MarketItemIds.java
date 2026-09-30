package com.colonybridge.market;

public final class MarketItemIds {
    private MarketItemIds() {
    }

    public static boolean isTradable(String itemId) {
        return isVanilla(itemId) && !itemId.equals("minecraft:air")
                && !itemId.equals("minecraft:diamond") && !itemId.equals("minecraft:diamond_block")
                && !itemId.equals("minecraft:diamond_ore") && !itemId.equals("minecraft:deepslate_diamond_ore");
    }

    public static boolean isVanilla(String itemId) {
        return itemId != null && itemId.matches("minecraft:[a-z0-9_./-]+");
    }
}
