package com.colonybridge.minecolonies;

import com.colonybridge.ColonyBridgeConstants;
import com.colonybridge.api.AdapterStatus;
import com.colonybridge.api.ExportTrigger;
import com.colonybridge.api.MineColoniesAdapter;
import com.colonybridge.api.ServerLevelContext;
import com.colonybridge.config.BridgeConfigValues;
import com.colonybridge.model.*;
import com.colonybridge.utility.BuilderHutProgressCalculator;
import com.colonybridge.utility.SnapshotSummaryCalculator;
import com.colonybridge.utility.DefenseStatisticsCalculator;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IBuildingWorker;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule;
import com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MineColonies121Adapter implements MineColoniesAdapter {
    private static final int MAX_STATISTIC_TYPES = 256;
    private static final int MAX_DEFENSE_STATISTIC_TYPES_PER_BUILDING = 256;
    private static final Map<String, CapabilityData> CAPABILITIES = createCapabilities();
    private static final BuildingAccess EMPTY_BUILDING_ACCESS = new BuildingAccess(Set.of(), Map.of());
    private final AdapterStatus status;
    private final GameData gameData;
    private final ColonyInventoryCollector inventoryCollector = new ColonyInventoryCollector();
    private final Map<Integer, LivestockData> lastObservedLivestock = new HashMap<>();

    public MineColonies121Adapter(String mineColoniesVersion) {
        this.status = new AdapterStatus(true, "minecolonies-1.21.1-api", mineColoniesVersion, "MineColonies API adapter ready.");
        this.gameData = createGameData();
    }

    @Override
    public AdapterStatus status() {
        return status;
    }

    @Override
    public List<ColonySnapshot> collectAll(ServerLevelContext context, BridgeConfigValues config, ExportTrigger trigger, Instant generatedAt) {
        List<IColony> colonies = IColonyManager.getInstance().getAllColonies().stream()
                .sorted(Comparator.comparingInt(IColony::getID))
                .toList();
        List<ColonySnapshot> snapshots = new ArrayList<>(colonies.size());
        for (IColony colony : colonies) {
            snapshots.add(collectColony(context, config, trigger, generatedAt, colony));
        }
        return snapshots;
    }

    @Override
    public Optional<ColonySnapshot> collectById(ServerLevelContext context, BridgeConfigValues config, int colonyId, ExportTrigger trigger, Instant generatedAt) {
        return IColonyManager.getInstance().getAllColonies().stream()
                .filter(colony -> colony.getID() == colonyId)
                .findFirst()
                .map(colony -> collectColony(context, config, trigger, generatedAt, colony));
    }

    private ColonySnapshot collectColony(ServerLevelContext context, BridgeConfigValues config, ExportTrigger trigger, Instant generatedAt, IColony colony) {
        List<BridgeMessage> warnings = new ArrayList<>();
        List<BridgeMessage> errors = new ArrayList<>();
        List<ICitizenData> citizenSource = List.copyOf(colony.getCitizenManager().getCitizens());
        Collection<?> buildingCandidates = colony.getCommonBuildingManager().getBuildings().values();
        List<ICommonBuilding> buildingSource = new ArrayList<>(buildingCandidates.size());
        for (Object candidate : buildingCandidates) {
            if (candidate instanceof ICommonBuilding building) buildingSource.add(building);
        }
        List<IWorkOrder> workOrderSource = colony.getWorkManager().getWorkOrders().values().stream()
                .filter(IWorkOrder.class::isInstance).map(IWorkOrder.class::cast).toList();
        Integer currentDay = safe(colony::getDay, null);
        Map<ICommonBuilding, BuildingAccess> buildingAccess = captureBuildingAccess(buildingSource);
        Map<Integer, IBuilding> inferredWorkplaces = inferredWorkplaces(buildingSource, buildingAccess);
        List<CitizenData> citizens = safeList("colony", String.valueOf(colony.getID()), "CITIZENS_FAILED", errors,
                () -> collectCitizens(colony, citizenSource, inferredWorkplaces, config, errors));
        List<BuildingData> buildings = safeList("colony", String.valueOf(colony.getID()), "BUILDINGS_FAILED", errors,
                () -> collectBuildings(colony, buildingSource, buildingAccess, citizens, errors));
        List<RequestData> requests = safeList("colony", String.valueOf(colony.getID()), "REQUESTS_FAILED", errors,
                () -> collectRequests(colony, citizenSource, buildingSource, buildingAccess, errors));
        List<ConstructionData> construction = safeList("colony", String.valueOf(colony.getID()), "CONSTRUCTION_FAILED", errors,
                () -> collectConstruction(colony, workOrderSource, buildingAccess, errors));

        ColonyData colonyData = collectColonyData(colony, config, errors);
        SummaryData summary = summarize(colony, citizens, buildings, requests, construction);
        EnvironmentData environment = collectEnvironment(context.server(), colony, buildings);
        TerritoryData territory = collectTerritory(colony, warnings);
        LivestockData livestock = collectLivestock(colony, buildingSource, trigger, warnings);
        ResearchData research = collectResearch(colony, errors);
        StatisticsCollection statistics = collectStatistics(colony, currentDay, warnings);
        DefenseStatisticsData defenseStatistics = collectDefenseStatistics(
                colony, buildingSource, currentDay, statistics, warnings);
        ColonyInventoryCollector.InventoryViews inventory = inventoryCollector.collect(
                colony, buildingSource, trigger, currentDay, summary.citizenCount(), warnings);
        WorldData world = worldData(context.server(), colony);

        return new ColonySnapshot(
                ColonyBridgeConstants.SCHEMA_VERSION,
                ColonyBridgeConstants.VERSION,
                DateTimeFormatter.ISO_INSTANT.format(generatedAt),
                trigger.jsonName(),
                null,
                gameData,
                world,
                colonyData,
                summary,
                citizens,
                buildings,
                requests,
                construction,
                environment,
                territory,
                livestock,
                research,
                statistics.lifetime(),
                statistics.recent(),
                defenseStatistics,
                inventory.foodSupply(),
                inventory.stockLedger(),
                CAPABILITIES,
                warnings,
                errors
        );
    }

    private ColonyData collectColonyData(IColony colony, BridgeConfigValues config, List<BridgeMessage> errors) {
        UUID ownerUuid = null;
        String ownerName = null;
        try {
            ownerUuid = config.includeOwnerUuid() ? colony.getPermissions().getOwner() : null;
            ownerName = colony.getPermissions().getOwnerName();
        } catch (Exception e) {
            errors.add(error("colony", String.valueOf(colony.getID()), "OWNER_READ_FAILED", e));
        }
        Integer maxCitizens = null;
        Integer citizenCount = null;
        try {
            maxCitizens = colony.getCitizenManager().getMaxCitizens();
            citizenCount = colony.getCitizenManager().getCurrentCitizenCount();
        } catch (Exception e) {
            errors.add(error("colony", String.valueOf(colony.getID()), "CITIZEN_COUNTS_FAILED", e));
        }
        Boolean raided = null;
        try {
            raided = colony.getRaiderManager().isRaided();
        } catch (Exception e) {
            errors.add(error("colony", String.valueOf(colony.getID()), "RAID_STATE_FAILED", e));
        }

        Map<String, Object> flags = new TreeMap<>();
        flags.put("day", safe(() -> colony.getDay(), null));
        flags.put("textureStyle", safe(colony::getTextureStyleId, null));
        flags.put("structurePack", safe(colony::getStructurePack, null));
        flags.put("nameStyle", safe(colony::getNameStyle, null));
        flags.put("colonyColor", safe(() -> colony.getTeamColonyColor().getName(), null));
        flags.put("lastContactHours", safe(colony::getLastContactInHours, null));
        flags.put("potentialCitizenCapacity", safe(() -> colony.getCitizenManager().getPotentialMaxCitizens(), null));
        flags.put("researchCitizenCapacityBonus", safe(() -> colony.getCitizenManager().maxCitizensFromResearch(), null));
        flags.put("waypointCount", safe(() -> colony.getWayPoints().size(), null));
        flags.put("graveCount", safe(() -> colony.getGraveManager().getGraves().size(), null));
        flags.put("memberCount", safe(() -> colony.getPermissions().getPlayers().size(), null));
        flags.put("raidLevel", safe(() -> colony.getRaiderManager().getColonyRaidLevel(), null));
        flags.put("raidDifficultyModifier", safe(() -> colony.getRaiderManager().getRaidDifficultyModifier(), null));
        flags.put("nightsSinceLastRaid", safe(() -> colony.getRaiderManager().getNightsSinceLastRaid(), null));
        flags.put("raidExpectedTonight", safe(() -> colony.getRaiderManager().willRaidTonight(), null));
        flags.put("raidsEnabled", safe(() -> colony.getRaiderManager().canHaveRaiderEvents(), null));
        flags.put("raidCanStart", safe(() -> colony.getRaiderManager().canRaid(), null));
        flags.put("spiesEnabled", safe(() -> colony.getRaiderManager().areSpiesEnabled(), null));
        flags.put("expectedRaiderCount", safe(() -> colony.getRaiderManager().calculateRaiderAmount(
                colony.getCitizenManager().getCurrentCitizenCount()), null));
        flags.put("lostCitizensToRaids", safe(() -> colony.getRaiderManager().getLostCitizen(), null));

        Object colonyState = safe(colony::getState, null);
        return new ColonyData(
                colony.getID(),
                safe(colony::getName, null),
                ownerUuid,
                ownerName,
                dimension(colony.getDimension()),
                pos(colony.getCenter()),
                safe(() -> colony.getTicketedChunks().size(), null),
                citizenCount,
                maxCitizens,
                safe(colony::getOverallHappiness, null),
                safe(colony::isActive, null),
                safe(colony::isColonyUnderAttack, null),
                raided,
                colonyState == null ? null : "ABANDONED".equalsIgnoreCase(String.valueOf(colonyState)),
                colonyState == null ? null : String.valueOf(colonyState),
                flags
        );
    }

    private List<CitizenData> collectCitizens(IColony colony, List<ICitizenData> source, Map<Integer, IBuilding> inferredWorkplaces,
                                              BridgeConfigValues config, List<BridgeMessage> errors) {
        List<CitizenData> citizens = new ArrayList<>();
        for (ICitizenData citizen : source) {
            try {
                citizens.add(citizenData(colony, citizen, inferredWorkplaces, config));
            } catch (Exception e) {
                errors.add(error("citizen", safe(() -> String.valueOf(citizen.getId()), "unknown"), "FIELD_READ_FAILED", e));
            }
        }
        citizens.sort(Comparator.comparing(CitizenData::id, Comparator.nullsLast(Integer::compareTo)));
        return citizens;
    }

    private CitizenData citizenData(IColony colony, ICitizenData citizen, Map<Integer, IBuilding> inferredWorkplaces, BridgeConfigValues config) {
        IJob<?> job = safe(citizen::getJob, null);
        String jobId = job == null || job.getJobRegistryEntry() == null ? null : stringValue(job.getJobRegistryEntry().getKey());
        String jobName = readableIdentifier(jobId);
        IBuilding work = safe(citizen::getWorkBuilding, null);
        if (work == null) {
            work = inferredWorkplaces.get(citizen.getId());
        }
        IBuilding home = safe(citizen::getHomeBuilding, null);
        Map<String, Integer> skills = new TreeMap<>();
        try {
            var skillHandler = citizen.getCitizenSkillHandler();
            for (Map.Entry<Skill, ?> entry : skillHandler.getSkills().entrySet()) {
                skills.put(entry.getKey().name().toLowerCase(Locale.ROOT), skillHandler.getLevel(entry.getKey()));
            }
        } catch (Exception ignored) {
            skills = Map.of();
        }
        var entity = citizen.getEntity();
        Double health = entity.map(value -> (double) value.getHealth()).orElse(null);
        Double maxHealth = entity.map(value -> (double) value.getMaxHealth()).orElse(null);
        Double happiness = safe(() -> citizen.getCitizenHappinessHandler().getHappiness(colony, citizen), null);
        Boolean sick = safe(() -> citizen.getCitizenDiseaseHandler().isSick(), null);
        Boolean injured = safe(() -> citizen.getCitizenDiseaseHandler().isHurt(), null);
        Map<String, Object> details = new TreeMap<>();
        details.put("paused", safe(citizen::isPaused, null));
        details.put("needsBetterFood", safe(citizen::needsBetterFood, null));
        details.put("bedPosition", pos(safe(citizen::getBedPos, null)));
        details.put("statusPosition", pos(safe(citizen::getStatusPosition, null)));
        details.put("homePosition", pos(safe(citizen::getHomePosition, null)));
        details.put("jobStatus", safe(() -> stringValue(citizen.getJobStatus()), null));
        details.put("jobNameTagDescription", job == null ? null : safe(job::getNameTagDescription, null));
        details.put("jobActionsDone", job == null ? null : safe(job::getActionsDone, null));
        details.put("jobIdling", job == null ? null : safe(job::isIdling, null));
        details.put("mourning", safe(() -> citizen.getCitizenMournHandler().isMourning(), null));
        details.put("partnerCitizenId", safe(() -> citizen.getPartner() == null ? null : citizen.getPartner().getId(), null));
        details.put("childrenCitizenIds", safe(citizen::getChildren, List.of()));
        details.put("siblingCitizenIds", safe(citizen::getSiblings, List.of()));
        var disease = safe(() -> citizen.getCitizenDiseaseHandler().getDisease(), null);
        details.put("diseaseId", disease == null ? null : disease.id().toString());
        details.put("diseaseName", disease == null ? null : disease.name().getString());
        details.put("hospitalized", safe(() -> citizen.getCitizenDiseaseHandler().sleepsAtHospital(), null));
        var foodStats = safe(() -> citizen.getCitizenFoodHandler().getFoodHappinessStats(), null);
        details.put("foodDiversity", foodStats == null ? null : foodStats.diversity());
        details.put("foodQuality", foodStats == null ? null : foodStats.quality());
        var lastEaten = safe(() -> citizen.getCitizenFoodHandler().getLastEaten(), null);
        details.put("lastEatenItem", lastEaten == null ? null : BuiltInRegistries.ITEM.getKey(lastEaten).toString());

        return new CitizenData(
                citizen.getId(),
                safe(citizen::getUUID, null),
                safe(citizen::getName, null),
                citizen.isChild() ? "child" : "adult",
                citizen.isFemale() ? "female" : "male",
                jobName,
                jobId,
                job != null && safe(job::isGuard, false),
                buildingId(work),
                buildingId(home),
                health,
                maxHealth,
                happiness,
                safe(citizen::getSaturation, null),
                citizenActivity(citizen),
                job != null,
                citizen.isChild(),
                true,
                sick,
                injured,
                safe(citizen::isAsleep, null),
                safe(citizen::isIdleAtJob, null),
                safe(citizen::isWorking, null),
                job != null && !safe(job::getAsyncRequests, List.of()).isEmpty(),
                skills,
                config.includeCitizenPositions() ? entity.map(value -> pos(value.blockPosition())).orElse(null) : null,
                config.includeCitizenPositions() ? pos(safe(citizen::getLastPosition, null)) : null,
                details
        );
    }

    private List<BuildingData> collectBuildings(IColony colony, List<ICommonBuilding> source,
                                                Map<ICommonBuilding, BuildingAccess> buildingAccess,
                                                List<CitizenData> citizens,
                                                List<BridgeMessage> errors) {
        List<BuildingData> buildings = new ArrayList<>();
        for (ICommonBuilding value : source) {
            try {
                buildings.add(buildingData(colony, value, buildingAccess.getOrDefault(value, EMPTY_BUILDING_ACCESS), citizens));
            } catch (Exception e) {
                errors.add(error("building", "unknown", "FIELD_READ_FAILED", e));
            }
        }
        buildings.sort(Comparator.comparing(BuildingData::id, Comparator.nullsLast(String::compareTo)));
        return buildings;
    }

    private BuildingData buildingData(IColony colony, ICommonBuilding common, BuildingAccess access, List<CitizenData> citizens) {
        IBuilding building = common instanceof IBuilding typed ? typed : null;
        Set<Integer> assigned = access.assignedCitizenIds();
        String id = buildingId(common);
        List<Integer> workplaceWorkers = citizens.stream()
                .filter(citizen -> Objects.equals(citizen.workplaceBuildingId(), id))
                .map(CitizenData::id)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        Set<String> requestIds = new TreeSet<>();
        if (building != null) {
            for (List<Object> open : access.openRequestsByCitizen().values()) {
                for (Object token : open) {
                    String requestId = tokenId(token);
                    if (requestId != null) requestIds.add(requestId);
                }
            }
        }
        String registryId = common.getBuildingType() == null || common.getBuildingType().getRegistryName() == null
                ? null
                : common.getBuildingType().getRegistryName().toString();
        Boolean built = building == null ? null : safe(building::isBuilt, null);
        Boolean pending = building == null ? null : safe(building::isPendingConstruction, null);
        Map<String, Object> state = new TreeMap<>();
        state.put("prestige", common.getPrestige());
        state.put("levelEquivalent", common.getBuildingLevelEquivalent());
        state.put("containerCount", safe(() -> common.getContainers().size(), null));
        if (building != null) {
            state.put("canAssignCitizens", safe(building::canAssignCitizens, null));
            state.put("claimRadius", safe(() -> building.getClaimRadius(common.getBuildingLevel()), null));
            state.put("requiredItemTypeCount", safe(() -> building.getRequiredItemsAndAmount().size(), null));
            state.put("guardBuildingNearby", safe(building::isGuardBuildingNear, null));
        }
        if (building instanceof IBuildingWorker worker) {
            state.put("jobName", safe(worker::getJobName, null));
            state.put("hiringMode", safe(() -> stringValue(worker.getHiringMode()), null));
            state.put("primarySkill", safe(() -> worker.getPrimarySkill().name().toLowerCase(Locale.ROOT), null));
            state.put("secondarySkill", safe(() -> worker.getSecondarySkill().name().toLowerCase(Locale.ROOT), null));
            state.put("maxEquipmentLevel", safe(worker::getMaxEquipmentLevel, null));
            state.put("worksInRain", safe(worker::canWorkDuringTheRain, null));
        }
        return new BuildingData(
                id,
                registryId,
                readableBuildingName(common, building),
                common.getBuildingLevel(),
                null,
                pos(common.getPosition()),
                dimension(colony.getDimension()),
                assigned.stream().sorted().toList(),
                workplaceWorkers,
                null,
                !workplaceWorkers.isEmpty(),
                built,
                pending,
                pending,
                built,
                null,
                null,
                List.copyOf(requestIds),
                state
        );
    }

    private List<RequestData> collectRequests(IColony colony, List<ICitizenData> citizens, List<ICommonBuilding> buildings,
                                              Map<ICommonBuilding, BuildingAccess> buildingAccess,
                                              List<BridgeMessage> errors) {
        Map<String, RequestData> requests = new TreeMap<>();
        for (ICitizenData citizen : citizens) {
            IJob<?> job = safe(citizen::getJob, null);
            if (job == null) {
                continue;
            }
            for (Object token : safe(job::getAsyncRequests, List.of())) {
                addRequest(colony, requests, citizen.getId(), null, token, errors);
            }
        }
        for (ICommonBuilding common : buildings) {
            IBuilding building = common instanceof IBuilding typed ? typed : null;
            if (building == null) {
                continue;
            }
            BuildingAccess access = buildingAccess.getOrDefault(common, EMPTY_BUILDING_ACCESS);
            for (Map.Entry<Integer, List<Object>> openRequests : access.openRequestsByCitizen().entrySet()) {
                Integer citizenId = openRequests.getKey();
                for (Object token : openRequests.getValue()) {
                    addRequest(colony, requests, citizenId, buildingId(common), token, errors);
                }
            }
        }
        return List.copyOf(requests.values());
    }

    private void addRequest(IColony colony, Map<String, RequestData> requests, Integer citizenId, String buildingId, Object tokenObject, List<BridgeMessage> errors) {
        String id = tokenId(tokenObject);
        if (id == null || requests.containsKey(id) || !(tokenObject instanceof IToken<?> token)) {
            return;
        }
        try {
            IRequest<?> request = colony.getRequestManager().getRequestForToken(token);
            if (request == null) {
                requests.put(id, unknownRequest(id, citizenId, buildingId));
                return;
            }
            requests.put(id, requestData(id, citizenId, buildingId, request));
        } catch (Exception e) {
            errors.add(error("request", id, "REQUEST_READ_FAILED", e));
            requests.put(id, unknownRequest(id, citizenId, buildingId));
        }
    }

    private RequestData requestData(String id, Integer citizenId, String buildingId, IRequest<?> request) {
        IRequestable requestable = request.getRequest();
        String itemId = null;
        String itemName = null;
        Integer requested = null;
        Integer remaining = null;
        List<ItemStack> deliveries = safe(() -> List.copyOf(request.getDeliveries()), List.of());
        if (requestable instanceof IDeliverable deliverable) {
            ItemStack stack = deliverable.getResult();
            if (!stack.isEmpty()) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                itemId = key == null ? null : key.toString();
                itemName = stack.getHoverName().getString();
            }
            requested = deliverable.getCount();
            int delivered = deliveries.stream().mapToInt(ItemStack::getCount).sum();
            remaining = Math.max(0, requested - delivered);
        }
        RequestState state = request.getState();
        Map<String, Object> details = new TreeMap<>();
        details.put("display", safe(() -> request.getShortDisplayString().getString(), null));
        details.put("description", safe(() -> request.getLongDisplayString().getString(), null));
        details.put("displayIcon", safe(() -> request.getDisplayIcon().toString(), null));
        details.put("strategy", safe(() -> stringValue(request.getStrategy()), null));
        details.put("deliverable", safe(request::canBeDelivered, null));
        details.put("deliveryStackCount", deliveries.size());
        return new RequestData(
                id,
                stringValue(request.getType()),
                citizenId,
                buildingId,
                itemId,
                itemName,
                requested,
                remaining,
                state == null ? null : state.name(),
                request.hasParent() ? tokenId(request.getParent()) : null,
                request.hasChildren() ? request.getChildren().stream().map(this::tokenId).filter(Objects::nonNull).toList() : List.of(),
                null,
                state == RequestState.COMPLETED,
                state == RequestState.CANCELLED,
                state == RequestState.ASSIGNED,
                state == RequestState.IN_PROGRESS,
                state == RequestState.CREATED,
                details
        );
    }

    private RequestData unknownRequest(String id, Integer citizenId, String buildingId) {
        return new RequestData(id, "unknown", citizenId, buildingId, null, null, null, null, null,
                null, List.of(), null, null, null, null, null, null, Map.of());
    }

    private List<ConstructionData> collectConstruction(IColony colony, List<IWorkOrder> workOrders,
                                                       Map<ICommonBuilding, BuildingAccess> buildingAccess,
                                                       List<BridgeMessage> errors) {
        List<ConstructionData> construction = new ArrayList<>();
        for (IWorkOrder workOrder : workOrders) {
            try {
                BlockPos location = workOrder.getLocation();
                String buildingId = buildingId(colony.getCommonBuildingManager().getBuilding(location));
                boolean claimed = safe(workOrder::isClaimed, false);
                BlockPos claimedBy = claimed ? safe(workOrder::getClaimedBy, null) : null;
                ICommonBuilding builderHut = claimedBy == null
                        ? null
                        : colony.getCommonBuildingManager().getBuilding(claimedBy);
                BuildingAccess builderAccess = buildingAccess.get(builderHut);
                Integer assignedBuilderId = (builderAccess == null
                        ? assignedCitizenIds(builderHut instanceof IBuilding typed ? typed : null)
                        : builderAccess.assignedCitizenIds()).stream().findFirst().orElse(null);
                Map<String, Object> details = new TreeMap<>();
                details.put("workOrderId", workOrder.getID());
                details.put("priority", workOrder.getPriority());
                details.put("displayName", safe(() -> workOrder.getDisplayName().getString(), null));
                details.put("translationKey", safe(workOrder::getTranslationKey, null));
                details.put("structurePack", safe(workOrder::getStructurePack, null));
                details.put("structurePath", safe(workOrder::getStructurePath, null));
                details.put("fileName", safe(workOrder::getFileName, null));
                details.put("rotationMirror", safe(() -> stringValue(workOrder.getRotationMirror()), null));
                details.put("location", pos(location));
                var bounds = safe(workOrder::getBoundingBox, null);
                if (bounds != null) {
                    details.put("bounds", Map.of(
                            "minX", bounds.minX, "minY", bounds.minY, "minZ", bounds.minZ,
                            "maxX", bounds.maxX, "maxY", bounds.maxY, "maxZ", bounds.maxZ));
                }
                Object stage = safe(workOrder::getStage, null);
                Double progress = builderHutProgress(builderHut, workOrder, details);
                construction.add(new ConstructionData(
                        buildingId,
                        workOrder.getWorkOrderType() == null ? "unknown" : workOrder.getWorkOrderType().name().toLowerCase(Locale.ROOT),
                        workOrder.getCurrentLevel(),
                        workOrder.getTargetLevel(),
                        assignedBuilderId,
                        blockId(claimedBy),
                        stage == null ? null : String.valueOf(stage),
                        null,
                        null,
                        null,
                        progress,
                        details
                ));
            } catch (Exception e) {
                errors.add(error("construction", "unknown", "WORK_ORDER_READ_FAILED", e));
            }
        }
        construction.sort(Comparator.comparingInt(item -> {
            Object workOrderId = item.details().get("workOrderId");
            return workOrderId instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
        }));
        return construction;
    }

    private Double builderHutProgress(ICommonBuilding builderHut, IWorkOrder workOrder, Map<String, Object> details) {
        if (!(builderHut instanceof IBuilding building) || !(workOrder instanceof IBuilderWorkOrder builderWorkOrder)) {
            return null;
        }

        List<BuildingResourcesModule> modules = safe(() -> building.getModules(BuildingResourcesModule.class), List.of());
        if (modules.isEmpty()) {
            return null;
        }

        BuildingResourcesModule resources = modules.getFirst();
        long remainingResources = safe(() -> resources.getNeededResources().values().stream()
                .mapToLong(resource -> Math.max(0, resource.getAmount()))
                .sum(), 0L);
        int totalResources = Math.max(0, safe(builderWorkOrder::getAmountOfResources, 0));
        int percent = BuilderHutProgressCalculator.percent(totalResources, remainingResources);

        details.put("progressSource", "builder_hut");
        details.put("progressPercent", percent);
        details.put("totalRequiredResources", totalResources);
        details.put("remainingRequiredResources", remainingResources);
        return percent / 100.0;
    }

    private SummaryData summarize(IColony colony, List<CitizenData> citizens, List<BuildingData> buildings, List<RequestData> requests, List<ConstructionData> construction) {
        Integer capacity = safe(() -> colony.getCitizenManager().getMaxCitizens(), null);
        return SnapshotSummaryCalculator.summarize(capacity, citizens, buildings, requests, construction);
    }

    private EnvironmentData collectEnvironment(MinecraftServer server, IColony colony, List<BuildingData> buildings) {
        ServerLevel level = server.getLevel(colony.getDimension());
        if (level == null) {
            return new EnvironmentData(null, List.of(), null, null, null, null, null, null, null);
        }

        Set<String> biomes = new TreeSet<>();
        String centerBiome = biomeId(level, colony.getCenter());
        if (centerBiome != null) {
            biomes.add(centerBiome);
        }
        buildings.stream()
                .map(BuildingData::position)
                .filter(Objects::nonNull)
                .limit(31)
                .map(position -> new BlockPos(position.x(), position.y(), position.z()))
                .map(position -> biomeId(level, position))
                .filter(Objects::nonNull)
                .forEach(biomes::add);

        long dayTime = level.getDayTime();
        return new EnvironmentData(
                centerBiome,
                List.copyOf(biomes),
                Math.floorDiv(dayTime, 24000L),
                Math.floorMod(dayTime, 24000L),
                level.getGameTime(),
                safe(level::getMoonPhase, null),
                safe(colony::isDay, null),
                level.isRaining(),
                level.isThundering()
        );
    }

    private TerritoryData collectTerritory(IColony colony, List<BridgeMessage> warnings) {
        List<ChunkPos> claims;
        try {
            claims = IColonyManager.getInstance().getClaimData(colony.getDimension()).entrySet().stream()
                    .filter(entry -> entry.getValue().getOwningColony() == colony.getID()
                            || entry.getValue().getStaticClaimColonies().contains(colony.getID()))
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z))
                    .toList();
        } catch (RuntimeException e) {
            claims = List.of();
            warnings.add(error("territory", String.valueOf(colony.getID()), "CLAIM_MAP_READ_FAILED", e));
        }
        if (claims.isEmpty()) {
            Integer ticketed = safe(() -> colony.getTicketedChunks().size(), null);
            return TerritoryData.estimatedFromTickets(safe(colony::getLoadedChunkCount, null), ticketed);
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ChunkPos claim : claims) {
            minX = Math.min(minX, claim.x);
            maxX = Math.max(maxX, claim.x);
            minZ = Math.min(minZ, claim.z);
            maxZ = Math.max(maxZ, claim.z);
        }
        return new TerritoryData(
                claims.size(),
                claims.size() * 256,
                minX,
                maxX,
                minZ,
                maxZ,
                maxX - minX + 1,
                maxZ - minZ + 1,
                safe(colony::getLoadedChunkCount, null),
                safe(() -> colony.getTicketedChunks().size(), null)
        );
    }

    private LivestockData collectLivestock(IColony colony, List<ICommonBuilding> buildings,
                                           ExportTrigger trigger, List<BridgeMessage> warnings) {
        if ((trigger == ExportTrigger.DISCONNECT || trigger == ExportTrigger.SHUTDOWN)
                && lastObservedLivestock.containsKey(colony.getID())) {
            return lastObservedLivestock.get(colony.getID());
        }
        try {
            Map<String, Integer> byType = new TreeMap<>();
            Set<UUID> observedAnimals = new HashSet<>();
            Map<String, LivestockHutAccumulator> huts = new TreeMap<>();
            if (!(colony.getWorld() instanceof ServerLevel level)) {
                return new LivestockData(null, null, null, Map.of(), List.of());
            }
            for (ICommonBuilding common : buildings) {
                if (!(common instanceof IBuilding hut)) {
                    continue;
                }
                List<AnimalHerdingModule> herdingModules = hut.getModules(AnimalHerdingModule.class);
                if (herdingModules.isEmpty()) {
                    continue;
                }
                List<Integer> workerIds = hut.getAllAssignedCitizen().stream()
                        .filter(Objects::nonNull)
                        .filter(citizen -> {
                            IJob<?> job = safe(citizen::getJob, null);
                            return job != null && Objects.equals(buildingId(safe(job::getWorkBuilding, null)), buildingId(hut));
                        })
                        .map(ICitizenData::getId)
                        .sorted()
                        .toList();
                if (workerIds.isEmpty()) {
                    continue;
                }
                String hutId = buildingId(hut);
                LivestockHutAccumulator accumulator = huts.computeIfAbsent(hutId, ignored -> new LivestockHutAccumulator(
                        hutId,
                        hut.getBuildingType() == null || hut.getBuildingType().getRegistryName() == null
                                ? null
                                : hut.getBuildingType().getRegistryName().toString(),
                        readableBuildingName(hut, hut),
                        pos(hut.getPosition()),
                        workerIds
                ));
                for (AnimalHerdingModule module : herdingModules) {
                    for (Animal animal : WorldUtil.getEntitiesWithinBuilding(
                            level, Animal.class, hut, module::isCompatible)) {
                        if (!observedAnimals.add(animal.getUUID())) {
                            continue;
                        }
                        String type = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString();
                        byType.merge(type, 1, Integer::sum);
                        accumulator.add(type);
                    }
                }
            }

            List<LivestockHutData> hutData = huts.values().stream()
                    .filter(hut -> hut.total() > 0)
                    .map(LivestockHutAccumulator::toData)
                    .toList();
            int total = byType.values().stream().mapToInt(Integer::intValue).sum();
            LivestockData result = new LivestockData(total, total, 0, byType, hutData);
            lastObservedLivestock.put(colony.getID(), result);
            return result;
        } catch (Exception e) {
            warnings.add(error("livestock", String.valueOf(colony.getID()), "LIVESTOCK_READ_FAILED", e));
            return new LivestockData(null, null, null, Map.of(), List.of());
        }
    }

    private static final class LivestockHutAccumulator {
        private final String buildingId;
        private final String buildingType;
        private final String name;
        private final PositionData position;
        private final List<Integer> workerIds;
        private final Map<String, Integer> byType = new TreeMap<>();

        private LivestockHutAccumulator(String buildingId, String buildingType, String name,
                                        PositionData position, List<Integer> workerIds) {
            this.buildingId = buildingId;
            this.buildingType = buildingType;
            this.name = name;
            this.position = position;
            this.workerIds = workerIds;
        }

        private void add(String type) {
            byType.merge(type, 1, Integer::sum);
        }

        private int total() {
            return byType.values().stream().mapToInt(Integer::intValue).sum();
        }

        private LivestockHutData toData() {
            return new LivestockHutData(buildingId, buildingType, name, position, workerIds, total(), byType);
        }
    }

    private ResearchData collectResearch(IColony colony, List<BridgeMessage> errors) {
        try {
            var tree = colony.getResearchManager().getResearchTree();
            List<ResourceLocation> completedResearch = List.copyOf(tree.getCompletedList());
            List<String> completed = completedResearch.stream().map(ResourceLocation::toString).sorted().toList();
            List<ResearchProjectData> inProgress = tree.getResearchInProgress().stream()
                    .map(project -> new ResearchProjectData(
                            project.getId().toString(),
                            project.getBranch().toString(),
                            project.getDepth(),
                            project.getProgress(),
                            stringValue(project.getState())))
                    .sorted(Comparator.comparing(ResearchProjectData::id))
                    .toList();
            Map<String, ResearchEffectData> effects = new TreeMap<>();
            for (ResourceLocation researchId : completedResearch) {
                for (IResearchEffect effect : IGlobalResearchTree.getInstance().getEffectsForResearch(researchId)) {
                    String effectId = effect.getId().toString();
                    effects.put(effectId, new ResearchEffectData(
                            effectId,
                            translationKey(effect.getName()),
                            translationKey(effect.getSubtitle()),
                            safe(() -> colony.getResearchManager().getResearchEffects().getEffectStrength(effect.getId()), null)
                    ));
                }
            }
            return new ResearchData(completed, inProgress, List.copyOf(effects.values()));
        } catch (Exception e) {
            errors.add(error("research", String.valueOf(colony.getID()), "RESEARCH_READ_FAILED", e));
            return new ResearchData(List.of(), List.of(), List.of());
        }
    }

    private StatisticsCollection collectStatistics(IColony colony, Integer currentDay, List<BridgeMessage> warnings) {
        Map<String, Integer> lifetime = new TreeMap<>();
        Map<String, Integer> today = new TreeMap<>();
        Map<String, Integer> recentWindow = new TreeMap<>();
        int windowDays = 7;
        try {
            List<String> types = colony.getStatisticsManager().getStatTypes().stream().sorted().toList();
            int exportedTypes = Math.min(types.size(), MAX_STATISTIC_TYPES);
            for (int index = 0; index < exportedTypes; index++) {
                String type = types.get(index);
                lifetime.put(type, colony.getStatisticsManager().getStatTotal(type));
                if (currentDay != null) {
                    today.put(type, colony.getStatisticsManager().getStatsInPeriod(type, currentDay, currentDay));
                    recentWindow.put(type, colony.getStatisticsManager().getStatsInPeriod(
                            type, Math.max(0, currentDay - windowDays + 1), currentDay));
                }
            }
            if (types.size() > MAX_STATISTIC_TYPES) {
                warnings.add(new BridgeMessage("statistics", String.valueOf(colony.getID()), "STATISTICS_TRUNCATED",
                        "Exported the first " + MAX_STATISTIC_TYPES + " of " + types.size() + " statistic types."));
            }
        } catch (Exception e) {
            warnings.add(error("statistics", String.valueOf(colony.getID()), "STATISTICS_READ_FAILED", e));
        }
        return new StatisticsCollection(lifetime, new RecentStatisticsData(currentDay, windowDays, today, recentWindow));
    }

    private DefenseStatisticsData collectDefenseStatistics(
            IColony colony,
            List<ICommonBuilding> buildings,
            Integer currentDay,
            StatisticsCollection colonyStatistics,
            List<BridgeMessage> warnings) {
        int windowDays = colonyStatistics.recent().windowDays();
        Map<String, Integer> lifetime = new TreeMap<>();
        Map<String, Integer> today = new TreeMap<>();
        Map<String, Integer> recentWindow = new TreeMap<>();
        int animalsButchered = 0;
        int animalsButcheredToday = 0;
        int animalsButcheredRecentWindow = 0;
        Set<IBuilding> seenBuildings = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ICommonBuilding commonBuilding : buildings) {
            if (!(commonBuilding instanceof IBuilding building) || !seenBuildings.add(building)) continue;
            try {
                if (!building.hasModule(BuildingModules.STATS_MODULE)) continue;
                BuildingStatisticsModule module = building.getModule(BuildingModules.STATS_MODULE);
                if (module == null) continue;
                var manager = module.getBuildingStatisticsManager();
                List<String> statTypes = manager.getStatTypes().stream()
                        .filter(type -> "animals_butchered".equals(type) || type.startsWith("mob_killed;"))
                        .sorted()
                        .toList();
                int exportedTypes = Math.min(statTypes.size(), MAX_DEFENSE_STATISTIC_TYPES_PER_BUILDING);
                for (int index = 0; index < exportedTypes; index++) {
                    String statType = statTypes.get(index);
                    if (statType.startsWith("mob_killed;") && statType.length() > "mob_killed;".length()) {
                        String entityKey = statType.substring("mob_killed;".length());
                        lifetime.merge(entityKey, manager.getStatTotal(statType), Integer::sum);
                        if (currentDay != null) {
                            today.merge(entityKey, manager.getStatsInPeriod(statType, currentDay, currentDay), Integer::sum);
                            recentWindow.merge(entityKey, manager.getStatsInPeriod(
                                    statType, Math.max(0, currentDay - windowDays + 1), currentDay), Integer::sum);
                        }
                    } else if ("animals_butchered".equals(statType)) {
                        animalsButchered += manager.getStatTotal(statType);
                        if (currentDay != null) {
                            animalsButcheredToday += manager.getStatsInPeriod(statType, currentDay, currentDay);
                            animalsButcheredRecentWindow += manager.getStatsInPeriod(
                                    statType, Math.max(0, currentDay - windowDays + 1), currentDay);
                        }
                    }
                }
                if (statTypes.size() > MAX_DEFENSE_STATISTIC_TYPES_PER_BUILDING) {
                    warnings.add(new BridgeMessage("defenseStatistics", buildingId(building), "DEFENSE_STATISTICS_TRUNCATED",
                            "Exported the first " + MAX_DEFENSE_STATISTIC_TYPES_PER_BUILDING + " of "
                                    + statTypes.size() + " relevant building statistic types."));
                }
            } catch (Exception e) {
                warnings.add(error("defenseStatistics", buildingId(building), "BUILDING_DEFENSE_STATISTICS_READ_FAILED", e));
            }
        }

        Map<String, String> categories = defenseEntityCategories();
        warnIfDefenseTotalsDiverge(colony, colonyStatistics, lifetime, today, recentWindow, warnings);
        return new DefenseStatisticsData(
                DefenseStatisticsCalculator.calculate(colonyStatistics.lifetime().get("mobs_killed"), lifetime, categories),
                DefenseStatisticsCalculator.calculate(colonyStatistics.recent().today().get("mobs_killed"), today, categories),
                DefenseStatisticsCalculator.calculate(colonyStatistics.recent().recentWindow().get("mobs_killed"), recentWindow, categories),
                currentDay,
                windowDays,
                animalsButchered,
                animalsButcheredToday,
                animalsButcheredRecentWindow);
    }

    private void warnIfDefenseTotalsDiverge(
            IColony colony,
            StatisticsCollection colonyStatistics,
            Map<String, Integer> lifetime,
            Map<String, Integer> today,
            Map<String, Integer> recentWindow,
            List<BridgeMessage> warnings) {
        Map<String, Map<String, Integer>> periods = Map.of(
                "lifetime", lifetime,
                "today", today,
                "recentWindow", recentWindow);
        Map<String, Integer> totals = Map.of(
                "lifetime", colonyStatistics.lifetime().getOrDefault("mobs_killed", 0),
                "today", colonyStatistics.recent().today().getOrDefault("mobs_killed", 0),
                "recentWindow", colonyStatistics.recent().recentWindow().getOrDefault("mobs_killed", 0));
        for (Map.Entry<String, Map<String, Integer>> period : periods.entrySet()) {
            int detailedTotal = period.getValue().values().stream().mapToInt(value -> Math.max(0, value)).sum();
            int authoritativeTotal = Math.max(0, totals.get(period.getKey()));
            if (detailedTotal > authoritativeTotal) {
                warnings.add(new BridgeMessage("defenseStatistics", String.valueOf(colony.getID()),
                        "GUARD_KILL_DETAIL_EXCEEDS_TOTAL",
                        period.getKey() + " guard-kill detail totals " + detailedTotal
                                + " but MineColonies reports " + authoritativeTotal + "."));
            }
        }
    }

    private Map<String, String> defenseEntityCategories() {
        Set<EntityType<?>> mineColoniesRaiders = Collections.newSetFromMap(new IdentityHashMap<>());
        mineColoniesRaiders.addAll(ModEntities.getRaiders());
        Map<String, String> categories = new HashMap<>();
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            String category = mineColoniesRaiders.contains(entityType)
                    ? DefenseStatisticsCalculator.MINECOLONIES_RAIDER
                    : entityType.getCategory() == MobCategory.MONSTER
                    ? DefenseStatisticsCalculator.MONSTER_OR_HOSTILE
                    : isKnownNonHostileCategory(entityType.getCategory())
                    ? DefenseStatisticsCalculator.NEUTRAL_OR_PEACEFUL
                    : DefenseStatisticsCalculator.UNCLASSIFIED;
            categories.merge(entityType.getDescriptionId(), category, this::moreSpecificDefenseCategory);
        }
        return categories;
    }

    private boolean isKnownNonHostileCategory(MobCategory category) {
        return category == MobCategory.CREATURE
                || category == MobCategory.AMBIENT
                || category == MobCategory.AXOLOTLS
                || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.WATER_CREATURE
                || category == MobCategory.UNDERGROUND_WATER_CREATURE;
    }

    private String moreSpecificDefenseCategory(String left, String right) {
        List<String> precedence = List.of(
                DefenseStatisticsCalculator.MINECOLONIES_RAIDER,
                DefenseStatisticsCalculator.MONSTER_OR_HOSTILE,
                DefenseStatisticsCalculator.NEUTRAL_OR_PEACEFUL,
                DefenseStatisticsCalculator.UNCLASSIFIED);
        return precedence.indexOf(left) <= precedence.indexOf(right) ? left : right;
    }

    private String translationKey(Object contents) {
        return contents instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    private record StatisticsCollection(Map<String, Integer> lifetime, RecentStatisticsData recent) {
    }

    private String biomeId(ServerLevel level, BlockPos position) {
        return position == null ? null : level.getBiome(position).unwrapKey()
                .map(key -> key.location().toString())
                .orElse(null);
    }

    private static Map<String, CapabilityData> createCapabilities() {
        Map<String, CapabilityData> map = new TreeMap<>();
        map.put("colonies", CapabilityData.available());
        map.put("citizens", CapabilityData.available());
        map.put("buildings", CapabilityData.available());
        map.put("requests", CapabilityData.available());
        map.put("construction", CapabilityData.available());
        map.put("environment", CapabilityData.available());
        map.put("territory", CapabilityData.available());
        map.put("livestock", CapabilityData.available());
        map.put("raidForecast", CapabilityData.available());
        map.put("medicalCare", CapabilityData.available());
        map.put("research", CapabilityData.available());
        map.put("researchEffects", CapabilityData.available());
        map.put("statistics", CapabilityData.available());
        map.put("recentStatistics", CapabilityData.available());
        map.put("defenseStatistics", CapabilityData.available());
        map.put("foodSupply", CapabilityData.available());
        map.put("stockLedger", CapabilityData.available());
        map.put("constructionMetadata", CapabilityData.available());
        map.put("constructionMaterials", CapabilityData.unsupported("Not currently exposed by the read-only bridge"));
        map.put("warehouseInventoryTotals", CapabilityData.unsupported("The stock ledger covers known colony building storage, not a warehouse-only authoritative inventory"));
        return Collections.unmodifiableMap(map);
    }

    private GameData createGameData() {
        return new GameData(
                SharedConstants.getCurrentVersion().getName(),
                "NeoForge",
                ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse(FMLLoader.versionInfo().neoForgeVersion()),
                status.mineColoniesVersion()
        );
    }

    private WorldData worldData(MinecraftServer server, IColony colony) {
        String saveName = safe(() -> server.getWorldData().getLevelName(), null);
        String worldId = safe(() -> server.getServerDirectory().toAbsolutePath().normalize().toString(), null);
        return new WorldData(saveName, worldId, dimension(colony.getDimension()), server.isDedicatedServer());
    }

    private Set<Integer> assignedCitizenIds(IBuilding building) {
        if (building == null) {
            return Set.of();
        }
        Set<Integer> ids = new TreeSet<>();
        for (Object assigned : safe(building::getAllAssignedCitizen, Set.of())) {
            if (assigned instanceof ICitizenData citizen) {
                ids.add(citizen.getId());
            } else if (assigned instanceof Integer id) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<ICommonBuilding, BuildingAccess> captureBuildingAccess(List<ICommonBuilding> buildings) {
        Map<ICommonBuilding, BuildingAccess> result = new IdentityHashMap<>();
        for (ICommonBuilding common : buildings) {
            if (!(common instanceof IBuilding building)) {
                result.put(common, EMPTY_BUILDING_ACCESS);
                continue;
            }
            Set<Integer> assigned = assignedCitizenIds(building);
            Map<Integer, List<Object>> openRequests = new TreeMap<>();
            for (Integer citizenId : assigned) {
                Collection<?> source = safe(() -> building.getOpenRequests(citizenId), List.of());
                if (!source.isEmpty()) {
                    openRequests.put(citizenId, List.copyOf(source));
                }
            }
            result.put(common, new BuildingAccess(assigned, Collections.unmodifiableMap(openRequests)));
        }
        return result;
    }

    private Map<Integer, IBuilding> inferredWorkplaces(List<ICommonBuilding> buildings,
                                                       Map<ICommonBuilding, BuildingAccess> buildingAccess) {
        Map<Integer, IBuilding> result = new HashMap<>();
        for (ICommonBuilding common : buildings) {
            if (!(common instanceof IBuilding building)) {
                continue;
            }
            for (Integer citizenId : buildingAccess.getOrDefault(common, EMPTY_BUILDING_ACCESS).assignedCitizenIds()) {
                result.putIfAbsent(citizenId, building);
            }
        }
        return result;
    }

    private String readableBuildingName(ICommonBuilding common, IBuilding building) {
        String custom = building == null ? null : safe(building::getCustomName, null);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        String display = building == null ? null : safe(building::getBuildingDisplayName, null);
        if (display != null && !display.isBlank()) {
            return display;
        }
        return common.getBuildingType() == null ? "unknown" : common.getBuildingType().getTranslationKey();
    }

    private static String readableIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        int namespaceSeparator = identifier.indexOf(':');
        String path = namespaceSeparator >= 0 ? identifier.substring(namespaceSeparator + 1) : identifier;
        String[] words = path.replace('-', '_').split("_+");
        StringJoiner result = new StringJoiner(" ");
        for (String word : words) {
            if (!word.isBlank()) {
                result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.length() == 0 ? identifier : result.toString();
    }

    private String citizenActivity(ICitizenData citizen) {
        Object status = safe(citizen::getStatus, null);
        if (status == null) {
            return null;
        }
        if (status instanceof com.minecolonies.api.entity.citizen.VisibleCitizenStatus visible) {
            return visible.getTranslationKey();
        }
        return String.valueOf(status);
    }

    private String buildingId(ICommonBuilding building) {
        return building == null ? null : blockId(building.getPosition());
    }

    private String blockId(BlockPos pos) {
        return pos == null ? null : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String tokenId(Object token) {
        if (token instanceof IRequest<?> request) {
            return tokenId(request.getId());
        }
        if (token instanceof IToken<?> typed) {
            Object id = typed.getIdentifier();
            return id == null ? typed.toString() : String.valueOf(id);
        }
        return token == null ? null : String.valueOf(token);
    }

    private String dimension(ResourceKey<Level> key) {
        return key == null ? null : key.location().toString();
    }

    private PositionData pos(BlockPos pos) {
        return pos == null ? null : new PositionData(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BridgeMessage error(String scope, String entityId, String code, Exception e) {
        String message = e.getMessage();
        return new BridgeMessage(scope, entityId, code, e.getClass().getSimpleName() + (message == null ? "" : ": " + message));
    }

    private static <T> T safe(ThrowingSupplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static <T> List<T> safeList(String scope, String entityId, String code, List<BridgeMessage> errors, ThrowingSupplier<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            errors.add(error(scope, entityId, code, e));
            return List.of();
        }
    }

    private record BuildingAccess(Set<Integer> assignedCitizenIds, Map<Integer, List<Object>> openRequestsByCitizen) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
