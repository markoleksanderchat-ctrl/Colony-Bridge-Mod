package com.colonybridge.market;

public record ItemClassificationFacts(String itemId, String displayName, int quantity, int rarityClass,
                                      int maximumStackSize, boolean damageable, boolean food,
                                      boolean logs, boolean planks, boolean saplings, boolean swords,
                                      boolean axes, boolean bowEnchantable, boolean boats,
                                      double recipeComplexity) {
}
