package com.colonybridge.market;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class VanillaItemClassifier {
    private static final Map<String, Double> OVERRIDES = overrides();
    private final Map<String, Double> recipeComplexities = new HashMap<>();

    public ClassifiedItem classify(Item item, int quantity) {
        return classify(item, quantity, 0);
    }

    public ClassifiedItem classify(MinecraftServer server, Item item, int quantity) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        double recipeComplexity = recipeComplexities.computeIfAbsent(itemId, ignored -> server.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getResultItem(server.registryAccess()).is(item))
                .mapToDouble(holder -> Math.min(7, 0.5 + Math.max(0, holder.value().getIngredients().size() - 1) * 0.35))
                .min().orElse(0));
        return classify(item, quantity, recipeComplexity);
    }

    private ClassifiedItem classify(Item item, int quantity, double recipeComplexity) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null || !MarketItemIds.isVanilla(key.toString()) || item == Items.AIR) {
            throw new IllegalArgumentException("Only registered vanilla Minecraft items may be traded.");
        }
        ItemStack stack = item.getDefaultInstance();
        String path = key.getPath();
        Set<String> tags = classifyTags(stack, path);
        Rarity rarityClass = stack.getRarity();

        double rarity = switch (rarityClass) {
            case COMMON -> 1;
            case UNCOMMON -> 3;
            case RARE -> 5;
            case EPIC -> 6;
        };
        if (tags.contains("#mineral")) rarity += path.contains("diamond") ? 3 : path.contains("netherite") ? 4 : 1;
        if (tags.contains("#artifact")) rarity = Math.max(rarity, 5);
        rarity = PriceCalculator.clamp(0, 7, rarity);

        double labor = tags.contains("#bulk") ? 1.2 : tags.contains("#crafted") ? 2.5 : 2;
        if (tags.contains("#artifact")) labor += 2.5;
        double hazard = tags.contains("#end") ? 5 : tags.contains("#nether") ? 4 : tags.contains("#combat") ? 3 : 1;
        double complexity = Math.max(recipeComplexity, tags.contains("#crafted") ? (stack.isDamageableItem() ? 3 : 1.5) : 0.5);
        if (path.contains("potion") || path.contains("beacon") || path.contains("conduit")) complexity += 2;
        double dimension = tags.contains("#end") ? 3 : tags.contains("#nether") ? 2 : tags.contains("#ocean") ? 1 : 0;
        double equipment = tags.contains("#artifact") ? 4 : tags.contains("#mineral") ? 2 : 1;
        double travel = tags.contains("#travel") ? 3 : tags.contains("#artifact") ? 4 : 1;
        double renewability = tags.contains("#unique") ? 2.25 : tags.contains("#farmable") ? 0.58
                : tags.contains("#artifact") ? 1.50 : tags.contains("#mineral") ? 1.20 : 1.0;
        double automation = tags.contains("#farmable") || tags.contains("#bulk") ? 0.60
                : tags.contains("#artifact") ? 1.35 : 1.0;
        double utility = tags.contains("#combat") ? 1.28 : tags.contains("#food") ? 1.12
                : tags.contains("#artifact") ? 1.48 : tags.contains("#building") ? 1.0 : 0.95;
        double replaceability = tags.contains("#unique") ? 2.0 : tags.contains("#artifact") ? 1.28
                : tags.contains("#farmable") ? 0.90 : 1.0;
        double wholesale = tags.contains("#unique") ? 0 : tags.contains("#bulk") ? 1
                : tags.contains("#crafted") ? 0.35 : tags.contains("#artifact") ? 0.15 : 0.65;
        double confidence = OVERRIDES.containsKey(key.toString()) ? 1.0 : 0.82;
        double volatility = tags.contains("#unique") || tags.contains("#artifact") ? 0.70
                : tags.contains("#food") ? 0.42 : tags.contains("#bulk") ? 0.18 : 0.30;

        TradeValueInput input = new TradeValueInput(key.toString(), stack.getHoverName().getString(), quantity,
                rarity, labor, hazard, complexity, dimension, equipment, travel, renewability, automation,
                utility, replaceability, wholesale, confidence);
        String increase = tags.contains("#artifact") ? "scarcity and difficult replacement"
                : tags.contains("#nether") || tags.contains("#end") ? "dimensional acquisition"
                : tags.contains("#combat") ? "hazard and strategic utility" : "labor and utility";
        String decrease = tags.contains("#farmable") ? "renewable production"
                : tags.contains("#bulk") ? "bulk availability" : "ordinary replaceability";
        return new ClassifiedItem(input, volatility, Set.copyOf(tags), increase, decrease,
                "The Royal Exchange weighs scarcity, labor, danger, production, utility, and replacement.",
                OVERRIDES.get(key.toString()));
    }

    private static Set<String> classifyTags(ItemStack stack, String path) {
        Set<String> tags = new HashSet<>();
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || path.contains("stone") || path.contains("brick")
                || path.contains("glass") || path.contains("concrete") || path.contains("terracotta")) {
            tags.add("#building"); tags.add("#bulk");
        }
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.SAPLINGS) || path.contains("crop") || path.contains("seed")
                || path.contains("wheat") || path.contains("carrot") || path.contains("potato")) tags.add("#farmable");
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.BOW_ENCHANTABLE)
                || path.contains("armor") || path.contains("shield") || path.contains("arrow")) tags.add("#combat");
        if (path.contains("ore") || path.contains("ingot") || path.contains("diamond") || path.contains("emerald")
                || path.contains("coal") || path.contains("lapis") || path.contains("quartz")) tags.add("#mineral");
        if (path.contains("nether") || path.contains("blaze") || path.contains("ghast") || path.contains("magma")) tags.add("#nether");
        if (path.contains("end_") || path.contains("chorus") || path.contains("shulker") || path.contains("elytra")
                || path.contains("dragon")) tags.add("#end");
        if (path.contains("ocean") || path.contains("prismarine") || path.contains("coral") || path.contains("nautilus")
                || path.contains("heart_of_the_sea")) tags.add("#ocean");
        if (stack.is(ItemTags.BOATS) || path.contains("minecart") || path.contains("saddle") || path.contains("elytra")) tags.add("#travel");
        if (stack.has(DataComponents.FOOD) || path.contains("cake") || path.contains("stew") || path.contains("soup")) tags.add("#food");
        if (path.contains("template") || path.contains("totem") || path.contains("elytra") || path.contains("dragon_egg")
                || path.contains("enchanted_golden_apple") || path.contains("nether_star")) tags.add("#artifact");
        if (path.contains("dragon_egg")) tags.add("#unique");
        if (path.contains("wool") || path.contains("banner") || path.contains("painting") || path.contains("flower")) tags.add("#decorative");
        if (path.contains("planks") || path.contains("stairs") || path.contains("slab") || path.contains("door")
                || path.contains("chest") || path.contains("piston") || stack.isDamageableItem()) tags.add("#crafted");
        if (tags.isEmpty()) tags.add("#ordinary");
        if (stack.getMaxStackSize() >= 64) tags.add("#bulk");
        if (path.contains("gold") || path.contains("diamond") || path.contains("emerald") || path.contains("netherite")) tags.add("#precious");
        if (path.contains("wheat") || path.contains("carrot") || path.contains("potato") || path.contains("beetroot")) tags.add("#crops");
        return tags;
    }

    private static Map<String, Double> overrides() {
        Map<String, Double> values = new HashMap<>();
        values.put("minecraft:diamond", 1.0);
        values.put("minecraft:netherite_ingot", 4.0);
        values.put("minecraft:elytra", 24.0);
        values.put("minecraft:totem_of_undying", 8.0);
        values.put("minecraft:enchanted_golden_apple", 16.0);
        values.put("minecraft:dragon_egg", 256.0);
        values.put("minecraft:netherite_upgrade_smithing_template", 12.0);
        return Map.copyOf(values);
    }
}
