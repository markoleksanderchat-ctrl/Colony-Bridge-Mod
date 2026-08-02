package com.colonybridge.export;

import com.colonybridge.api.ExportTrigger;
import com.colonybridge.config.BridgeConfigValues;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalInt;

public record ExportRequest(
        MinecraftServer server,
        BridgeConfigValues config,
        ExportTrigger trigger,
        OptionalInt colonyId,
        Instant requestedAt
) {
    public ExportRequest {
        Objects.requireNonNull(server, "server");
        config = Objects.requireNonNull(config, "config").validated();
        Objects.requireNonNull(trigger, "trigger");
        colonyId = colonyId == null ? OptionalInt.empty() : colonyId;
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    }

    public static ExportRequest all(MinecraftServer server, BridgeConfigValues config, ExportTrigger trigger) {
        return new ExportRequest(server, config, trigger, OptionalInt.empty(), Instant.now());
    }

    public static ExportRequest one(MinecraftServer server, BridgeConfigValues config, ExportTrigger trigger, int colonyId) {
        return new ExportRequest(server, config, trigger, OptionalInt.of(colonyId), Instant.now());
    }
}
