package com.colonybridge.utility;

import com.colonybridge.model.ColonySnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class SnapshotFingerprinter {
    private SnapshotFingerprinter() {
    }

    public static String fingerprint(ColonySnapshot snapshot) {
        JsonObject root = JsonParser.parseString(JsonSupport.toJson(snapshot.withFingerprint(null), false)).getAsJsonObject();
        return fingerprint(root);
    }

    public static String fingerprint(JsonObject serializedSnapshot) {
        JsonObject root = serializedSnapshot.deepCopy();
        root.remove("generatedAt");
        root.remove("trigger");
        root.remove("fingerprint");
        // Scoped export errors can be environment/transient. The payload is still useful in the snapshot,
        // but should not force historical duplicates when colony state is unchanged.
        root.remove("errors");
        root.remove("statistics");
        root.remove("foodSupply");
        if (root.has("environment") && root.get("environment").isJsonObject()) {
            JsonObject environment = root.getAsJsonObject("environment");
            environment.remove("dayTimeTicks");
            environment.remove("gameTimeTicks");
        }
        if (root.has("citizens") && root.get("citizens").isJsonArray()) {
            root.getAsJsonArray("citizens").forEach(element -> {
                if (element.isJsonObject() && element.getAsJsonObject().has("details")) {
                    element.getAsJsonObject().getAsJsonObject("details").remove("jobActionsDone");
                }
            });
        }
        return Hashing.sha256(root.toString());
    }
}
