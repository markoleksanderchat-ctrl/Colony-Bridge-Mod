package com.colonybridge.config;

public record ExportSettings(
        boolean onStartup,
        boolean onPlayerJoin,
        boolean onShutdown,
        boolean onLastPlayerDisconnect,
        boolean periodicEnabled,
        int periodicSeconds,
        int retainSnapshots,
        boolean prettyPrintJson,
        boolean showProgress
) {
}
