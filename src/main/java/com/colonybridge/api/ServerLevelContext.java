package com.colonybridge.api;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Objects;

public record ServerLevelContext(MinecraftServer server, Path outputRoot) {
    public ServerLevelContext {
        server = Objects.requireNonNull(server, "server");
        outputRoot = Objects.requireNonNull(outputRoot, "outputRoot").toAbsolutePath().normalize();
    }
}
