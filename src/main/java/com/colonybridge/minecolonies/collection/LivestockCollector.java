package com.colonybridge.minecolonies.collection;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.model.*;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

import java.util.*;

public final class LivestockCollector {
    private final LivestockObservationCache cache = new LivestockObservationCache();

    public void clear() { cache.clear(); }

    public Result collect(ColonyCollectionContext context) {
        context.requireServerThread();
        if (context.trigger() == ExportTrigger.DISCONNECT || context.trigger() == ExportTrigger.SHUTDOWN) {
            Optional<LivestockData> retained = cache.get(context.colony().getID());
            if (retained.isPresent()) return new Result(retained.get(), 0, 0);
        }
        int hutsConsidered = 0;
        int entitiesConsidered = 0;
        try {
            Map<String, Integer> byType = new TreeMap<>();
            Set<UUID> observed = new HashSet<>();
            Map<String, HutAccumulator> huts = new TreeMap<>();
            if (!(context.colony().getWorld() instanceof ServerLevel level)) {
                return new Result(new LivestockData(null, null, null, Map.of(), List.of()), 0, 0);
            }
            for (ICommonBuilding common : context.buildings()) {
                if (!(common instanceof IBuilding hut)) continue;
                List<AnimalHerdingModule> modules = hut.getModules(AnimalHerdingModule.class);
                if (modules.isEmpty()) continue;
                hutsConsidered++;
                List<Integer> workers = hut.getAllAssignedCitizen().stream().filter(Objects::nonNull)
                        .filter(citizen -> {
                            IJob<?> job = CollectionSupport.safe(citizen::getJob, null);
                            return job != null && Objects.equals(
                                    CollectionSupport.buildingId(CollectionSupport.safe(job::getWorkBuilding, null)),
                                    CollectionSupport.buildingId(hut));
                        }).map(ICitizenData::getId).sorted().toList();
                if (workers.isEmpty()) continue;
                String hutId = CollectionSupport.buildingId(hut);
                HutAccumulator accumulator = huts.computeIfAbsent(hutId, ignored -> new HutAccumulator(
                        hutId,
                        hut.getBuildingType() == null || hut.getBuildingType().getRegistryName() == null
                                ? null : hut.getBuildingType().getRegistryName().toString(),
                        CollectionSupport.readableBuildingName(hut, hut), CollectionSupport.pos(hut.getPosition()), workers));
                for (AnimalHerdingModule module : modules) {
                    for (Animal animal : WorldUtil.getEntitiesWithinBuilding(level, Animal.class, hut, module::isCompatible)) {
                        if (!observed.add(animal.getUUID())) continue;
                        entitiesConsidered++;
                        String type = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString();
                        byType.merge(type, 1, Integer::sum);
                        accumulator.add(type);
                    }
                }
            }
            List<LivestockHutData> hutData = huts.values().stream().filter(hut -> hut.total() > 0)
                    .map(HutAccumulator::toData).toList();
            int total = byType.values().stream().mapToInt(Integer::intValue).sum();
            LivestockData result = new LivestockData(total, total, 0, byType, hutData);
            cache.put(context.colony().getID(), result);
            return new Result(result, entitiesConsidered, hutsConsidered);
        } catch (Exception exception) {
            context.warnings().add(CollectionSupport.error("livestock", context.colonyId(), "LIVESTOCK_READ_FAILED", exception));
            return new Result(new LivestockData(null, null, null, Map.of(), List.of()), entitiesConsidered, hutsConsidered);
        }
    }

    public record Result(LivestockData data, int entitiesConsidered, int hutsConsidered) { }

    private static final class HutAccumulator {
        private final String buildingId;
        private final String buildingType;
        private final String name;
        private final PositionData position;
        private final List<Integer> workerIds;
        private final Map<String, Integer> byType = new TreeMap<>();

        private HutAccumulator(String buildingId, String buildingType, String name, PositionData position,
                               List<Integer> workerIds) {
            this.buildingId = buildingId;
            this.buildingType = buildingType;
            this.name = name;
            this.position = position;
            this.workerIds = workerIds;
        }

        private void add(String type) { byType.merge(type, 1, Integer::sum); }
        private int total() { return byType.values().stream().mapToInt(Integer::intValue).sum(); }
        private LivestockHutData toData() {
            return new LivestockHutData(buildingId, buildingType, name, position, workerIds, total(), byType);
        }
    }
}
