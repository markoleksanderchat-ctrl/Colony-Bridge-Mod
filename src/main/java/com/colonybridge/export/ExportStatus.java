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
        List<Path> latestFiles
) {
    public ExportStatus {
        latestFiles = latestFiles == null ? List.of() : List.copyOf(latestFiles);
    }
}
