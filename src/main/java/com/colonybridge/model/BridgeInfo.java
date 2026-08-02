package com.colonybridge.model;

import java.util.List;

public record BridgeInfo(
        String bridgeVersion,
        String protocolId,
        int schemaVersion,
        int outputLayoutVersion,
        String transport,
        boolean readOnly,
        String minecraftVersion,
        String neoForgeVersion,
        String mineColoniesVersion,
        String status,
        String lastExportAt,
        String lastSuccessfulExportAt,
        Long lastExportDurationMs,
        int coloniesDetected,
        String outputRoot,
        List<String> latestFiles
) {
    public BridgeInfo {
        latestFiles = latestFiles == null ? List.of() : List.copyOf(latestFiles);
    }
}
