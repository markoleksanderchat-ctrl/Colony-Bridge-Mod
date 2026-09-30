package com.colonybridge.market.trader;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoyalExchangeDraftCacheTests {
    @Test
    void keepsEachBlockDraftForThirtySecondsThenExpiresIt() {
        AtomicLong clock = new AtomicLong(1_000);
        var cache = new RoyalExchangeDraftCache<String, String>(clock::get, 2);
        cache.put("player:world:block-a", "steak x64");
        cache.put("player:world:block-b", "oak x8");

        clock.addAndGet(29_999);
        assertEquals("steak x64", cache.get("player:world:block-a").orElseThrow());
        assertEquals("oak x8", cache.get("player:world:block-b").orElseThrow());

        clock.incrementAndGet();
        assertTrue(cache.get("player:world:block-a").isEmpty());
        assertTrue(cache.get("player:world:block-b").isEmpty());
    }

    @Test
    void capsRetainedDraftsWithoutMixingPlayersOrBlocks() {
        AtomicLong clock = new AtomicLong();
        var cache = new RoyalExchangeDraftCache<String, Integer>(clock::get, 2);
        cache.put("player-one:block-a", 3);
        clock.incrementAndGet();
        cache.put("player-two:block-a", 6);
        clock.incrementAndGet();
        cache.put("player-one:block-b", 9);

        assertTrue(cache.get("player-one:block-a").isEmpty());
        assertEquals(6, cache.get("player-two:block-a").orElseThrow());
        assertEquals(9, cache.get("player-one:block-b").orElseThrow());
    }
}
