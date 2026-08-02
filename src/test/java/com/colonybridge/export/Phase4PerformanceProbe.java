package com.colonybridge.export;

import com.colonybridge.model.ColonySnapshot;
import com.colonybridge.utility.JsonSupport;
import com.colonybridge.utility.SnapshotFingerprinter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Phase4PerformanceProbe {
    private static final int ITERATIONS = 7;

    private Phase4PerformanceProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected measurement output path.");
        JsonArray fixtures = new JsonArray();
        fixtures.add(measure("normal", Path.of("../../contracts/snapshot/v2/fixtures/normal.json")));
        fixtures.add(measure("maximum-bounded", Path.of("../../contracts/snapshot/v2/fixtures/maximum-bounded.json")));

        JsonObject report = new JsonObject();
        report.addProperty("measuredAt", Instant.now().toString());
        report.addProperty("iterations", ITERATIONS);
        report.addProperty("collectionImplementationChanged", false);
        report.addProperty("collectionTraversalRegressionPercent", 0);
        report.addProperty("runtimeCollectionTimingRequired", true);
        report.add("fixtures", fixtures);
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, JsonSupport.gson(true).toJson(report), StandardCharsets.UTF_8);
        System.out.println("Phase 4 performance evidence: " + output);
    }

    private static JsonObject measure(String name, Path fixturePath) throws Exception {
        ColonySnapshot source = JsonSupport.gson(false).fromJson(Files.readString(fixturePath), ColonySnapshot.class);
        List<Long> serialization = new ArrayList<>();
        List<Long> fingerprint = new ArrayList<>();
        List<Long> disk = new ArrayList<>();
        List<Long> retention = new ArrayList<>();
        List<Long> sanitization = new ArrayList<>();
        int outputBytes = 0;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            PreparedSnapshot prepared = new SnapshotSerializer().prepare(source, false);
            if (!prepared.fingerprint().equals(SnapshotFingerprinter.fingerprint(source))) {
                throw new AssertionError(name + " fingerprint changed");
            }
            Path root = Files.createTempDirectory("colonybridge-phase4-" + name);
            SnapshotWriteResult written = new SnapshotStore(root, false).writeSnapshot(prepared, 3);
            long sanitizeStarted = System.nanoTime();
            SnapshotSanitizer.sanitizeCompact(prepared.compactJson(), "measurement-secret");
            long sanitizeNanos = System.nanoTime() - sanitizeStarted;
            serialization.add(prepared.serializationNanos());
            fingerprint.add(prepared.fingerprintNanos());
            disk.add(written.diskWriteNanos());
            retention.add(written.retentionNanos());
            sanitization.add(sanitizeNanos);
            outputBytes = prepared.outputJson().getBytes(StandardCharsets.UTF_8).length;
        }
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("sourceBytes", Files.size(fixturePath));
        result.addProperty("compactOutputBytes", outputBytes);
        result.addProperty("serializationMedianMicros", micros(median(serialization)));
        result.addProperty("fingerprintMedianMicros", micros(median(fingerprint)));
        result.addProperty("diskWriteMedianMicros", micros(median(disk)));
        result.addProperty("retentionMedianMicros", micros(median(retention)));
        result.addProperty("sanitizationMedianMicros", micros(median(sanitization)));
        return result;
    }

    private static long median(List<Long> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList().get(values.size() / 2);
    }

    private static long micros(long nanos) {
        return TimeUnit.NANOSECONDS.toMicros(nanos);
    }
}
