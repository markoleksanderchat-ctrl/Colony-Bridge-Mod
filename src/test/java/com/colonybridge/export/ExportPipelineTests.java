package com.colonybridge.export;

import com.colonybridge.SnapshotFixtures;
import com.colonybridge.ColonyBridgeConstants;
import com.colonybridge.model.CapabilityData;
import com.colonybridge.model.ColonySnapshot;
import com.colonybridge.model.BridgeInfo;
import com.colonybridge.utility.JsonSupport;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExportPipelineTests {
    private ExportPipelineTests() {
    }

    public static void run() throws Exception {
        sanitizesWithoutMutatingSource();
        rejectsUnsafeRemoteEndpoints();
        statusDefensivelyCopiesPaths();
        malformedTimestampCannotEscapeHistoryDirectory();
        snapshotMapsSerializeDeterministically();
        remoteRetryDelayIsBounded();
        bridgeInfoAdvertisesDesktopContract();
    }

    private static void bridgeInfoAdvertisesDesktopContract() {
        BridgeInfo info = new BridgeInfo(
                ColonyBridgeConstants.VERSION,
                ColonyBridgeConstants.PROTOCOL_ID,
                ColonyBridgeConstants.SCHEMA_VERSION,
                ColonyBridgeConstants.OUTPUT_LAYOUT_VERSION,
                "filesystem",
                true,
                "1.21.1",
                "21.1.238",
                "test",
                "ready",
                "2026-07-17T00:00:00Z",
                "2026-07-17T00:00:01Z",
                1L,
                1,
                "root",
                List.of("latest.json")
        );
        JsonObject json = com.google.gson.JsonParser.parseString(JsonSupport.toJson(info, false)).getAsJsonObject();
        require(ColonyBridgeConstants.PROTOCOL_ID.equals(json.get("protocolId").getAsString()),
                "bridge-info must advertise the stable protocol id");
        require("filesystem".equals(json.get("transport").getAsString()),
                "bridge-info must identify its offline filesystem transport");
        require(json.get("readOnly").getAsBoolean(), "bridge-info must advertise the read-only guarantee");
    }

    private static void remoteRetryDelayIsBounded() {
        require(RemoteSnapshotPublisher.retryDelayMillis(java.util.Optional.empty(), 1) == 250L,
                "first retry should use a short backoff");
        require(RemoteSnapshotPublisher.retryDelayMillis(java.util.Optional.of("2"), 1) == 2_000L,
                "Retry-After seconds should be respected");
        require(RemoteSnapshotPublisher.retryDelayMillis(java.util.Optional.of("60"), 1) == 5_000L,
                "server retry delays must be capped to keep exports responsive");
        require(RemoteSnapshotPublisher.retryDelayMillis(java.util.Optional.of("invalid"), 2) == 500L,
                "invalid Retry-After values should fall back safely");
    }

    private static void sanitizesWithoutMutatingSource() {
        var snapshot = SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "manual");
        JsonObject sanitized = SnapshotSanitizer.sanitize(snapshot, "token-a");
        require(!sanitized.getAsJsonObject("world").has("worldId"), "world id must be removed");
        require(!sanitized.getAsJsonObject("colony").has("ownerUuid"), "owner UUID must be removed");
        require(!sanitized.getAsJsonArray("citizens").get(0).getAsJsonObject().has("uuid"), "citizen UUID must be removed");
        require(sanitized.getAsJsonArray("buildings").get(0).getAsJsonObject().get("id").getAsString().startsWith("building-"),
                "building ids must be aliased");
        require(!containsSensitiveKey(sanitized), "sensitive coordinate and identity keys must be scrubbed recursively");
        String firstAlias = sanitized.getAsJsonArray("buildings").get(0).getAsJsonObject().get("id").getAsString();
        String sameSecretAlias = SnapshotSanitizer.sanitize(snapshot, "token-a").getAsJsonArray("buildings")
                .get(0).getAsJsonObject().get("id").getAsString();
        String otherSecretAlias = SnapshotSanitizer.sanitize(snapshot, "token-b").getAsJsonArray("buildings")
                .get(0).getAsJsonObject().get("id").getAsString();
        require(firstAlias.equals(sameSecretAlias), "aliases must be stable for the same remote token");
        require(!firstAlias.equals(otherSecretAlias), "aliases must not be predictable without the remote token");
        requireThrows(() -> SnapshotSanitizer.sanitize(snapshot, ""), "blank alias secrets must be rejected");
        require(snapshot.world().worldId() != null, "sanitization must not mutate the source snapshot");
    }

    private static void rejectsUnsafeRemoteEndpoints() {
        require("example.com".equals(RemoteSnapshotPublisher.validatedEndpoint("https://example.com/upload").getHost()),
                "HTTPS endpoint should be accepted");
        requireThrows(() -> RemoteSnapshotPublisher.validatedEndpoint("http://example.com/upload"), "HTTP endpoint must be rejected");
        requireThrows(() -> RemoteSnapshotPublisher.validatedEndpoint("https://user:pass@example.com/upload"),
                "embedded credentials must be rejected");
        requireThrows(() -> RemoteSnapshotPublisher.validatedEndpoint("https://not a host/upload"),
                "malformed endpoints must be rejected cleanly");
    }

    private static void statusDefensivelyCopiesPaths() {
        List<Path> mutable = new ArrayList<>();
        mutable.add(Path.of("one"));
        ExportStatus status = new ExportStatus(true, "test", 1, Instant.now(), Instant.now(), 1,
                Path.of("root"), "adapter", 0, 0, mutable);
        mutable.clear();
        require(status.latestFiles().size() == 1, "status must retain an immutable snapshot of paths");
        requireThrows(() -> status.latestFiles().add(Path.of("two")), "status paths must be immutable");
    }

    private static void malformedTimestampCannotEscapeHistoryDirectory() throws Exception {
        Path root = Files.createTempDirectory("colonybridge-timestamp-test");
        SnapshotStore store = new SnapshotStore(root, false);
        var fixture = SnapshotFixtures.smallColony("../../escape", "manual");
        store.writeSnapshot(fixture, 5);
        require(!Files.exists(root.getParent().resolve("escape.json")), "malformed timestamp must not escape the snapshot directory");
    }

    private static void snapshotMapsSerializeDeterministically() {
        var source = SnapshotFixtures.smallColony("2026-07-10T01:00:00Z", "manual");
        Map<String, Integer> statistics = new LinkedHashMap<>();
        statistics.put("z_stat", 2);
        statistics.put("a_stat", 1);
        Map<String, CapabilityData> capabilities = new LinkedHashMap<>();
        capabilities.put("z_capability", CapabilityData.available());
        capabilities.put("a_capability", CapabilityData.available());
        ColonySnapshot snapshot = new ColonySnapshot(source.schemaVersion(), source.bridgeVersion(), source.generatedAt(),
                source.trigger(), source.fingerprint(), source.game(), source.world(), source.colony(), source.summary(),
                source.citizens(), source.buildings(), source.requests(), source.construction(), source.environment(),
                source.territory(), source.livestock(), source.research(), statistics, source.recentStatistics(),
                source.defenseStatistics(),
                source.foodSupply(), source.stockLedger(), capabilities, source.warnings(), source.errors());
        String json = JsonSupport.toJson(snapshot, false);
        require(json.indexOf("a_stat") < json.indexOf("z_stat"), "statistics must serialize in key order");
        require(json.indexOf("a_capability") < json.indexOf("z_capability"), "capabilities must serialize in key order");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean containsSensitiveKey(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsSensitiveKey(child)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;
        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("worldId", "ownerUuid", "uuid", "center", "position", "currentPosition",
                "lastKnownPosition", "bedPosition", "statusPosition", "homePosition", "location", "bounds")) {
            if (object.has(key)) return true;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (containsSensitiveKey(entry.getValue())) return true;
        }
        return false;
    }

    private static void requireThrows(ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
