package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.*;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IBuildingWorker;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.Skill;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

public final class CitizenBuildingCollector {
    public Result collect(ColonyCollectionContext context) {
        context.requireServerThread();
        List<CitizenData> citizens = CollectionSupport.safeList("colony", context.colonyId(), "CITIZENS_FAILED",
                context.errors(), () -> collectCitizens(context));
        List<BuildingData> buildings = CollectionSupport.safeList("colony", context.colonyId(), "BUILDINGS_FAILED",
                context.errors(), () -> collectBuildings(context, citizens));
        return new Result(citizens, buildings);
    }

    private List<CitizenData> collectCitizens(ColonyCollectionContext context) {
        List<CitizenData> result = new ArrayList<>();
        for (ICitizenData citizen : context.citizens()) {
            try {
                result.add(citizenData(context, citizen));
            } catch (Exception exception) {
                context.errors().add(CollectionSupport.error("citizen",
                        CollectionSupport.safe(() -> String.valueOf(citizen.getId()), "unknown"),
                        "FIELD_READ_FAILED", exception));
            }
        }
        result.sort(Comparator.comparing(CitizenData::id, Comparator.nullsLast(Integer::compareTo)));
        return result;
    }

    private CitizenData citizenData(ColonyCollectionContext context, ICitizenData citizen) {
        IJob<?> job = CollectionSupport.safe(citizen::getJob, null);
        String jobId = job == null || job.getJobRegistryEntry() == null ? null
                : CollectionSupport.stringValue(job.getJobRegistryEntry().getKey());
        String jobName = CollectionSupport.readableIdentifier(jobId);
        IBuilding work = CollectionSupport.safe(citizen::getWorkBuilding, null);
        if (work == null) work = context.inferredWorkplaces().get(citizen.getId());
        IBuilding home = CollectionSupport.safe(citizen::getHomeBuilding, null);
        Map<String, Integer> skills = new TreeMap<>();
        try {
            var handler = citizen.getCitizenSkillHandler();
            for (Map.Entry<Skill, ?> entry : handler.getSkills().entrySet()) {
                skills.put(entry.getKey().name().toLowerCase(Locale.ROOT), handler.getLevel(entry.getKey()));
            }
        } catch (Exception ignored) {
            skills = Map.of();
        }
        var entity = citizen.getEntity();
        Double health = entity.map(value -> (double) value.getHealth()).orElse(null);
        Double maxHealth = entity.map(value -> (double) value.getMaxHealth()).orElse(null);
        Double happiness = CollectionSupport.safe(() -> citizen.getCitizenHappinessHandler().getHappiness(context.colony(), citizen), null);
        Boolean sick = CollectionSupport.safe(() -> citizen.getCitizenDiseaseHandler().isSick(), null);
        Boolean injured = CollectionSupport.safe(() -> citizen.getCitizenDiseaseHandler().isHurt(), null);
        Map<String, Object> details = new TreeMap<>();
        details.put("paused", CollectionSupport.safe(citizen::isPaused, null));
        details.put("needsBetterFood", CollectionSupport.safe(citizen::needsBetterFood, null));
        details.put("bedPosition", CollectionSupport.pos(CollectionSupport.safe(citizen::getBedPos, null)));
        details.put("statusPosition", CollectionSupport.pos(CollectionSupport.safe(citizen::getStatusPosition, null)));
        details.put("homePosition", CollectionSupport.pos(CollectionSupport.safe(citizen::getHomePosition, null)));
        details.put("jobStatus", CollectionSupport.safe(() -> CollectionSupport.stringValue(citizen.getJobStatus()), null));
        details.put("jobNameTagDescription", job == null ? null : CollectionSupport.safe(job::getNameTagDescription, null));
        details.put("jobActionsDone", job == null ? null : CollectionSupport.safe(job::getActionsDone, null));
        details.put("jobIdling", job == null ? null : CollectionSupport.safe(job::isIdling, null));
        details.put("mourning", CollectionSupport.safe(() -> citizen.getCitizenMournHandler().isMourning(), null));
        details.put("partnerCitizenId", CollectionSupport.safe(() -> citizen.getPartner() == null ? null : citizen.getPartner().getId(), null));
        details.put("childrenCitizenIds", CollectionSupport.safe(citizen::getChildren, List.of()));
        details.put("siblingCitizenIds", CollectionSupport.safe(citizen::getSiblings, List.of()));
        var disease = CollectionSupport.safe(() -> citizen.getCitizenDiseaseHandler().getDisease(), null);
        details.put("diseaseId", disease == null ? null : disease.id().toString());
        details.put("diseaseName", disease == null ? null : disease.name().getString());
        details.put("hospitalized", CollectionSupport.safe(() -> citizen.getCitizenDiseaseHandler().sleepsAtHospital(), null));
        var foodStats = CollectionSupport.safe(() -> citizen.getCitizenFoodHandler().getFoodHappinessStats(), null);
        details.put("foodDiversity", foodStats == null ? null : foodStats.diversity());
        details.put("foodQuality", foodStats == null ? null : foodStats.quality());
        var lastEaten = CollectionSupport.safe(() -> citizen.getCitizenFoodHandler().getLastEaten(), null);
        details.put("lastEatenItem", lastEaten == null ? null : BuiltInRegistries.ITEM.getKey(lastEaten).toString());
        return new CitizenData(
                citizen.getId(), CollectionSupport.safe(citizen::getUUID, null), CollectionSupport.safe(citizen::getName, null),
                citizen.isChild() ? "child" : "adult", citizen.isFemale() ? "female" : "male", jobName, jobId,
                job != null && CollectionSupport.safe(job::isGuard, false), CollectionSupport.buildingId(work),
                CollectionSupport.buildingId(home), health, maxHealth, happiness,
                CollectionSupport.safe(citizen::getSaturation, null), citizenActivity(citizen), job != null,
                citizen.isChild(), true, sick, injured, CollectionSupport.safe(citizen::isAsleep, null),
                CollectionSupport.safe(citizen::isIdleAtJob, null), CollectionSupport.safe(citizen::isWorking, null),
                job != null && !CollectionSupport.safe(job::getAsyncRequests, List.of()).isEmpty(), skills,
                context.config().includeCitizenPositions() ? entity.map(value -> CollectionSupport.pos(value.blockPosition())).orElse(null) : null,
                context.config().includeCitizenPositions() ? CollectionSupport.pos(CollectionSupport.safe(citizen::getLastPosition, null)) : null,
                details);
    }

    private List<BuildingData> collectBuildings(ColonyCollectionContext context, List<CitizenData> citizens) {
        List<BuildingData> result = new ArrayList<>();
        for (ICommonBuilding common : context.buildings()) {
            try {
                result.add(buildingData(context, common,
                        context.buildingAccess().getOrDefault(common, CollectionSupport.EMPTY_BUILDING_ACCESS), citizens));
            } catch (Exception exception) {
                context.errors().add(CollectionSupport.error("building", "unknown", "FIELD_READ_FAILED", exception));
            }
        }
        result.sort(Comparator.comparing(BuildingData::id, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    private BuildingData buildingData(ColonyCollectionContext context, ICommonBuilding common,
                                      CollectionSupport.BuildingAccess access, List<CitizenData> citizens) {
        IBuilding building = common instanceof IBuilding typed ? typed : null;
        String id = CollectionSupport.buildingId(common);
        List<Integer> workplaceWorkers = citizens.stream()
                .filter(citizen -> Objects.equals(citizen.workplaceBuildingId(), id))
                .map(CitizenData::id).filter(Objects::nonNull).sorted().toList();
        Set<String> requestIds = new TreeSet<>();
        if (building != null) {
            for (List<Object> open : access.openRequestsByCitizen().values()) {
                for (Object token : open) {
                    String requestId = CollectionSupport.tokenId(token);
                    if (requestId != null) requestIds.add(requestId);
                }
            }
        }
        String registryId = common.getBuildingType() == null || common.getBuildingType().getRegistryName() == null
                ? null : common.getBuildingType().getRegistryName().toString();
        Boolean built = building == null ? null : CollectionSupport.safe(building::isBuilt, null);
        Boolean pending = building == null ? null : CollectionSupport.safe(building::isPendingConstruction, null);
        Map<String, Object> state = new TreeMap<>();
        state.put("prestige", common.getPrestige());
        state.put("levelEquivalent", common.getBuildingLevelEquivalent());
        state.put("containerCount", CollectionSupport.safe(() -> common.getContainers().size(), null));
        if (building != null) {
            state.put("canAssignCitizens", CollectionSupport.safe(building::canAssignCitizens, null));
            state.put("claimRadius", CollectionSupport.safe(() -> building.getClaimRadius(common.getBuildingLevel()), null));
            state.put("requiredItemTypeCount", CollectionSupport.safe(() -> building.getRequiredItemsAndAmount().size(), null));
            state.put("guardBuildingNearby", CollectionSupport.safe(building::isGuardBuildingNear, null));
        }
        if (building instanceof IBuildingWorker worker) {
            state.put("jobName", CollectionSupport.safe(worker::getJobName, null));
            state.put("hiringMode", CollectionSupport.safe(() -> CollectionSupport.stringValue(worker.getHiringMode()), null));
            state.put("primarySkill", CollectionSupport.safe(() -> worker.getPrimarySkill().name().toLowerCase(Locale.ROOT), null));
            state.put("secondarySkill", CollectionSupport.safe(() -> worker.getSecondarySkill().name().toLowerCase(Locale.ROOT), null));
            state.put("maxEquipmentLevel", CollectionSupport.safe(worker::getMaxEquipmentLevel, null));
            state.put("worksInRain", CollectionSupport.safe(worker::canWorkDuringTheRain, null));
        }
        return new BuildingData(id, registryId, CollectionSupport.readableBuildingName(common, building),
                common.getBuildingLevel(), null, CollectionSupport.pos(common.getPosition()),
                CollectionSupport.dimension(context.colony().getDimension()), access.assignedCitizenIds().stream().sorted().toList(),
                workplaceWorkers, null, !workplaceWorkers.isEmpty(), built, pending, pending, built,
                null, null, List.copyOf(requestIds), state);
    }

    private String citizenActivity(ICitizenData citizen) {
        Object status = CollectionSupport.safe(citizen::getStatus, null);
        if (status == null) return null;
        if (status instanceof com.minecolonies.api.entity.citizen.VisibleCitizenStatus visible) return visible.getTranslationKey();
        return String.valueOf(status);
    }

    public record Result(List<CitizenData> citizens, List<BuildingData> buildings) {
    }
}
