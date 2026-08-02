package com.colonybridge.export;

import java.util.Objects;

public record ExportResult(
        ExportRequest request,
        LocalOutcome localOutcome,
        RemoteOutcome remoteOutcome,
        ExportStatus status,
        ExportTimings timings
) {
    public ExportResult {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(localOutcome, "localOutcome");
        Objects.requireNonNull(remoteOutcome, "remoteOutcome");
        Objects.requireNonNull(status, "status");
        timings = timings == null ? ExportTimings.EMPTY : timings;
    }

    public enum LocalOutcome { SAVED, NOT_FOUND, FAILED }
    public enum RemoteOutcome { DISABLED, MISCONFIGURED, QUEUED, SUCCEEDED, FAILED, COALESCED, CANCELLED }
}
