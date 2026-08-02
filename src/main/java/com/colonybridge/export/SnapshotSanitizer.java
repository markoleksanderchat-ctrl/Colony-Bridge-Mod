package com.colonybridge.export;

import com.colonybridge.model.ColonySnapshot;
import com.colonybridge.utility.JsonSupport;
import com.colonybridge.utility.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class SnapshotSanitizer {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "worldId", "ownerUuid", "uuid", "center", "position", "currentPosition", "lastKnownPosition",
            "bedPosition", "statusPosition", "homePosition", "location", "bounds"
    );

    private SnapshotSanitizer() {
    }

    public static JsonObject sanitize(ColonySnapshot snapshot) {
        return sanitize(snapshot, "local-sanitizer");
    }

    public static JsonObject sanitize(ColonySnapshot snapshot, String aliasSecret) {
        return sanitizeCompact(JsonSupport.toJson(snapshot, false), aliasSecret);
    }

    public static JsonObject sanitizeCompact(String compactJson, String aliasSecret) {
        if (aliasSecret == null || aliasSecret.isBlank()) throw new IllegalArgumentException("Alias secret is missing.");
        JsonObject root = JsonParser.parseString(compactJson).getAsJsonObject();
        remove(root, "world", "worldId");
        remove(root, "colony", "ownerUuid");
        remove(root, "colony", "center");

        JsonArray buildings = array(root, "buildings");
        JsonArray requests = array(root, "requests");
        Map<String, String> buildingIds = aliases(buildings, "id", "building", aliasSecret);
        Map<String, String> requestIds = aliases(requests, "id", "request", aliasSecret);

        for (JsonElement element : array(root, "citizens")) sanitizeCitizen(element.getAsJsonObject(), buildingIds);
        for (JsonElement element : buildings) sanitizeBuilding(element.getAsJsonObject(), buildingIds, requestIds);
        for (JsonElement element : requests) sanitizeRequest(element.getAsJsonObject(), buildingIds, requestIds);
        for (JsonElement element : array(root, "construction")) sanitizeConstruction(element.getAsJsonObject(), buildingIds);
        scrubSensitiveKeys(root);
        return root;
    }

    private static void sanitizeCitizen(JsonObject citizen, Map<String, String> buildingIds) {
        citizen.remove("uuid");
        citizen.remove("currentPosition");
        citizen.remove("lastKnownPosition");
        remap(citizen, "workplaceBuildingId", buildingIds);
        remap(citizen, "homeBuildingId", buildingIds);
        remove(citizen, "details", "bedPosition");
        remove(citizen, "details", "statusPosition");
        remove(citizen, "details", "homePosition");
    }

    private static void sanitizeBuilding(JsonObject building, Map<String, String> buildingIds, Map<String, String> requestIds) {
        remap(building, "id", buildingIds);
        building.remove("position");
        remapArray(building, "openRequestIds", requestIds);
    }

    private static void sanitizeRequest(JsonObject request, Map<String, String> buildingIds, Map<String, String> requestIds) {
        remap(request, "id", requestIds);
        remap(request, "requestingBuildingId", buildingIds);
        remap(request, "parentRequestId", requestIds);
        remapArray(request, "childRequestIds", requestIds);
    }

    private static void sanitizeConstruction(JsonObject project, Map<String, String> buildingIds) {
        remap(project, "buildingId", buildingIds);
        remap(project, "builderHutId", buildingIds);
        remove(project, "details", "location");
        remove(project, "details", "bounds");
    }

    private static JsonArray array(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonArray() ? object.getAsJsonArray(field) : new JsonArray();
    }

    private static Map<String, String> aliases(JsonArray items, String field, String prefix, String aliasSecret) {
        Map<String, String> result = new HashMap<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (item.has(field) && !item.get(field).isJsonNull()) {
                String value = item.get(field).getAsString();
                result.put(value, prefix + "-" + Hashing.hmacSha256(value, aliasSecret).substring(0, 12));
            }
        }
        return Map.copyOf(result);
    }

    private static void remap(JsonObject object, String field, Map<String, String> aliases) {
        if (!object.has(field) || object.get(field).isJsonNull()) return;
        String alias = aliases.get(object.get(field).getAsString());
        if (alias == null) object.add(field, JsonNull.INSTANCE);
        else object.addProperty(field, alias);
    }

    private static void remapArray(JsonObject object, String field, Map<String, String> aliases) {
        if (!object.has(field) || !object.get(field).isJsonArray()) return;
        JsonArray mapped = new JsonArray();
        for (JsonElement value : object.getAsJsonArray(field)) {
            if (!value.isJsonNull()) {
                String alias = aliases.get(value.getAsString());
                if (alias != null) mapped.add(alias);
            }
        }
        object.add(field, mapped);
    }

    private static void remove(JsonObject root, String object, String field) {
        if (root.has(object) && root.get(object).isJsonObject()) root.getAsJsonObject(object).remove(field);
    }

    private static void scrubSensitiveKeys(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) scrubSensitiveKeys(child);
            return;
        }
        if (!element.isJsonObject()) return;

        JsonObject object = element.getAsJsonObject();
        for (String key : SENSITIVE_KEYS) object.remove(key);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) scrubSensitiveKeys(entry.getValue());
    }

}
