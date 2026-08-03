package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.*;
import com.colonybridge.utility.DefenseStatisticsCalculator;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.*;

public final class DefenseCollector {
    private static final int MAX_TYPES_PER_BUILDING = 256;

    public Result collect(ColonyCollectionContext context,
                          ResearchStatisticsCollector.StatisticsCollection statistics) {
        context.requireServerThread();
        int windowDays = statistics.recent().windowDays();
        Map<String, Integer> lifetime = new TreeMap<>(), today = new TreeMap<>(), recent = new TreeMap<>();
        int butchered = 0, butcheredToday = 0, butcheredRecent = 0;
        int buildingsConsidered = 0;
        Set<IBuilding> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ICommonBuilding common : context.buildings()) {
            if (!(common instanceof IBuilding building) || !seen.add(building)) continue;
            buildingsConsidered++;
            try {
                if (!building.hasModule(BuildingModules.STATS_MODULE)) continue;
                BuildingStatisticsModule module = building.getModule(BuildingModules.STATS_MODULE);
                if (module == null) continue;
                var manager = module.getBuildingStatisticsManager();
                List<String> types = manager.getStatTypes().stream()
                        .filter(type -> "animals_butchered".equals(type) || type.startsWith("mob_killed;"))
                        .sorted().toList();
                int exported = Math.min(types.size(), MAX_TYPES_PER_BUILDING);
                for (int index = 0; index < exported; index++) {
                    String type = types.get(index);
                    if (type.startsWith("mob_killed;") && type.length() > "mob_killed;".length()) {
                        String entity = type.substring("mob_killed;".length());
                        lifetime.merge(entity, manager.getStatTotal(type), Integer::sum);
                        if (context.currentDay() != null) {
                            today.merge(entity, manager.getStatsInPeriod(type, context.currentDay(), context.currentDay()), Integer::sum);
                            recent.merge(entity, manager.getStatsInPeriod(type,
                                    Math.max(0, context.currentDay() - windowDays + 1), context.currentDay()), Integer::sum);
                        }
                    } else if ("animals_butchered".equals(type)) {
                        butchered += manager.getStatTotal(type);
                        if (context.currentDay() != null) {
                            butcheredToday += manager.getStatsInPeriod(type, context.currentDay(), context.currentDay());
                            butcheredRecent += manager.getStatsInPeriod(type,
                                    Math.max(0, context.currentDay() - windowDays + 1), context.currentDay());
                        }
                    }
                }
                if (types.size() > MAX_TYPES_PER_BUILDING) context.warnings().add(new BridgeMessage(
                        "defenseStatistics", CollectionSupport.buildingId(building), "DEFENSE_STATISTICS_TRUNCATED",
                        "Exported the first " + MAX_TYPES_PER_BUILDING + " of " + types.size() + " relevant building statistic types."));
            } catch (Exception exception) {
                context.warnings().add(CollectionSupport.error("defenseStatistics", CollectionSupport.buildingId(building),
                        "BUILDING_DEFENSE_STATISTICS_READ_FAILED", exception));
            }
        }
        warnIfDiverged(context, statistics, lifetime, today, recent);
        Map<String, String> categories = entityCategories();
        DefenseStatisticsData data = new DefenseStatisticsData(
                DefenseStatisticsCalculator.calculate(statistics.lifetime().get("mobs_killed"), lifetime, categories),
                DefenseStatisticsCalculator.calculate(statistics.recent().today().get("mobs_killed"), today, categories),
                DefenseStatisticsCalculator.calculate(statistics.recent().recentWindow().get("mobs_killed"), recent, categories),
                context.currentDay(), windowDays, butchered, butcheredToday, butcheredRecent);
        return new Result(data, buildingsConsidered);
    }

    public record Result(DefenseStatisticsData data, int buildingsConsidered) { }

    private void warnIfDiverged(ColonyCollectionContext context,
                                ResearchStatisticsCollector.StatisticsCollection statistics,
                                Map<String, Integer> lifetime, Map<String, Integer> today, Map<String, Integer> recent) {
        Map<String, Map<String, Integer>> periods = Map.of("lifetime", lifetime, "today", today, "recentWindow", recent);
        Map<String, Integer> totals = Map.of(
                "lifetime", statistics.lifetime().getOrDefault("mobs_killed", 0),
                "today", statistics.recent().today().getOrDefault("mobs_killed", 0),
                "recentWindow", statistics.recent().recentWindow().getOrDefault("mobs_killed", 0));
        for (Map.Entry<String, Map<String, Integer>> period : periods.entrySet()) {
            int detailed = period.getValue().values().stream().mapToInt(value -> Math.max(0, value)).sum();
            int authoritative = Math.max(0, totals.get(period.getKey()));
            if (detailed > authoritative) context.warnings().add(new BridgeMessage("defenseStatistics", context.colonyId(),
                    "GUARD_KILL_DETAIL_EXCEEDS_TOTAL", period.getKey() + " guard-kill detail totals " + detailed
                    + " but MineColonies reports " + authoritative + "."));
        }
    }

    private Map<String, String> entityCategories() {
        Set<EntityType<?>> raiders = Collections.newSetFromMap(new IdentityHashMap<>());
        raiders.addAll(ModEntities.getRaiders());
        Map<String, String> categories = new HashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String category = raiders.contains(type) ? DefenseStatisticsCalculator.MINECOLONIES_RAIDER
                    : type.getCategory() == MobCategory.MONSTER ? DefenseStatisticsCalculator.MONSTER_OR_HOSTILE
                    : knownNonHostile(type.getCategory()) ? DefenseStatisticsCalculator.NEUTRAL_OR_PEACEFUL
                    : DefenseStatisticsCalculator.UNCLASSIFIED;
            categories.merge(type.getDescriptionId(), category, this::moreSpecific);
        }
        return categories;
    }

    private boolean knownNonHostile(MobCategory category) {
        return category == MobCategory.CREATURE || category == MobCategory.AMBIENT || category == MobCategory.AXOLOTLS
                || category == MobCategory.WATER_AMBIENT || category == MobCategory.WATER_CREATURE
                || category == MobCategory.UNDERGROUND_WATER_CREATURE;
    }

    private String moreSpecific(String left, String right) {
        List<String> order = List.of(DefenseStatisticsCalculator.MINECOLONIES_RAIDER,
                DefenseStatisticsCalculator.MONSTER_OR_HOSTILE, DefenseStatisticsCalculator.NEUTRAL_OR_PEACEFUL,
                DefenseStatisticsCalculator.UNCLASSIFIED);
        return order.indexOf(left) <= order.indexOf(right) ? left : right;
    }
}
