package com.colonybridge.config;

import com.colonybridge.utility.RemoteEndpointValidator;

public record BridgeConfigValues(
        boolean enabled,
        boolean exportOnStartup,
        boolean exportOnPlayerJoin,
        boolean exportOnShutdown,
        boolean exportOnLastPlayerDisconnect,
        boolean periodicExportEnabled,
        int periodicExportSeconds,
        int retainSnapshots,
        boolean prettyPrintJson,
        boolean showExportProgress,
        boolean includeCitizenPositions,
        boolean includeOwnerUuid,
        boolean dayCounterEnabled,
        boolean remoteSyncEnabled,
        String remoteEndpoint,
        String remoteToken
) {
    public static final int MIN_PERIODIC_SECONDS = 30;
    public static final int DEFAULT_PERIODIC_SECONDS = 120;
    public static final int MAX_PERIODIC_SECONDS = 86_400;
    public static final int MAX_RETAINED_SNAPSHOTS = 10_000;

    public static BridgeConfigValues defaults() {
        return new BridgeConfigValues(true, true, true, true, true, true, DEFAULT_PERIODIC_SECONDS, 50, true, false, false, false, true,
                false, "", "");
    }

    public BridgeConfigValues validated() {
        int normalizedPeriodicSeconds = Math.clamp(periodicExportSeconds, MIN_PERIODIC_SECONDS, MAX_PERIODIC_SECONDS);
        return new BridgeConfigValues(
                enabled,
                exportOnStartup,
                exportOnPlayerJoin,
                exportOnShutdown,
                exportOnLastPlayerDisconnect,
                periodicExportEnabled,
                normalizedPeriodicSeconds,
                Math.clamp(retainSnapshots, 1, MAX_RETAINED_SNAPSHOTS),
                prettyPrintJson,
                showExportProgress,
                includeCitizenPositions,
                includeOwnerUuid,
                dayCounterEnabled,
                remoteSyncEnabled,
                remoteEndpoint == null ? "" : remoteEndpoint.trim(),
                remoteToken == null ? "" : remoteToken.trim()
        );
    }

    public boolean remoteSyncConfigured() {
        return remoteSyncEnabled && RemoteEndpointValidator.isSafeHttps(remoteEndpoint) && !remoteToken.isBlank();
    }
}
