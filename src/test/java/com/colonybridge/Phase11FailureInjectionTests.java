package com.colonybridge;

import com.colonybridge.export.SnapshotStore;
import com.colonybridge.market.MarketPersistence;
import com.colonybridge.market.OnlineEndpointFailover;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Phase11FailureInjectionTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void diskWriteFailureDoesNotPublishLatestSnapshot() throws Exception {
        Path blockedRoot = temporaryDirectory.resolve("blocked-root");
        Files.writeString(blockedRoot, "not a directory");

        assertThrows(IOException.class, () -> new SnapshotStore(blockedRoot, false)
                .writeSnapshot(SnapshotFixtures.smallColony("2026-08-03T12:00:00Z", "manual"), 5));
        assertFalse(Files.exists(blockedRoot.resolve("latest")));
    }

    @Test
    void corruptMarketPersistenceIsQuarantinedBeforeFailure() throws Exception {
        Path market = temporaryDirectory.resolve("market.json");
        Files.writeString(market, "{incomplete");

        assertThrows(IOException.class, () -> MarketPersistence.load(market));
        assertFalse(Files.exists(market));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().matches("market-corrupt-\\d+\\.json")));
        }
    }

    @Test
    void completeRemoteFailureRemainsLocalAndObservable() {
        List<URI> endpoints = List.of(URI.create("https://primary.invalid/market"),
                URI.create("https://fallback.invalid/market"));

        CompletableFuture<?> result = OnlineEndpointFailover.fetch(endpoints,
                endpoint -> CompletableFuture.failedFuture(new IOException("offline: " + endpoint.getHost())));
        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertTrue(failure.getCause() instanceof IOException);
    }
}
