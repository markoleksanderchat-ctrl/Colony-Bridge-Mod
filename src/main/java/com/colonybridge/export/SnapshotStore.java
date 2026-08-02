package com.colonybridge.export;

import com.colonybridge.model.BridgeInfo;
import com.colonybridge.model.ColonySnapshot;
import com.colonybridge.utility.FilenameSanitizer;
import com.colonybridge.utility.JsonSupport;
import com.colonybridge.utility.SnapshotFingerprinter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class SnapshotStore {
    private final Path root;
    private final boolean prettyPrint;

    public SnapshotStore(Path root, boolean prettyPrint) {
        this.root = root.toAbsolutePath().normalize();
        this.prettyPrint = prettyPrint;
    }

    public Path root() {
        return root;
    }

    public SnapshotWriteResult writeSnapshot(ColonySnapshot input, int retainSnapshots) throws IOException {
        String fingerprint = SnapshotFingerprinter.fingerprint(input);
        ColonySnapshot snapshot = input.withFingerprint(fingerprint);
        String fileStem = fileStem(snapshot);
        Path latestPath = root.resolve("latest").resolve(fileStem + ".json");
        Path historyDir = root.resolve("snapshots").resolve(fileStem);
        Path historyPath = historyDir.resolve(timestampFileName(snapshot.generatedAt()));

        boolean duplicate = latestFingerprint(latestPath).map(fingerprint::equals).orElse(false);
        String json = JsonSupport.toJson(snapshot, prettyPrint);
        boolean wroteHistory = false;
        if (!duplicate) {
            AtomicFileWriter.writeUtf8(historyPath, json);
            wroteHistory = true;
        }
        AtomicFileWriter.writeUtf8(latestPath, json);
        retain(historyDir, retainSnapshots);
        return new SnapshotWriteResult(latestPath, wroteHistory ? historyPath : null, wroteHistory, duplicate);
    }

    public void writeBridgeInfo(BridgeInfo info) throws IOException {
        AtomicFileWriter.writeUtf8(root.resolve("bridge-info.json"), JsonSupport.toJson(info, prettyPrint));
    }

    private Optional<String> latestFingerprint(Path latestPath) throws IOException {
        if (!Files.isRegularFile(latestPath)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(latestPath, StandardCharsets.UTF_8);
            JsonObject rootObject = JsonParser.parseString(json).getAsJsonObject();
            return rootObject.has("fingerprint") && !rootObject.get("fingerprint").isJsonNull()
                    ? Optional.of(rootObject.get("fingerprint").getAsString())
                    : Optional.empty();
        } catch (RuntimeException malformedLatest) {
            return Optional.empty();
        }
    }

    private void retain(Path historyDir, int retainSnapshots) throws IOException {
        if (retainSnapshots < 1 || !Files.isDirectory(historyDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(historyDir)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> {
                        try {
                            return AtomicFileWriter.isRegularChild(historyDir, path);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .sorted(Comparator.<Path>comparingLong(SnapshotStore::lastModifiedMillis)
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
            int remove = Math.max(0, files.size() - retainSnapshots);
            for (int i = 0; i < remove; i++) {
                Files.deleteIfExists(files.get(i));
            }
        }
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return Long.MIN_VALUE;
        }
    }

    private static String timestampFileName(String generatedAt) {
        String timestamp;
        try {
            timestamp = DateTimeFormatter.ISO_INSTANT.format(generatedAt == null ? Instant.now() : Instant.parse(generatedAt));
        } catch (DateTimeException malformedTimestamp) {
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        }
        return timestamp.replace(":", "-") + ".json";
    }

    private static String fileStem(ColonySnapshot snapshot) {
        String dimension = FilenameSanitizer.sanitize(snapshot.world().dimension());
        String colonyId = snapshot.colony().id() == null ? "unknown" : snapshot.colony().id().toString();
        return "colony-" + dimension + "-" + FilenameSanitizer.sanitize(colonyId);
    }
}
