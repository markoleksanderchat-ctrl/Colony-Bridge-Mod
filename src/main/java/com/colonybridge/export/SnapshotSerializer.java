package com.colonybridge.export;

import com.colonybridge.model.ColonySnapshot;
import com.colonybridge.utility.JsonSupport;
import com.colonybridge.utility.SnapshotFingerprinter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;

public final class SnapshotSerializer {
    public PreparedSnapshot prepare(ColonySnapshot input, boolean prettyPrint) {
        Objects.requireNonNull(input, "input");
        long serializationStarted = System.nanoTime();
        String remoteCompact = JsonSupport.toJson(input.withFingerprint(null), false);
        JsonObject root = JsonParser.parseString(remoteCompact).getAsJsonObject();
        long serializationNanos = System.nanoTime() - serializationStarted;

        long fingerprintStarted = System.nanoTime();
        String fingerprint = SnapshotFingerprinter.fingerprint(root);
        long fingerprintNanos = System.nanoTime() - fingerprintStarted;

        root.addProperty("fingerprint", fingerprint);
        serializationStarted = System.nanoTime();
        String compact = root.toString();
        String output = prettyPrint ? JsonSupport.gson(true).toJson(root) : compact;
        serializationNanos += System.nanoTime() - serializationStarted;
        return new PreparedSnapshot(input.withFingerprint(fingerprint), remoteCompact, compact, output, fingerprint,
                serializationNanos, fingerprintNanos);
    }
}
