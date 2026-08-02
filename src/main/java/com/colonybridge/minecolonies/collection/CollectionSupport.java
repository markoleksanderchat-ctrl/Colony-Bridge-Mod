package com.colonybridge.minecolonies.collection;

import com.colonybridge.model.BridgeMessage;
import com.colonybridge.model.PositionData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

final class CollectionSupport {
    static final BuildingAccess EMPTY_BUILDING_ACCESS = new BuildingAccess(Set.of(), Map.of());

    private CollectionSupport() {
    }

    static <T> T safe(ThrowingSupplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static <T> List<T> safeList(String scope, String entityId, String code, List<BridgeMessage> errors,
                                ThrowingSupplier<List<T>> supplier) {
        return CollectionIssues.safeList(scope, entityId, code, errors, supplier::get);
    }

    static BridgeMessage error(String scope, String entityId, String code, Exception exception) {
        return CollectionIssues.error(scope, entityId, code, exception);
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static PositionData pos(BlockPos position) {
        return position == null ? null : new PositionData(position.getX(), position.getY(), position.getZ());
    }

    static String blockId(BlockPos position) {
        return position == null ? null : position.getX() + "," + position.getY() + "," + position.getZ();
    }

    static String buildingId(ICommonBuilding building) {
        return building == null ? null : blockId(building.getPosition());
    }

    static String dimension(ResourceKey<Level> key) {
        return key == null ? null : key.location().toString();
    }

    static String tokenId(Object token) {
        if (token instanceof IRequest<?> request) return tokenId(request.getId());
        if (token instanceof IToken<?> typed) {
            Object id = typed.getIdentifier();
            return id == null ? typed.toString() : String.valueOf(id);
        }
        return token == null ? null : String.valueOf(token);
    }

    static Set<Integer> assignedCitizenIds(IBuilding building) {
        if (building == null) return Set.of();
        Set<Integer> ids = new TreeSet<>();
        for (Object assigned : safe(building::getAllAssignedCitizen, Set.of())) {
            if (assigned instanceof ICitizenData citizen) ids.add(citizen.getId());
            else if (assigned instanceof Integer id) ids.add(id);
        }
        return ids;
    }

    static Map<ICommonBuilding, BuildingAccess> captureBuildingAccess(List<ICommonBuilding> buildings) {
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
                if (!source.isEmpty()) openRequests.put(citizenId, List.copyOf(source));
            }
            result.put(common, new BuildingAccess(assigned, Collections.unmodifiableMap(openRequests)));
        }
        return result;
    }

    static Map<Integer, IBuilding> inferredWorkplaces(List<ICommonBuilding> buildings,
                                                       Map<ICommonBuilding, BuildingAccess> buildingAccess) {
        Map<Integer, IBuilding> result = new HashMap<>();
        for (ICommonBuilding common : buildings) {
            if (!(common instanceof IBuilding building)) continue;
            for (Integer citizenId : buildingAccess.getOrDefault(common, EMPTY_BUILDING_ACCESS).assignedCitizenIds()) {
                result.putIfAbsent(citizenId, building);
            }
        }
        return result;
    }

    static String readableBuildingName(ICommonBuilding common, IBuilding building) {
        String custom = building == null ? null : safe(building::getCustomName, null);
        if (custom != null && !custom.isBlank()) return custom;
        String display = building == null ? null : safe(building::getBuildingDisplayName, null);
        if (display != null && !display.isBlank()) return display;
        return common.getBuildingType() == null ? "unknown" : common.getBuildingType().getTranslationKey();
    }

    static String readableIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        int separator = identifier.indexOf(':');
        String path = separator >= 0 ? identifier.substring(separator + 1) : identifier;
        StringJoiner result = new StringJoiner(" ");
        for (String word : path.replace('-', '_').split("_+")) {
            if (!word.isBlank()) result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.length() == 0 ? identifier : result.toString();
    }

    record BuildingAccess(Set<Integer> assignedCitizenIds, Map<Integer, List<Object>> openRequestsByCitizen) {
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
