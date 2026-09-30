package com.colonybridge.market;

import com.colonybridge.export.SnapshotSanitizer;
import com.colonybridge.market.trader.RoyalExchangePresentationModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class AuditRegressionTests {
    @TempDir Path temporary;
    private static final InventoryMutation MUTATION = new InventoryMutation("minecraft:diamond", 3, "minecraft:stone", 16);

    @Test void rejectsCurrencyAtQuoteAndMutationBoundaries() {
        for (String item : List.of("minecraft:diamond", "minecraft:diamond_block", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")) {
            var classified = VanillaClassificationDomain.classify(new ItemClassificationFacts(item, item, 64,
                    0, 64, false, false, false, false, false, false, false, false, false, 0));
            assertFalse(MarketItemIds.isTradable(item));
            assertThrows(IllegalArgumentException.class, () -> QuoteLifecycleService.create("q", "p", classified,
                    item, 64, TradeDirection.BUY, 1, 1, null, List.of(), 0, MarketConfig.defaults()));
            assertThrows(IllegalArgumentException.class, () -> TradeExecutionService.mutation(quote("q", "p", item, 0)));
        }
        assertTrue(MarketItemIds.isTradable("minecraft:diamond_pickaxe"));
    }

    @Test void rollsBackPartialInventoryApplication() {
        Inventory inventory = new Inventory(); inventory.failApply = true;
        var result = MarketTransactionCoordinator.execute(inventory, MUTATION, () -> fail("state must not run"),
                () -> fail("state did not start"), () -> fail("must not persist"), "ok");
        assertFalse(result.success()); assertEquals(10, inventory.goods); assertEquals(1, inventory.rollbacks);
    }

    @Test void rollsBackPartiallyUpdatedMarketState() {
        Inventory inventory = new Inventory(); int[] state = {0};
        var result = MarketTransactionCoordinator.execute(inventory, MUTATION,
                () -> { state[0] = 1; throw new IllegalStateException("partial"); }, () -> state[0] = 0, () -> {}, "ok");
        assertFalse(result.success()); assertEquals(0, state[0]); assertEquals(10, inventory.goods);
    }

    @Test void attemptsInventoryRollbackEvenWhenStateRollbackFails() {
        Inventory inventory = new Inventory();
        var result = MarketTransactionCoordinator.execute(inventory, MUTATION, () -> {},
                () -> { throw new IllegalStateException("rollback"); }, () -> { throw new IOException("disk"); }, "ok");
        assertFalse(result.success()); assertEquals(10, inventory.goods);
        assertEquals(1, result.failure().getSuppressed().length);
        assertTrue(result.message().contains("recovery failed"));
    }

    @Test void preservesBothRollbackFailures() {
        Inventory inventory = new Inventory(); inventory.failRollback = true;
        var result = MarketTransactionCoordinator.execute(inventory, MUTATION, () -> {},
                () -> { throw new IllegalStateException("state rollback"); }, () -> { throw new IOException("disk"); }, "ok");
        assertEquals(2, result.failure().getSuppressed().length); assertEquals(1, inventory.rollbacks);
    }

    @Test void preparationFailureIsReported() {
        Inventory inventory = new Inventory(); inventory.failPrepare = true;
        var result = MarketTransactionCoordinator.execute(inventory, MUTATION, () -> {}, () -> {}, () -> {}, "ok");
        assertFalse(result.success()); assertEquals(0, inventory.rollbacks); assertEquals(10, inventory.goods);
    }

    @Test void durableCommitIsNeverRolledBackOnlyInMemory() {
        Inventory inventory = new Inventory(); inventory.failCommit = true; boolean[] saved = {false};
        var result = MarketTransactionCoordinator.execute(inventory, MUTATION, () -> {}, () -> fail("durable"),
                () -> saved[0] = true, "ok");
        assertTrue(result.success()); assertTrue(saved[0]); assertEquals(7, inventory.goods); assertEquals(0, inventory.rollbacks);
        assertNotNull(result.failure());
    }

    @Test void fullInventoryHasActionableFeedbackAfterRollback() {
        Inventory inventory = new Inventory(); inventory.full = true;
        var result = MarketTransactionCoordinator.execute(inventory, MUTATION, () -> {}, () -> {}, () -> {}, "ok");
        assertFalse(result.success()); assertEquals(10, inventory.goods); assertEquals(12, TradeFeedback.code(result.message()));
    }

    @Test void sanitizesLivestockReferencesAndTerritoryCoordinates() {
        var json = SnapshotSanitizer.sanitizeCompact("""
                {"buildings":[{"id":"100,64,200"}],"livestock":{"huts":[{"buildingId":"100,64,200","position":{"x":100}}]},
                 "territory":{"minChunkX":6,"maxChunkX":12,"minChunkZ":8,"maxChunkZ":14,"claimedChunks":5}}
                """, "secret");
        var hut = json.getAsJsonObject("livestock").getAsJsonArray("huts").get(0).getAsJsonObject();
        assertEquals(json.getAsJsonArray("buildings").get(0).getAsJsonObject().get("id"), hut.get("buildingId"));
        assertFalse(hut.has("position")); assertFalse(json.toString().contains("100,64,200"));
        assertEquals(Set.of("claimedChunks"), json.getAsJsonObject("territory").keySet());
    }

    @Test void unknownHutReferenceDoesNotLeakItsCoordinateId() {
        var json = SnapshotSanitizer.sanitizeCompact("{\"livestock\":{\"huts\":[{\"buildingId\":\"1,2,3\"}]}}", "secret");
        assertTrue(json.getAsJsonObject("livestock").getAsJsonArray("huts").get(0).getAsJsonObject().get("buildingId").isJsonNull());
    }

    @Test void quotesReplacePreviousOfferAndExpireAtTheirDeadline() {
        Map<String, MarketQuote> quotes = new HashMap<>();
        QuoteLifecycleService.retain(quotes, quote("a", "p", "minecraft:stone", 0), 0);
        QuoteLifecycleService.retain(quotes, quote("b", "p", "minecraft:stone", 1), 1);
        assertEquals(Set.of("b"), quotes.keySet());
        QuoteLifecycleService.retain(quotes, quote("c", "other", "minecraft:stone", 200), 200);
        assertEquals(Set.of("c"), quotes.keySet());
    }

    @Test void quoteCountHasGlobalBound() {
        Map<String, MarketQuote> quotes = new HashMap<>();
        for (int i = 0; i < 1100; i++) QuoteLifecycleService.retain(quotes, quote("q" + i, "p" + i, "minecraft:stone", 0), 0);
        assertEquals(1024, quotes.size()); assertTrue(quotes.containsKey("q1099"));
    }

    @Test void completedAndDeclinedQuotesStayTerminal() {
        assertEquals(3, RoyalExchangePresentationModel.quoteState(3, true, true));
        assertEquals(4, RoyalExchangePresentationModel.quoteState(4, true, true));
        assertEquals(5, RoyalExchangePresentationModel.quoteState(2, true, true));
        assertEquals(2, RoyalExchangePresentationModel.quoteState(1, true, false));
    }

    @Test void anomalyDoesNotRepeatAfterDisappearing() {
        var inbox = new OnlineAnomalyInbox(); var event = event("a", 10000);
        inbox.accept(List.of(), List.of(event)); assertEquals(List.of(event), inbox.drain(1));
        inbox.accept(List.of(event), List.of()); inbox.accept(List.of(), List.of(event));
        assertTrue(inbox.drain(2).isEmpty());
    }

    @Test void expiredAndWithdrawnNoticesAreNotDelivered() {
        var inbox = new OnlineAnomalyInbox(); var event = event("a", 10);
        inbox.accept(List.of(), List.of(event)); assertTrue(inbox.drain(10).isEmpty());
        inbox.accept(List.of(), List.of(event("b", 100))); inbox.accept(List.of(), List.of());
        assertTrue(inbox.drain(1).isEmpty());
    }

    @Test void anomalyQueueIsBounded() {
        var inbox = new OnlineAnomalyInbox(); List<OnlineMarketEvent> events = new ArrayList<>();
        for (int i=0;i<100;i++) events.add(event("a"+i,10000));
        inbox.accept(List.of(), events); assertEquals(32,inbox.drain(1).size());
    }

    @Test void malformedNestedMarketDataIsQuarantined() throws Exception {
        Path path=temporary.resolve("market.json");
        Files.writeString(path,"{\"version\":2,\"seed\":1,\"records\":{\"minecraft:stone\":null}}");
        assertThrows(IOException.class,()->MarketPersistence.load(path)); assertFalse(Files.exists(path));
        try(var files=Files.list(temporary)){assertEquals(1,files.count());}
    }

    @Test void rejectsNonFiniteHistoryAndMismatchedIds() throws Exception {
        for(String record:List.of("{\"itemId\":\"minecraft:dirt\",\"baseValue\":1,\"priceHistory\":[1]}",
                "{\"itemId\":\"minecraft:stone\",\"baseValue\":1,\"priceHistory\":[\"NaN\"]}")) {
            Path path=temporary.resolve("market.json");
            Files.writeString(path,"{\"version\":2,\"records\":{\"minecraft:stone\":"+record+"}}");
            assertThrows(IOException.class,()->MarketPersistence.load(path));
        }
    }

    @Test void olderAndClosedOnlineCompletionsCannotReplaceCache() {
        List<OnlineMarketSnapshot> writes=new ArrayList<>();
        var current=new OnlineMarketSnapshot(200,Map.of("IRON",1.0),List.of());
        var client=new OnlineMarketClient(new OnlineMarketCache(){
            public OnlineMarketSnapshot load(){return current;}
            public void save(OnlineMarketSnapshot snapshot){writes.add(snapshot);}
        });
        try {
            client.accept(new OnlineMarketSnapshot(100,Map.of(),List.of()),java.net.URI.create("https://example.invalid"));
            assertTrue(writes.isEmpty()); client.close();
            client.accept(new OnlineMarketSnapshot(300,Map.of(),List.of()),java.net.URI.create("https://example.invalid"));
            assertTrue(writes.isEmpty());
        } finally {client.close();}
    }

    private static MarketQuote quote(String id,String player,String item,long now) {
        return new MarketQuote(id,player,item,16,TradeDirection.BUY,3,3,3,"steady","",now,now,now+100,false);
    }
    private static OnlineMarketEvent event(String id,long end) {
        return new OnlineMarketEvent(id,"Title","Description","notice","onset",List.of("IRON"),1,0,end);
    }
    private static final class Inventory implements PlayerInventoryPort {
        int goods=10,rollbacks; boolean failPrepare,failApply,failRollback,failCommit,full;
        public String playerId(){return "p";}
        public boolean restrictedGameMode(){return false;}
        public int countDiamonds(){return goods;}
        public int countPlainItems(String id){return goods;}
        public PendingInventoryMutation prepare(InventoryMutation mutation){
            if(failPrepare)throw new IllegalStateException("prepare");
            return new PendingInventoryMutation(){
                public void apply(){goods=7;if(full)throw new InventoryCapacityException();if(failApply)throw new IllegalStateException("apply");}
                public void commit(){if(failCommit)throw new IllegalStateException("commit");}
                public void rollback(){rollbacks++;if(failRollback)throw new IllegalStateException("rollback");goods=10;}
            };
        }
    }
}
