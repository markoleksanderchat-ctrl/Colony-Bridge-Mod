package com.colonybridge.market;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public final class VanillaItemClassifier {
    private final Map<String, Double> recipeComplexities = new HashMap<>();

    public ClassifiedItem classify(Item item, int quantity) {
        return classify(item, quantity, 0);
    }

    public ClassifiedItem classify(MinecraftServer server, Item item, int quantity) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        double recipeComplexity = recipeComplexities.computeIfAbsent(itemId, ignored ->
                server.getRecipeManager().getRecipes().stream()
                        .filter(holder -> holder.value().getResultItem(server.registryAccess()).is(item))
                        .mapToDouble(holder -> Math.min(7,
                                0.5 + Math.max(0, holder.value().getIngredients().size() - 1) * 0.35))
                        .min().orElse(0));
        return classify(item, quantity, recipeComplexity);
    }

    private ClassifiedItem classify(Item item, int quantity, double recipeComplexity) {
        var key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null || item == Items.AIR) {
            throw new IllegalArgumentException("Only registered vanilla Minecraft items may be traded.");
        }
        ItemStack stack = item.getDefaultInstance();
        ItemClassificationFacts facts = new ItemClassificationFacts(key.toString(),
                stack.getHoverName().getString(), quantity, stack.getRarity().ordinal(), stack.getMaxStackSize(),
                stack.isDamageableItem(), stack.has(DataComponents.FOOD), stack.is(ItemTags.LOGS),
                stack.is(ItemTags.PLANKS), stack.is(ItemTags.SAPLINGS), stack.is(ItemTags.SWORDS),
                stack.is(ItemTags.AXES), stack.is(ItemTags.BOW_ENCHANTABLE), stack.is(ItemTags.BOATS),
                recipeComplexity);
        return VanillaClassificationDomain.classify(facts);
    }
}
