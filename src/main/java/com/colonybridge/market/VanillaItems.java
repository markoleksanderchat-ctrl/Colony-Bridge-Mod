package com.colonybridge.market;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class VanillaItems {
    private VanillaItems() { }
    public static Item resolve(String itemId) {
        if (!MarketItemIds.isVanilla(itemId)) return Items.AIR;
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
    }
}
