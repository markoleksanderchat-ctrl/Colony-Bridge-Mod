package com.colonybridge.model;

public record WorldData(
        String saveName,
        String worldId,
        String dimension,
        boolean dedicatedServer
) {
}
