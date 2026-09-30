package com.colonybridge.market;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class OnlineMarketRecoveryTests {
    @Test void acceptsResponsesAboveTheOldLimit() throws Exception {
        String payload = " ".repeat(600_000);
        assertEquals(payload, OnlineMarketResponse.read(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void enforcesTheNewBoundWithoutReadingTheWholeStream() throws Exception {
        var input = new ByteArrayInputStream(new byte[OnlineMarketResponse.MAX_BYTES + 100]);
        assertThrows(IOException.class, () -> OnlineMarketResponse.read(input));
        assertEquals(99, input.available());
        assertEquals(OnlineMarketResponse.MAX_BYTES,
                OnlineMarketResponse.read(new ByteArrayInputStream(new byte[OnlineMarketResponse.MAX_BYTES])).length());
    }
    @Test void preservesUtf8() throws Exception {
        assertEquals("Guild — café", OnlineMarketResponse.read(new ByteArrayInputStream("Guild — café".getBytes(StandardCharsets.UTF_8))));
    }
    @Test void distinguishesCachedConnectedFailedAndRecoveredFeeds() {
        long now = System.currentTimeMillis();
        var snapshot = new OnlineMarketSnapshot(now, Map.of("IRON", 1.0), List.of());
        var client = new OnlineMarketClient(new OnlineMarketCache() {
            public OnlineMarketSnapshot load() { return snapshot; }
            public void save(OnlineMarketSnapshot value) { }
        });
        try {
            assertTrue(client.status(now, OnlineMarketConfig.defaults()).startsWith("cached pricing"));
            client.accept(snapshot, URI.create("https://example.invalid"));
            assertTrue(client.status(now, OnlineMarketConfig.defaults()).startsWith("connected via"));
            client.recordFailure(new java.util.concurrent.CompletionException(new UncheckedIOException(new IOException("payload rejected"))));
            String failed = client.status(now, OnlineMarketConfig.defaults());
            assertTrue(failed.startsWith("cached pricing"));
            assertTrue(failed.contains("payload rejected"));
            assertTrue(failed.contains("last fetch"));
            client.accept(snapshot, URI.create("https://example.invalid"));
            assertFalse(client.status(now, OnlineMarketConfig.defaults()).contains("payload rejected"));
            assertTrue(client.status(now + 7 * 60 * 60_000L, OnlineMarketConfig.defaults()).startsWith("local pricing"));
        } finally { client.close(); }
    }
}
