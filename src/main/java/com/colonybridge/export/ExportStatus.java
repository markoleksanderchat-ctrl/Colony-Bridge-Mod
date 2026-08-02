package com.colonybridge.export;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record ExportStatus(
        boolean mineColoniesDetected,
        String mineColoniesVersion,
        int coloniesDetected,
        Instant lastExportAt,
        Instant lastSuccessfulExportAt,
        long lastExportDurationMs,
        Path outputRoot,
        String adapterName,
        int lastWarningCount,
        int lastErrorCount,
        List<Path> latestFiles,
        String lastLocalOutcome,
        String lastRemoteOutcome,
        ExportTimings lastTimings
) {
    public ExportStatus {
        latestFiles = latestFiles == null ? List.of() : List.copyOf(latestFiles);
        lastLocalOutcome = lastLocalOutcome == null ? "none" : lastLocalOutcome;
        lastRemoteOutcome = lastRemoteOutcome == null ? "disabled" : lastRemoteOutcome;
        lastTimings = lastTimings == null ? ExportTimings.EMPTY : lastTimings;
    }

    public ExportStatus(boolean mineColoniesDetected, String mineColoniesVersion, int coloniesDetected,
                        Instant lastExportAt, Instant lastSuccessfulExportAt, long lastExportDurationMs,
                        Path outputRoot, String adapterName, int lastWarningCount, int lastErrorCount,
                        List<Path> latestFiles) {
        this(mineColoniesDetected, mineColoniesVersion, coloniesDetected, lastExportAt, lastSuccessfulExportAt,
                lastExportDurationMs, outputRoot, adapterName, lastWarningCount, lastErrorCount, latestFiles,
                "none", "disabled", ExportTimings.EMPTY);
    }
}
