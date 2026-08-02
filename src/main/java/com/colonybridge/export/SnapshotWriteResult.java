package com.colonybridge.export;

import java.nio.file.Path;

public record SnapshotWriteResult(
        Path latestPath,
        Path historicalPath,
        boolean wroteHistoricalSnapshot,
        boolean duplicate,
        String fingerprint,
        long serializationNanos,
        long fingerprintNanos,
        long diskWriteNanos,
        long retentionNanos
) {
}
