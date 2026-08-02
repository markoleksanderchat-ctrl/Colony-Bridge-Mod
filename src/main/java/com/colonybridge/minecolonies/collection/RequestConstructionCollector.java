package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.*;
import com.colonybridge.utility.BuilderHutProgressCalculator;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class RequestConstructionCollector {
    public Result collect(ColonyCollectionContext context) {
        context.requireServerThread();
        List<RequestData> requests = CollectionSupport.safeList("colony", context.colonyId(), "REQUESTS_FAILED",
                context.errors(), () -> collectRequests(context));
        List<ConstructionData> construction = CollectionSupport.safeList("colony", context.colonyId(), "CONSTRUCTION_FAILED",
                context.errors(), () -> collectConstruction(context));
        return new Result(requests, construction);
    }

    private List<RequestData> collectRequests(ColonyCollectionContext context) {
        Map<String, RequestData> requests = new TreeMap<>();
        for (ICitizenData citizen : context.citizens()) {
            IJob<?> job = CollectionSupport.safe(citizen::getJob, null);
            if (job == null) continue;
            for (Object token : CollectionSupport.safe(job::getAsyncRequests, List.of())) {
                addRequest(context, requests, citizen.getId(), null, token);
            }
        }
        for (ICommonBuilding common : context.buildings()) {
            if (!(common instanceof IBuilding)) continue;
            CollectionSupport.BuildingAccess access = context.buildingAccess()
                    .getOrDefault(common, CollectionSupport.EMPTY_BUILDING_ACCESS);
            for (Map.Entry<Integer, List<Object>> open : access.openRequestsByCitizen().entrySet()) {
                for (Object token : open.getValue()) {
                    addRequest(context, requests, open.getKey(), CollectionSupport.buildingId(common), token);
                }
            }
        }
        return List.copyOf(requests.values());
    }

    private void addRequest(ColonyCollectionContext context, Map<String, RequestData> requests,
                            Integer citizenId, String buildingId, Object tokenObject) {
        String id = CollectionSupport.tokenId(tokenObject);
        if (id == null || requests.containsKey(id) || !(tokenObject instanceof IToken<?> token)) return;
        try {
            IRequest<?> request = context.colony().getRequestManager().getRequestForToken(token);
            requests.put(id, request == null ? unknownRequest(id, citizenId, buildingId)
                    : requestData(id, citizenId, buildingId, request));
        } catch (Exception exception) {
            context.errors().add(CollectionSupport.error("request", id, "REQUEST_READ_FAILED", exception));
            requests.put(id, unknownRequest(id, citizenId, buildingId));
        }
    }

    private RequestData requestData(String id, Integer citizenId, String buildingId, IRequest<?> request) {
        IRequestable requestable = request.getRequest();
        String itemId = null;
        String itemName = null;
        Integer requested = null;
        Integer remaining = null;
        List<ItemStack> deliveries = CollectionSupport.safe(() -> List.copyOf(request.getDeliveries()), List.of());
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
        details.put("display", CollectionSupport.safe(() -> request.getShortDisplayString().getString(), null));
        details.put("description", CollectionSupport.safe(() -> request.getLongDisplayString().getString(), null));
        details.put("displayIcon", CollectionSupport.safe(() -> request.getDisplayIcon().toString(), null));
        details.put("strategy", CollectionSupport.safe(() -> CollectionSupport.stringValue(request.getStrategy()), null));
        details.put("deliverable", CollectionSupport.safe(request::canBeDelivered, null));
        details.put("deliveryStackCount", deliveries.size());
        return new RequestData(id, CollectionSupport.stringValue(request.getType()), citizenId, buildingId,
                itemId, itemName, requested, remaining, state == null ? null : state.name(),
                request.hasParent() ? CollectionSupport.tokenId(request.getParent()) : null,
                request.hasChildren() ? request.getChildren().stream().map(CollectionSupport::tokenId).filter(Objects::nonNull).toList() : List.of(),
                null, state == RequestState.COMPLETED, state == RequestState.CANCELLED,
                state == RequestState.ASSIGNED, state == RequestState.IN_PROGRESS, state == RequestState.CREATED, details);
    }

    private RequestData unknownRequest(String id, Integer citizenId, String buildingId) {
        return new RequestData(id, "unknown", citizenId, buildingId, null, null, null, null, null,
                null, List.of(), null, null, null, null, null, null, Map.of());
    }

    private List<ConstructionData> collectConstruction(ColonyCollectionContext context) {
        List<ConstructionData> result = new ArrayList<>();
        for (IWorkOrder workOrder : context.workOrders()) {
            try {
                BlockPos location = workOrder.getLocation();
                String buildingId = CollectionSupport.buildingId(context.colony().getCommonBuildingManager().getBuilding(location));
                boolean claimed = CollectionSupport.safe(workOrder::isClaimed, false);
                BlockPos claimedBy = claimed ? CollectionSupport.safe(workOrder::getClaimedBy, null) : null;
                ICommonBuilding builderHut = claimedBy == null ? null
                        : context.colony().getCommonBuildingManager().getBuilding(claimedBy);
                CollectionSupport.BuildingAccess access = context.buildingAccess().get(builderHut);
                Integer builderId = (access == null
                        ? CollectionSupport.assignedCitizenIds(builderHut instanceof IBuilding typed ? typed : null)
                        : access.assignedCitizenIds()).stream().findFirst().orElse(null);
                Map<String, Object> details = new TreeMap<>();
                details.put("workOrderId", workOrder.getID());
                details.put("priority", workOrder.getPriority());
                details.put("displayName", CollectionSupport.safe(() -> workOrder.getDisplayName().getString(), null));
                details.put("translationKey", CollectionSupport.safe(workOrder::getTranslationKey, null));
                details.put("structurePack", CollectionSupport.safe(workOrder::getStructurePack, null));
                details.put("structurePath", CollectionSupport.safe(workOrder::getStructurePath, null));
                details.put("fileName", CollectionSupport.safe(workOrder::getFileName, null));
                details.put("rotationMirror", CollectionSupport.safe(() -> CollectionSupport.stringValue(workOrder.getRotationMirror()), null));
                details.put("location", CollectionSupport.pos(location));
                var bounds = CollectionSupport.safe(workOrder::getBoundingBox, null);
                if (bounds != null) details.put("bounds", Map.of(
                        "minX", bounds.minX, "minY", bounds.minY, "minZ", bounds.minZ,
                        "maxX", bounds.maxX, "maxY", bounds.maxY, "maxZ", bounds.maxZ));
                Object stage = CollectionSupport.safe(workOrder::getStage, null);
                Double progress = builderHutProgress(builderHut, workOrder, details);
                result.add(new ConstructionData(buildingId,
                        workOrder.getWorkOrderType() == null ? "unknown" : workOrder.getWorkOrderType().name().toLowerCase(Locale.ROOT),
                        workOrder.getCurrentLevel(), workOrder.getTargetLevel(), builderId, CollectionSupport.blockId(claimedBy),
                        stage == null ? null : String.valueOf(stage), null, null, null, progress, details));
            } catch (Exception exception) {
                context.errors().add(CollectionSupport.error("construction", "unknown", "WORK_ORDER_READ_FAILED", exception));
            }
        }
        result.sort(Comparator.comparingInt(item -> {
            Object id = item.details().get("workOrderId");
            return id instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
        }));
        return result;
    }

    private Double builderHutProgress(ICommonBuilding builderHut, IWorkOrder workOrder, Map<String, Object> details) {
        if (!(builderHut instanceof IBuilding building) || !(workOrder instanceof IBuilderWorkOrder builderWorkOrder)) return null;
        List<BuildingResourcesModule> modules = CollectionSupport.safe(() -> building.getModules(BuildingResourcesModule.class), List.of());
        if (modules.isEmpty()) return null;
        BuildingResourcesModule resources = modules.getFirst();
        long remaining = CollectionSupport.safe(() -> resources.getNeededResources().values().stream()
                .mapToLong(resource -> Math.max(0, resource.getAmount())).sum(), 0L);
        int total = Math.max(0, CollectionSupport.safe(builderWorkOrder::getAmountOfResources, 0));
        int percent = BuilderHutProgressCalculator.percent(total, remaining);
        details.put("progressSource", "builder_hut");
        details.put("progressPercent", percent);
        details.put("totalRequiredResources", total);
        details.put("remainingRequiredResources", remaining);
        return percent / 100.0;
    }

    public record Result(List<RequestData> requests, List<ConstructionData> construction) {
    }
}
