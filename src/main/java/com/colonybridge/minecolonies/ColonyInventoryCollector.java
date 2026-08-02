package com.colonybridge.minecolonies;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.model.BridgeMessage;
import com.colonybridge.model.FoodSupplyData;
import com.colonybridge.model.StockLedgerData;
import com.colonybridge.utility.FoodRunwayAnalyzer;
import com.colonybridge.utility.StockRefreshPolicy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Owns the bounded inventory cache and derives the stock-ledger and food-supply views.
 * Keeping inventory traversal here makes the main MineColonies adapter an orchestrator
 * instead of mixing snapshot assembly with cache policy and item-handler scanning.
 */
final class ColonyInventoryCollector {
    private static final int REFRESH_INTERVAL_DAYS = 2;
    private static final int MAX_HANDLERS = 512;
    private static final int MAX_SLOTS = 16_384;
    private static final int MAX_EXPORTED_STOCK_TYPES = 512;
    private static final int MAX_EXPORTED_FOOD_TYPES = 16;
    private static final int MAX_MENU_ENTRIES = 256;

    private final Map<String, InventoryCacheEntry> cache = new HashMap<>();

    InventoryViews collect(
            IColony colony,
            List<ICommonBuilding> buildings,
            ExportTrigger trigger,
            Integer currentDay,
            int citizenCount,
            List<BridgeMessage> warnings
    ) {
        InventoryCacheEntry inventory = collectStock(colony, buildings, trigger, currentDay, warnings);
        return new InventoryViews(
                toStockLedger(inventory, currentDay),
                collectFoodSupply(colony, buildings, inventory, currentDay, citizenCount, warnings)
        );
    }

    private InventoryCacheEntry collectStock(
            IColony colony,
            List<ICommonBuilding> buildings,
            ExportTrigger trigger,
            Integer currentDay,
            List<BridgeMessage> warnings
    ) {
        String cacheKey = dimension(colony) + ":" + colony.getID();
        InventoryCacheEntry cached = cache.get(cacheKey);
        boolean startup = trigger == ExportTrigger.STARTUP;
        if (cached != null && !StockRefreshPolicy.shouldRefresh(
                currentDay,
                cached.refreshedColonyDay(),
                REFRESH_INTERVAL_DAYS,
                cached.createdAtStartup(),
                startup
        )) {
            return cached;
        }

        Map<String, Long> counts = new HashMap<>();
        Set<IItemHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Integer> handlersByBuildingType = new TreeMap<>();
        Map<String, Integer> slotsByBuildingType = new TreeMap<>();
        int scannedBuildings = 0;
        int scannedSlots = 0;
        boolean truncated = false;

        try {
            outer:
            for (ICommonBuilding common : buildings) {
                if (!(common instanceof IBuilding building)) {
                    continue;
                }
                scannedBuildings++;
                String buildingType = buildingType(common);
                List<IItemHandler> handlers = safe(building::getHandlers, List.of());
                for (IItemHandler handler : handlers) {
                    if (handler == null || !seenHandlers.add(handler)) {
                        continue;
                    }
                    if (seenHandlers.size() > MAX_HANDLERS) {
                        truncated = true;
                        break outer;
                    }

                    int slots = Math.max(0, handler.getSlots());
                    handlersByBuildingType.merge(buildingType, 1, Integer::sum);
                    slotsByBuildingType.merge(buildingType, slots, Integer::sum);
                    for (int slot = 0; slot < slots; slot++) {
                        if (scannedSlots >= MAX_SLOTS) {
                            truncated = true;
                            break outer;
                        }
                        scannedSlots++;
                        addStack(counts, handler.getStackInSlot(slot));
                    }
                }
            }
        } catch (Exception failure) {
            warnings.add(message(colony, "stockLedger", "STOCK_SCAN_FAILED", failure));
            if (cached != null) {
                warnings.add(new BridgeMessage(
                        "stockLedger",
                        String.valueOf(colony.getID()),
                        "USING_CACHED_STOCK",
                        "The scheduled stock refresh failed, so the previous inventory count was retained."
                ));
                return cached;
            }
            truncated = true;
        }

        if (truncated) {
            warnings.add(new BridgeMessage(
                    "stockLedger",
                    String.valueOf(colony.getID()),
                    "STOCK_SCAN_TRUNCATED",
                    "The two-day stock scan stopped at its bounded handler or slot limit."
            ));
        }

        InventoryCacheEntry refreshed = new InventoryCacheEntry(
                currentDay,
                Collections.unmodifiableMap(new TreeMap<>(counts)),
                scannedBuildings,
                seenHandlers.size(),
                scannedSlots,
                Collections.unmodifiableMap(new TreeMap<>(handlersByBuildingType)),
                Collections.unmodifiableMap(new TreeMap<>(slotsByBuildingType)),
                startup,
                truncated
        );
        cache.put(cacheKey, refreshed);
        return refreshed;
    }

    private StockLedgerData toStockLedger(InventoryCacheEntry inventory, Integer currentDay) {
        long total = inventory.counts().values().stream().mapToLong(Long::longValue).sum();
        Map<String, Integer> exported = topCounts(inventory.counts(), MAX_EXPORTED_STOCK_TYPES);
        return new StockLedgerData(
                currentDay,
                inventory.refreshedColonyDay(),
                StockRefreshPolicy.nextRefreshDay(inventory.refreshedColonyDay(), REFRESH_INTERVAL_DAYS),
                StockRefreshPolicy.ageDays(currentDay, inventory.refreshedColonyDay()),
                REFRESH_INTERVAL_DAYS,
                boundedInt(total),
                inventory.counts().size(),
                exported.size(),
                Math.max(0, inventory.counts().size() - exported.size()),
                exported,
                inventory.scannedBuildings(),
                inventory.scannedHandlers(),
                inventory.scannedSlots(),
                inventory.handlersByBuildingType(),
                inventory.slotsByBuildingType(),
                inventory.createdAtStartup(),
                inventory.truncated()
        );
    }

    private FoodSupplyData collectFoodSupply(
            IColony colony,
            List<ICommonBuilding> buildings,
            InventoryCacheEntry inventory,
            Integer currentDay,
            int citizenCount,
            List<BridgeMessage> warnings
    ) {
        Set<String> approvedMenuItems = new TreeSet<>();
        int diningHallsScanned = 0;
        int menuEntriesRead = 0;
        boolean menuTruncated = false;

        try {
            for (ICommonBuilding common : buildings) {
                if (!(common instanceof IBuilding building)) {
                    continue;
                }
                List<RestaurantMenuModule> menus = safe(
                        () -> building.getModules(RestaurantMenuModule.class),
                        List.of()
                );
                if (menus.isEmpty()) {
                    continue;
                }
                diningHallsScanned++;
                for (RestaurantMenuModule menu : menus) {
                    Set<ItemStorage> menuEntries = safe(menu::getMenu, Set.of());
                    for (ItemStorage entry : menuEntries) {
                        if (entry == null || entry.isEmpty()) {
                            continue;
                        }
                        if (menuEntriesRead >= MAX_MENU_ENTRIES) {
                            menuTruncated = true;
                            break;
                        }
                        menuEntriesRead++;
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(entry.getItem());
                        if (itemId != null) {
                            approvedMenuItems.add(itemId.toString());
                        }
                    }
                }
            }
        } catch (Exception failure) {
            warnings.add(message(colony, "foodSupply", "FOOD_MENU_READ_FAILED", failure));
        }

        Map<String, Long> foodCounts = new HashMap<>();
        for (String itemId : approvedMenuItems) {
            long count = inventory.counts().getOrDefault(itemId, 0L);
            if (count > 0) {
                foodCounts.put(itemId, count);
            }
        }

        long total = foodCounts.values().stream().mapToLong(Long::longValue).sum();
        int sampleDays = currentDay == null ? 0 : Math.min(7, Math.max(0, currentDay));
        int mealsToday = currentDay == null ? 0 : safe(() -> colony.getStatisticsManager()
                .getStatsInPeriod("food_served", currentDay, currentDay), 0);
        int sampleMeals = currentDay == null || sampleDays == 0 ? 0 : safe(() -> colony.getStatisticsManager()
                .getStatsInPeriod("food_served", currentDay - sampleDays, currentDay - 1), 0);

        return FoodRunwayAnalyzer.analyze(
                boundedInt(total),
                foodCounts.size(),
                topCounts(foodCounts, MAX_EXPORTED_FOOD_TYPES),
                mealsToday,
                sampleMeals,
                sampleDays,
                currentDay,
                citizenCount,
                diningHallsScanned,
                approvedMenuItems.size(),
                List.copyOf(approvedMenuItems),
                inventory.scannedBuildings(),
                inventory.scannedSlots(),
                menuTruncated || inventory.truncated()
        );
    }

    private static Map<String, Integer> topCounts(Map<String, Long> counts, int limit) {
        Map<String, Integer> result = new TreeMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(entry -> result.put(entry.getKey(), boundedInt(entry.getValue())));
        return result;
    }

    private static void addStack(Map<String, Long> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null) {
            counts.merge(itemId.toString(), (long) stack.getCount(), Long::sum);
        }
    }

    private static String buildingType(ICommonBuilding building) {
        if (building.getBuildingType() == null || building.getBuildingType().getRegistryName() == null) {
            return "unknown";
        }
        return building.getBuildingType().getRegistryName().toString();
    }

    private static String dimension(IColony colony) {
        return colony.getDimension() == null ? "unknown" : colony.getDimension().location().toString();
    }

    private static int boundedInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static BridgeMessage message(IColony colony, String scope, String code, Exception failure) {
        String detail = failure.getMessage();
        return new BridgeMessage(
                scope,
                String.valueOf(colony.getID()),
                code,
                failure.getClass().getSimpleName() + (detail == null ? "" : ": " + detail)
        );
    }

    private static <T> T safe(ThrowingSupplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    record InventoryViews(StockLedgerData stockLedger, FoodSupplyData foodSupply) {
    }

    private record InventoryCacheEntry(
            Integer refreshedColonyDay,
            Map<String, Long> counts,
            int scannedBuildings,
            int scannedHandlers,
            int scannedSlots,
            Map<String, Integer> handlersByBuildingType,
            Map<String, Integer> slotsByBuildingType,
            boolean createdAtStartup,
            boolean truncated
    ) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
