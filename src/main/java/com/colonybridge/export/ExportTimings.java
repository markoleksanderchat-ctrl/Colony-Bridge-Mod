package com.colonybridge.export;

public record ExportTimings(
        long collectionMs,
        long serializationMs,
        long fingerprintMs,
        long diskWriteMs,
        long retentionMs,
        long sanitizationMs,
        long remotePublishMs,
        long localTotalMs
) {
    public static final ExportTimings EMPTY = new ExportTimings(0, 0, 0, 0, 0, 0, 0, 0);

    public ExportTimings {
        if (collectionMs < 0 || serializationMs < 0 || fingerprintMs < 0 || diskWriteMs < 0
                || retentionMs < 0 || sanitizationMs < 0 || remotePublishMs < 0 || localTotalMs < 0) {
            throw new IllegalArgumentException("Export timings must not be negative.");
        }
    }

    public ExportTimings withRemote(long sanitization, long publish) {
        return new ExportTimings(collectionMs, serializationMs, fingerprintMs, diskWriteMs, retentionMs,
                sanitization, publish, localTotalMs);
    }
}
