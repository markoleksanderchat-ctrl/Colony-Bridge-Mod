package com.colonybridge.market;

import com.colonybridge.market.trader.RoyalExchangeLayout;
import com.colonybridge.market.trader.RoyalExchangeMenu;
import com.colonybridge.market.trader.RoyalExchangePresentationModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class Phase6MarketArchitectureTests {
    private Phase6MarketArchitectureTests() {
    }

    public static void run() throws Exception {
        matchesFrozenDomainFixtures();
        commitsTransactionsExactlyOnce();
        rollsBackInventoryAndStateWhenPersistenceFails();
        validatesTradeAndContractFailuresBeforeMutation();
        isolatesOnlineParsingFailoverCacheInfluenceAndAnomalies();
        validatesIssuerCatalogCoverageAndChecksum();
        preservesMenuAndPresentationContracts();
        preservesClientInputAndJeiBoundaries();
    }

    private static void matchesFrozenDomainFixtures() throws Exception {
        JsonObject fixtures = fixture();
        TradeValueInput input = new TradeValueInput("minecraft:test", "Test", 1, 4, 3, 2, 1, 0, 2, 1,
                1.2, 1, 1.12, 1, 0.65, 0.8);
        TradeValueResult price = PriceCalculator.calculate(input);
        JsonObject expectedPrice = fixtures.getAsJsonObject("pricing");
        requireClose(expectedPrice.get("intrinsicValue").getAsDouble(), price.intrinsicValue(), "pricing intrinsic fixture");
        requireClose(expectedPrice.get("rawDiamondValue").getAsDouble(), price.rawDiamondValue(), "pricing diamond fixture");
        requireClose(expectedPrice.get("minimumValue").getAsDouble(), price.lowerEstimate(), "pricing minimum fixture");
        requireClose(expectedPrice.get("maximumValue").getAsDouble(), price.upperEstimate(), "pricing maximum fixture");
        requireEquals(expectedPrice.get("classification").getAsString(), price.treasuryClass(), "pricing classification fixture");

        JsonObject classificationFixture = fixtures.getAsJsonObject("itemClassification");
        ClassifiedItem iron = VanillaClassificationDomain.classify(new ItemClassificationFacts(
                classificationFixture.get("itemId").getAsString(), "Iron Ingot", 1, 0, 64,
                false, false, false, false, false, false, false, false, false, 0));
        requireEquals(Set.of("#bulk", "#mineral"), iron.tags(), "classification tags fixture");
        requireClose(classificationFixture.get("rarity").getAsDouble(), iron.input().rarity(), "classification rarity fixture");
        requireClose(classificationFixture.get("labor").getAsDouble(), iron.input().labor(), "classification labor fixture");
        requireClose(classificationFixture.get("equipmentGate").getAsDouble(), iron.input().equipmentGate(), "classification equipment fixture");
        requireClose(classificationFixture.get("wholesaleSuitability").getAsDouble(),
                iron.input().wholesaleSuitability(), "classification wholesale fixture");
        requireClose(classificationFixture.get("volatility").getAsDouble(), iron.volatility(), "classification volatility fixture");
        requireEquals(classificationFixture.get("restraint").getAsString(), iron.strongestDecrease(),
                "classification restraint fixture");

        JsonObject expectedQuote = fixtures.getAsJsonObject("quote");
        ClassifiedItem classified = new ClassifiedItem(input, 0.3, Set.of("#ordinary"),
                "labor and utility", "ordinary replaceability", "method", null);
        MarketQuote quote = QuoteLifecycleService.create(expectedQuote.get("id").getAsString(),
                expectedQuote.get("playerId").getAsString(), classified, expectedQuote.get("itemId").getAsString(),
                expectedQuote.get("requestedQuantity").getAsInt(), TradeDirection.BUY, 0.5, 0.6,
                null, List.of(), expectedQuote.get("createdAt").getAsLong(), MarketConfig.defaults());
        requireEquals(expectedQuote.get("quantity").getAsInt(), quote.quantity(), "quote quantity fixture");
        requireClose(expectedQuote.get("basePrice").getAsDouble(), quote.basePrice(), "quote base fixture");
        requireClose(expectedQuote.get("currentPrice").getAsDouble(), quote.currentPrice(), "quote current fixture");
        requireEquals(expectedQuote.get("diamonds").getAsInt(), quote.totalDiamondCost(), "quote diamonds fixture");
        requireEquals(expectedQuote.get("condition").getAsString(), quote.marketCondition(), "quote condition fixture");
        requireEquals(expectedQuote.get("readyAt").getAsLong(), quote.readyTime(), "quote readiness fixture");
        requireEquals(expectedQuote.get("expiresAt").getAsLong(), quote.expirationTime(), "quote expiry fixture");

        JsonObject eventFixture = fixtures.getAsJsonObject("event");
        MarketEvent event = new MarketEvent("fixture", "Fixture", "Fixture", List.of("#ordinary"), 0.2, 1_000, 1_000);
        requireClose(eventFixture.get("strengthAtStart").getAsDouble(), event.strengthAt(1_000), "event start fixture");
        requireClose(eventFixture.get("strengthAtHalf").getAsDouble(), event.strengthAt(1_500), "event half fixture");
        requireClose(eventFixture.get("strengthAtEnd").getAsDouble(), event.strengthAt(2_000), "event end fixture");

        JsonObject contractFixture = fixtures.getAsJsonObject("contract");
        RoyalContractManager.ContractTemplate template = RoyalContractManager.template(contractFixture.get("seed").getAsLong(),
                contractFixture.get("bucket").getAsLong(), contractFixture.get("index").getAsInt());
        requireEquals(contractFixture.get("itemId").getAsString(), template.itemId(), "contract item fixture");
        requireEquals(contractFixture.get("quantity").getAsInt(), template.quantity(), "contract quantity fixture");

        JsonObject daily = fixtures.getAsJsonObject("dailyLimit");
        DailySellVolume volume = new DailySellVolume(daily.get("epochDay").getAsLong(), daily.get("alreadyPaid").getAsInt());
        requireEquals(daily.get("remaining").getAsInt(), MarketDomainOperations.remainingSellAllowance(volume,
                daily.get("epochDay").getAsLong(), daily.get("limit").getAsInt()), "daily limit fixture");
        requireEquals(daily.get("afterSale").getAsInt(), MarketDomainOperations.recordSale(volume,
                daily.get("epochDay").getAsLong(), daily.get("nextSale").getAsInt()).diamondsPaid(), "daily sale fixture");
    }

    private static void commitsTransactionsExactlyOnce() {
        FakeInventory inventory = new FakeInventory("player", false, Map.of("minecraft:diamond", 10));
        int[] state = {0};
        int[] saves = {0};
        MarketTransactionCoordinator.Result result = MarketTransactionCoordinator.execute(inventory,
                new InventoryMutation("minecraft:diamond", 3, "minecraft:stone", 16),
                () -> state[0]++, () -> state[0]--, () -> saves[0]++, "done");
        require(result.success(), "valid transaction must succeed");
        requireEquals(7, inventory.count("minecraft:diamond"), "buy removes payment once");
        requireEquals(16, inventory.count("minecraft:stone"), "buy grants goods once");
        requireEquals(1, inventory.applyCount, "inventory mutation count");
        requireEquals(1, inventory.commitCount, "inventory commit count");
        requireEquals(1, state[0], "market state update count");
        requireEquals(1, saves[0], "persistence count");
    }

    private static void rollsBackInventoryAndStateWhenPersistenceFails() {
        FakeInventory inventory = new FakeInventory("player", false, Map.of("minecraft:stone", 16));
        int[] state = {4};
        MarketTransactionCoordinator.Result result = MarketTransactionCoordinator.execute(inventory,
                new InventoryMutation("minecraft:stone", 16, "minecraft:diamond", 3),
                () -> state[0] = 5, () -> state[0] = 4,
                () -> { throw new IOException("disk full"); }, "done");
        require(!result.success(), "persistence failure must fail the transaction");
        requireEquals(16, inventory.count("minecraft:stone"), "failed sell restores goods");
        requireEquals(0, inventory.count("minecraft:diamond"), "failed sell removes staged reward");
        requireEquals(4, state[0], "failed sell restores market state");
        requireEquals(1, inventory.applyCount, "failed transaction applies inventory once");
        requireEquals(1, inventory.rollbackCount, "failed transaction rolls inventory back once");
    }

    private static void validatesTradeAndContractFailuresBeforeMutation() {
        long now = 10_000;
        MarketQuote buy = new MarketQuote("q", "player", "minecraft:stone", 16, TradeDirection.BUY,
                1, 1, 3, "steady", "fixture", 1_000, 2_000, 20_000, false);
        FakeInventory survival = new FakeInventory("player", false, Map.of("minecraft:diamond", 2));
        requireEquals("You do not have enough diamonds.", TradeExecutionService.validate(survival, buy, false,
                buy.itemId(), buy.quantity(), now, MarketConfig.defaults(), true, 64), "insufficient payment rejection");
        FakeInventory creative = new FakeInventory("player", true, Map.of("minecraft:diamond", 64));
        require(TradeExecutionService.validate(creative, buy, false, buy.itemId(), buy.quantity(), now,
                MarketConfig.defaults(), true, 64).contains("Survival or Adventure"), "creative trade rejection");
        require(TradeExecutionService.validate(survival, buy.completedCopy(), true, buy.itemId(), buy.quantity(), now,
                MarketConfig.defaults(), true, 64).contains("already"), "duplicate quote rejection");
        require(TradeExecutionService.validate(survival, buy, false, "minecraft:dirt", buy.quantity(), now,
                MarketConfig.defaults(), true, 64).contains("no longer valid"), "modified item rejection");
        require(TradeExecutionService.validate(survival, buy, false, buy.itemId(), buy.quantity(), 20_000,
                MarketConfig.defaults(), true, 64).contains("expired"), "quote expiry rejection");

        MarketQuote sell = new MarketQuote("s", "player", "minecraft:stone", 16, TradeDirection.SELL,
                1, 1, 8, "steady", "fixture", 1_000, 2_000, 20_000, false);
        FakeInventory modifiedOnly = new FakeInventory("player", false, Map.of());
        require(TradeExecutionService.validate(modifiedOnly, sell, false, sell.itemId(), sell.quantity(), now,
                MarketConfig.defaults(), true, 64).contains("unmodified"), "modified goods rejection");
        FakeInventory goods = new FakeInventory("player", false, Map.of("minecraft:stone", 16));
        require(TradeExecutionService.validate(goods, sell, false, sell.itemId(), sell.quantity(), now,
                MarketConfig.defaults(), true, 7).contains("7 diamonds"), "daily sale limit rejection");
        requireEquals("minecraft:diamond", TradeExecutionService.mutation(buy).removeItemId(), "buy direction payment");
        requireEquals("minecraft:stone", TradeExecutionService.mutation(sell).removeItemId(), "sell direction goods");

        MarketContract contract = new MarketContract("c", "minecraft:stone", 16, 4, "title", "body", 1_000, 20_000);
        require(ContractExecutionService.validate(goods, contract, true, now, true).contains("no longer available"),
                "duplicate contract rejection");
        require(ContractExecutionService.validate(modifiedOnly, contract, false, now, true).contains("unmodified"),
                "modified contract goods rejection");
        requireEquals(0, survival.applyCount + creative.applyCount + modifiedOnly.applyCount + goods.applyCount,
                "validation failures must not mutate inventory");
    }

    private static void isolatesOnlineParsingFailoverCacheInfluenceAndAnomalies() {
        URI first = URI.create("https://first.invalid/market");
        URI second = URI.create("https://second.invalid/market");
        List<URI> attempts = new java.util.ArrayList<>();
        OnlineEndpointFailover.EndpointResult<String> fetched = OnlineEndpointFailover.fetch(List.of(first, second), endpoint -> {
            attempts.add(endpoint);
            return endpoint.equals(first) ? CompletableFuture.failedFuture(new IOException("offline"))
                    : CompletableFuture.completedFuture("ok");
        }).join();
        requireEquals(List.of(first, second), attempts, "endpoint failover order");
        requireEquals(second, fetched.endpoint(), "endpoint failover result");
        requireEquals("ok", fetched.value(), "endpoint failover value");
        requireThrows(() -> OnlineMarketFeed.parse("{}"), "malformed feed rejection");
        requireEquals(2 * 1024 * 1024, OnlineMarketResponse.MAX_BYTES, "online response size bound");

        OnlineAnomalyInbox inbox = new OnlineAnomalyInbox();
        OnlineMarketEvent event = new OnlineMarketEvent("rxa-fixture", "Fixture", "Fixture", "material", "onset",
                List.of("IRON"), 2, 1_000, 2_000);
        inbox.accept(List.of(), List.of(event));
        inbox.accept(List.of(), List.of(event));
        requireEquals(List.of(event), inbox.drain(event.startedAtEpochMillis()), "anomaly delivery is deduplicated");
        require(inbox.drain(event.startedAtEpochMillis()).isEmpty(), "anomaly delivery drains once");

        OnlineMarketCache brokenCache = new OnlineMarketCache() {
            @Override public OnlineMarketSnapshot load() throws IOException { throw new IOException("broken cache"); }
            @Override public void save(OnlineMarketSnapshot snapshot) throws IOException { throw new IOException("broken cache"); }
        };
        requireThrows(brokenCache::load, "cache read failure is isolated");
        OnlineMarketSnapshot stale = new OnlineMarketSnapshot(1_000,
                OnlineIssuerMapper.TICKERS.stream().collect(java.util.stream.Collectors.toMap(ticker -> ticker, ticker -> 0.0)),
                List.of());
        require(stale.influence("minecraft:iron_ingot", Set.of("#mineral"), 100_000_000,
                new OnlineMarketConfig(true, 60, 60, 1)).isEmpty(), "stale cache falls back to local pricing");
    }

    private static void validatesIssuerCatalogCoverageAndChecksum() throws Exception {
        requireEquals(1332, OnlineIssuerMapper.catalog().size(), "issuer catalog item count");
        requireEquals(38, OnlineIssuerMapper.TICKERS.size(), "issuer ticker count");
        requireEquals(OnlineIssuerMapper.CATALOG_CHECKSUM, OnlineIssuerMapper.checksum(OnlineIssuerMapper.catalog()),
                "issuer coverage checksum");
        requireEquals(OnlineIssuerMapper.TICKERS, Set.copyOf(OnlineIssuerMapper.catalog().values()),
                "every issuer has mapped items");
        for (String itemId : OnlineIssuerMapper.catalog().keySet()) {
            require(itemId.matches("minecraft:[a-z0-9_]+"), "mapped vanilla item id is invalid: " + itemId);
        }
    }

    private static void preservesMenuAndPresentationContracts() {
        requireEquals(1_000, RoyalExchangeMenu.SELECT_ITEM_BASE, "item selection button base");
        requireEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), List.of(
                RoyalExchangeMenu.DECREASE_QUANTITY, RoyalExchangeMenu.INCREASE_QUANTITY,
                RoyalExchangeMenu.REQUEST_QUOTE, RoyalExchangeMenu.COMPLETE_TRADE, RoyalExchangeMenu.BUY_MODE,
                RoyalExchangeMenu.SELL_MODE, RoyalExchangeMenu.CONTRACTS_VIEW, RoyalExchangeMenu.PREV_CONTRACT,
                RoyalExchangeMenu.NEXT_CONTRACT, RoyalExchangeMenu.COMPLETE_CONTRACT), "menu button ids");
        requireEquals(94, RoyalExchangeMenu.DATA_CONTRACT_ROWS, "contract rows append after existing slots");
        requireEquals(119, RoyalExchangeMenu.DATA_COUNT, "menu data slot count including bounded contract page");
        requireEquals(0, RoyalExchangeMenu.DATA_SELECTED_ITEM, "first data slot");
        requireEquals(18, RoyalExchangeMenu.DATA_ONLINE_STATE, "last data slot");
        require(RoyalExchangeLayout.WIDTH <= RoyalExchangeLayout.MINIMUM_SCALED_WIDTH
                && RoyalExchangeLayout.HEIGHT <= RoyalExchangeLayout.MINIMUM_SCALED_HEIGHT, "minimum GUI resolution");
        requireEquals(4, RoyalExchangePresentationModel.quantityStep(8), "quantity step boundary");
        requireEquals("Long...", RoyalExchangePresentationModel.fit("Long label", 7, String::length,
                width -> "Long label".substring(0, width)), "text fitting");
        RoyalExchangePresentationModel.Controls buy = RoyalExchangePresentationModel.controls(false, false, 2, 3);
        require(buy.completeTradeVisible() && buy.tradeLabel().equals("Buy Goods"), "buy presentation direction");
        RoyalExchangePresentationModel.Controls sell = RoyalExchangePresentationModel.controls(false, true, 2, 3);
        require(sell.completeTradeVisible() && sell.tradeLabel().equals("Sell Goods"), "sell presentation direction");
    }

    private static void preservesClientInputAndJeiBoundaries() throws Exception {
        String screen = Files.readString(Path.of("src/main/java/com/colonybridge/market/trader/RoyalExchangeScreen.java"));
        String menu = Files.readString(Path.of("src/main/java/com/colonybridge/market/trader/RoyalExchangeMenu.java"));
        String jei = Files.readString(Path.of("src/main/java/com/colonybridge/market/trader/RoyalExchangeJeiPlugin.java"));
        require(screen.contains("search.isFocused()") && screen.contains("GLFW.GLFW_KEY_ESCAPE")
                && screen.contains("search.setFocused(false)"), "search focus and Escape handling");
        require(jei.contains("guiLeft() { return 0; }") && jei.contains("guiXSize() { return screenWidth; }"),
                "JEI overlay suppression remains full-screen");
        require(menu.contains("manager.requestQuote(serverPlayer, selected")
                && menu.contains("manager.completeTrade(serverPlayer, activeQuote.id(),")
                && menu.contains("activeQuote.itemId(), activeQuote.quantity())"),
                "client never supplies authoritative price");
        require(!screen.contains("MarketManager") && !screen.contains("MarketPersistence"),
                "screen remains presentation-only");
    }

    private static JsonObject fixture() throws Exception {
        try (var stream = Phase6MarketArchitectureTests.class.getResourceAsStream("/phase6-market-fixtures.json")) {
            require(stream != null, "Phase 6 market fixture must exist");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static final class FakeInventory implements PlayerInventoryPort {
        private final String playerId;
        private final boolean restricted;
        private final Map<String, Integer> counts = new HashMap<>();
        private int applyCount;
        private int commitCount;
        private int rollbackCount;

        private FakeInventory(String playerId, boolean restricted, Map<String, Integer> counts) {
            this.playerId = playerId;
            this.restricted = restricted;
            this.counts.putAll(counts);
        }

        @Override public String playerId() { return playerId; }
        @Override public boolean restrictedGameMode() { return restricted; }
        @Override public int countDiamonds() { return count("minecraft:diamond"); }
        @Override public int countPlainItems(String itemId) { return count(itemId); }
        private int count(String itemId) { return counts.getOrDefault(itemId, 0); }

        @Override
        public PendingInventoryMutation prepare(InventoryMutation mutation) {
            Map<String, Integer> before = Map.copyOf(counts);
            return new PendingInventoryMutation() {
                @Override public void apply() {
                    applyCount++;
                    counts.put(mutation.removeItemId(), count(mutation.removeItemId()) - mutation.removeCount());
                    counts.put(mutation.grantItemId(), count(mutation.grantItemId()) + mutation.grantCount());
                }
                @Override public void commit() { commitCount++; }
                @Override public void rollback() { rollbackCount++; counts.clear(); counts.putAll(before); }
            };
        }
    }

    private static void requireThrows(ThrowingAction action, String message) {
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError(message);
    }
    @FunctionalInterface private interface ThrowingAction { void run() throws Exception; }
    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000000001) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void requireEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
}
