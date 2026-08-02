package com.colonybridge;

import com.colonybridge.model.BridgeInfo;
import com.colonybridge.utility.JsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ContractFixtureExporter {
    private ContractFixtureExporter() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one output directory argument.");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        var snapshot = SnapshotFixtures.smallColony("2026-08-02T12:00:00Z", "contract-fixture");
        write(output.resolve("snapshot-v2.json"), snapshot);
        write(output.resolve("bridge-info-v1.json"), new BridgeInfo(
                ColonyBridgeConstants.VERSION,
                ColonyBridgeConstants.PROTOCOL_ID,
                ColonyBridgeConstants.SCHEMA_VERSION,
                ColonyBridgeConstants.OUTPUT_LAYOUT_VERSION,
                "filesystem",
                true,
                "1.21.1",
                "21.1.238",
                "1.1.1319-1.21.1",
                "ready",
                "2026-08-02T12:00:00Z",
                "2026-08-02T12:00:00Z",
                25L,
                1,
                "C:/contract-fixture/colonybridge",
                List.of("C:/contract-fixture/colonybridge/latest/colony-fixture-1.json")
        ));
        System.out.println("Exported Java model fixtures to " + output);
    }

    private static void write(Path file, Object value) throws IOException {
        Files.writeString(file, JsonSupport.toJson(value, true) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
