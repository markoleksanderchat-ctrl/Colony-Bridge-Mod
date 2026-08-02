package com.colonybridge.market;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MarketLogicTests {
    private MarketLogicTests() {
    }

    public static void run() throws Exception {
        calculatesBasePrices();
        advancesDeterministicallyWithinBounds();
        appliesTradePressure();
        decaysEvents();
        validatesQuotesAndPayment();
        rejectsModdedIds();
        mapsOnlineIssuersExactly();
        validatesOnlineConnectionContract();
        validatesMarketScreenBounds();
        appliesValidatedOnlineMovements();
        persistsConsistently();
        migratesVersionOneState();
        rotatesContractsDeterministically();
        validatesTraderRecipeFormat();
        validatesExchangeResources();
    }

    private static void calculatesBasePrices() {
        TradeValueInput input = new TradeValueInput("minecraft:test", "Test", 1, 4, 3, 2, 1, 0, 2, 1,
                1.2, 1, 1.12, 1, 0.65, 0.8);
        TradeValueResult result = PriceCalculator.calculate(input);
        require(result.rawDiamondValue() > 0, "base price must be positive");
        require(close(PriceCalculator.bulkModifier(64, 1), 0.82), "full wholesale discount must be 0.82");
        require(close(PriceCalculator.bulkModifier(1, 1), 1), "single items must not be discounted");
        require(PriceCalculator.practicalItemsPerDiamond(0.015) == 64, "cheap goods should use a practical 64-item bundle");
        require(PriceCalculator.practicalItemsPerDiamond(0.25) == 4, "quarter-diamond goods should bundle four per diamond");
        require(PriceCalculator.practicalItemsPerDiamond(2) == 1, "expensive goods should remain single items");
    }

    private static void advancesDeterministicallyWithinBounds() {
        long start = 1_700_000_000_000L;
        MarketRecord initial = new MarketRecord("minecraft:iron_ingot", 1, 0.5, 0, start, List.of(1.0));
        MarketRecord first = MarketDynamics.advance(initial, 42, start + 3_600_000L, 0.5, 3.0, 0.6, 1.8);
        MarketRecord second = MarketDynamics.advance(initial, 42, start + 3_600_000L, 0.5, 3.0, 0.6, 1.8);
        require(first.equals(second), "market advancement must be deterministic");
        double price = first.priceHistory().get(first.priceHistory().size() - 1);
        require(price >= 0.6 && price <= 1.8, "market price must respect configured bounds");
    }

    private static void decaysEvents() {
        MarketEvent event = new MarketEvent("one", "Test", "Test", List.of("minecraft:iron_ingot"), 0.4, 1_000, 1_000);
        require(close(event.strengthAt(1_000), 1), "event must begin at full strength");
        require(close(event.strengthAt(1_500), 0.5), "event must decay gradually");
        require(close(event.strengthAt(2_000), 0), "event must expire");
    }

    private static void appliesTradePressure() {
        MarketRecord initial = new MarketRecord("minecraft:stone", 1, 0.5, 0, 100, List.of(1.0));
        require(MarketDynamics.afterTrade(initial, TradeDirection.BUY, 64, 200).currentTrend() > 0,
                "buying must increase demand pressure");
        require(MarketDynamics.afterTrade(initial, TradeDirection.SELL, 64, 200).currentTrend() < 0,
                "selling must increase supply pressure");
    }

    private static void validatesQuotesAndPayment() {
        MarketQuote quote = new MarketQuote("q", "p", "minecraft:stone", 16, TradeDirection.BUY, 1, 1, 2,
                "steady", "test", 100, 200, 500, false);
        require(!QuoteManager.canComplete(quote, "p", "minecraft:stone", 16, 150), "pending quotes must not complete");
        require(QuoteManager.canComplete(quote, "p", "minecraft:stone", 16, 250), "ready quote should complete");
        require(!QuoteManager.canComplete(quote.completedCopy(), "p", "minecraft:stone", 16, 250), "completed quote must be rejected");
        require(!QuoteManager.canComplete(quote, "p", "minecraft:dirt", 16, 250), "modified quote must be rejected");
        require(!QuoteManager.canComplete(quote, "p", "minecraft:stone", 16, 500), "expired quote must be rejected");
        require(!QuoteManager.canAfford(1, 2), "insufficient diamonds must be rejected");
    }

    private static void rejectsModdedIds() {
        require(MarketItemIds.isVanilla("minecraft:stone"), "vanilla item id should pass");
        require(!MarketItemIds.isVanilla("create:shaft"), "modded item id must fail");
    }

    private static void mapsOnlineIssuersExactly() {
        require(OnlineIssuerMapper.CATALOG_SIZE == 1332, "website and block must share all 1,332 item mappings");
        require(OnlineIssuerMapper.issuerFor("minecraft:iron_ingot", Set.of("#mineral")).equals("IRON"),
                "iron must follow the online metal issuer");
        require(OnlineIssuerMapper.issuerFor("minecraft:redstone", Set.of()).equals("WIRE"),
                "redstone must follow the online wiredrawers");
        require(OnlineIssuerMapper.issuerFor("minecraft:elytra", Set.of("#end", "#artifact")).equals("ADVT"),
                "End wares must follow the merchant adventurers");
        require(OnlineIssuerMapper.issuerFor("minecraft:oak_boat", Set.of("#travel")).equals("SHIP"),
                "boats must follow the shipwrights");
        require(OnlineIssuerMapper.issuerFor("minecraft:leather", Set.of()).equals("SKIN"),
                "raw leather must follow the skinners");
        require(OnlineIssuerMapper.TICKERS.contains(OnlineIssuerMapper.issuerFor("minecraft:stick", Set.of())),
                "every vanilla item must have a safe fallback issuer");
    }

    private static void validatesOnlineConnectionContract() {
        require(OnlineMarketClient.ENDPOINTS.size() == 2, "online market must have a primary and fallback endpoint");
        require(OnlineMarketClient.ENDPOINTS.get(0).toString().equals("https://royalexchange.net/api/market"),
                "easy custom domain must be the primary market endpoint");
        require(OnlineMarketClient.ENDPOINTS.get(1).getHost().endsWith("workers.dev"),
                "legacy Worker endpoint must remain as connection failover");
    }

    private static void validatesMarketScreenBounds() {
        require(com.colonybridge.market.trader.RoyalExchangeScreen.SCREEN_WIDTH <= 320,
                "Royal Exchange screen must fit Minecraft's minimum scaled width");
        require(com.colonybridge.market.trader.RoyalExchangeScreen.SCREEN_HEIGHT <= 240,
                "Royal Exchange screen must fit Minecraft's minimum scaled height");
    }

    private static void appliesValidatedOnlineMovements() {
        StringBuilder companies = new StringBuilder();
        boolean first = true;
        for (String ticker : OnlineIssuerMapper.TICKERS.stream().sorted().toList()) {
            if (!first) companies.append(',');
            first = false;
            double change = ticker.equals("IRON") ? 4.5 : 0;
            companies.append("{\"ticker\":\"").append(ticker).append("\",\"changePercent\":").append(change).append('}');
        }
        String json = "{\"exchange\":\"Royal Exchange\",\"mode\":\"autonomous-wall-clock\",\"asOf\":\"2027-01-15T08:00:00Z\",\"companies\":["
                + companies + "],\"events\":[{\"id\":\"rxa-test-1\",\"title\":\"Royal armament order sealed\","
                + "\"description\":\"The Guildhall has placed an exceptional order.\",\"severity\":\"severe\","
                + "\"phase\":\"onset\",\"affectedTickers\":[\"IRON\",\"ARMR\"],\"impactPercent\":14.5,"
                + "\"startedAt\":\"2027-01-15T07:55:00Z\",\"endsAt\":\"2027-01-15T11:00:00Z\"}]}";
        OnlineMarketSnapshot snapshot = OnlineMarketFeed.parse(json);
        OnlineMarketConfig config = new OnlineMarketConfig(true, 60, 360, 1.0).validated();
        OnlineMarketInfluence influence = snapshot.influence("minecraft:iron_ingot", Set.of("#mineral"),
                java.time.Instant.parse("2027-01-15T08:01:00Z").toEpochMilli(), config).orElseThrow();
        require(influence.ticker().equals("IRON") && close(influence.multiplier(), 1.045),
                "online percentage movement must multiply the mapped Minecraft price");
        require(snapshot.events().size() == 1 && snapshot.events().getFirst().severity().equals("severe")
                        && snapshot.events().getFirst().affectedTickers().contains("IRON"),
                "online anomaly notices must be parsed for one-time chat announcements");
        require(snapshot.influence("minecraft:iron_ingot", Set.of("#mineral"),
                java.time.Instant.parse("2027-01-16T08:00:00Z").toEpochMilli(), config).isEmpty(),
                "stale online prices must fall back to local valuation");
    }

    private static void persistsConsistently() throws Exception {
        var directory = Files.createTempDirectory("colonybridge-market-test");
        var path = directory.resolve("market.json");
        MarketState state = new MarketState(MarketState.CURRENT_VERSION, 77, Map.of("minecraft:stone",
                new MarketRecord("minecraft:stone", 0.1, 0.2, 0, 100, List.of(0.1))), List.of(), Map.of(), Set.of(),
                MarketConfig.defaults(), Map.of(), Set.of(), Map.of());
        MarketPersistence.save(path, state);
        MarketState loaded = MarketPersistence.load(path);
        require(loaded.seed() == 77 && loaded.records().containsKey("minecraft:stone"), "market persistence must round-trip");
    }

    private static void migratesVersionOneState() throws Exception {
        var directory = Files.createTempDirectory("colonybridge-market-v1-test");
        var path = directory.resolve("market.json");
        Files.writeString(path, "{\"version\":1,\"seed\":99,\"records\":{},\"activeEvents\":[],\"quotes\":{},\"completedQuoteIds\":[]}");
        MarketState loaded = MarketPersistence.load(path);
        require(loaded.version() == MarketState.CURRENT_VERSION && loaded.seed() == 99,
                "version one markets must migrate without losing their seed");
        require(loaded.contracts().isEmpty() && loaded.dailySellVolumes().isEmpty(),
                "new market collections must initialize during migration");
    }

    private static void rotatesContractsDeterministically() {
        var first = RoyalContractManager.template(42, 100, 0);
        var second = RoyalContractManager.template(42, 100, 0);
        require(first.equals(second), "contract rotation must be deterministic");
        require(MarketItemIds.isVanilla(first.itemId()) && first.quantity() > 0,
                "contract templates must request positive vanilla goods");
    }

    private static void validatesTraderRecipeFormat() throws Exception {
        try (var stream = MarketLogicTests.class.getResourceAsStream("/data/colonybridge/recipe/royal_exchange.json")) {
            require(stream != null, "Royal Exchange recipe resource must exist");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var key = json.getAsJsonObject("key");
            require(key.get("C").isJsonObject() && key.get("E").isJsonObject() && key.get("L").isJsonObject(),
                    "Minecraft 1.21.1 recipe ingredients must use object form");
        }
    }

    private static void validatesExchangeResources() throws Exception {
        List<String> jsonResources = List.of(
                "/assets/colonybridge/blockstates/royal_exchange.json",
                "/assets/colonybridge/lang/en_us.json",
                "/assets/colonybridge/models/block/royal_exchange.json",
                "/assets/colonybridge/models/item/royal_exchange.json",
                "/data/colonybridge/loot_table/blocks/royal_exchange.json",
                "/data/colonybridge/recipe/royal_exchange.json");
        for (String resource : jsonResources) {
            try (var stream = MarketLogicTests.class.getResourceAsStream(resource)) {
                require(stream != null, "missing Exchange resource: " + resource);
                com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        try (var texture = MarketLogicTests.class.getResourceAsStream("/assets/colonybridge/textures/block/royal_exchange.png")) {
            require(texture != null && texture.readAllBytes().length > 0, "Royal Exchange texture must be packaged");
        }
    }

    private static boolean close(double left, double right) { return Math.abs(left - right) < 0.000001; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
