package com.colonybridge.market;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class BasketTradeTests {
    @Test void identifiesTheGoodAndMinimumFromTheActualSaleQuote() {
        String itemId = "minecraft:copper_block";
        MarketQuote quoted = saleQuote(itemId, 9, 24);

        BasketTradeService.QuantityRequiredException failure = assertThrows(
                BasketTradeService.QuantityRequiredException.class,
                () -> BasketTradeService.requireRequestedQuantities(List.of(new BasketLine(itemId, 9)), List.of(quoted)));
        assertEquals(Map.of(itemId, 24), failure.requiredQuantities());
        assertDoesNotThrow(() -> BasketTradeService.requireRequestedQuantities(
                List.of(new BasketLine(itemId, 24)), List.of(saleQuote(itemId, 24, 24))));
        for (MarketQuote other : quote().lines()) {
            assertDoesNotThrow(() -> BasketTradeService.requireRequestedQuantities(
                    List.of(new BasketLine(other.itemId(), other.quantity())), List.of(other)));
        }
    }

    @Test void reportsEveryInsufficientGoodTogetherAndRechecksAfterCorrection() {
        List<BasketLine> basket = List.of(new BasketLine("minecraft:stone", 1),
                new BasketLine("minecraft:oak_log", 16), new BasketLine("minecraft:copper_block", 9));
        List<MarketQuote> quoted = List.of(saleQuote("minecraft:stone", 1, 4),
                saleQuote("minecraft:oak_log", 16, 16), saleQuote("minecraft:copper_block", 9, 24));
        var failure = assertThrows(BasketTradeService.QuantityRequiredException.class,
                () -> BasketTradeService.requireRequestedQuantities(basket, quoted));
        assertEquals(Map.of("minecraft:stone", 4, "minecraft:copper_block", 24), failure.requiredQuantities());

        List<BasketLine> corrected = List.of(new BasketLine("minecraft:stone", 4), basket.get(1), basket.get(2));
        List<MarketQuote> correctedQuotes = List.of(saleQuote("minecraft:stone", 4, 4), quoted.get(1), quoted.get(2));
        var remaining = assertThrows(BasketTradeService.QuantityRequiredException.class,
                () -> BasketTradeService.requireRequestedQuantities(corrected, correctedQuotes));
        assertEquals(Map.of("minecraft:copper_block", 24), remaining.requiredQuantities());

        assertDoesNotThrow(() -> BasketTradeService.requireRequestedQuantities(
                List.of(corrected.get(0), corrected.get(1), new BasketLine("minecraft:copper_block", 24)),
                List.of(correctedQuotes.get(0), correctedQuotes.get(1), saleQuote("minecraft:copper_block", 24, 24))));
    }

    @Test void reportsAllNineInsufficientGoodsWithSeparateMinimums() {
        List<String> items = List.of("minecraft:stone", "minecraft:oak_log", "minecraft:copper_block",
                "minecraft:dirt", "minecraft:cobblestone", "minecraft:lead", "minecraft:stick",
                "minecraft:coal", "minecraft:iron_ingot");
        int[] minimums = {2, 4, 8, 12, 16, 24, 32, 48, 64};
        List<BasketLine> basket = new ArrayList<>();
        List<MarketQuote> quoted = new ArrayList<>();
        Map<String, Integer> expected = new HashMap<>();
        for (int index = 0; index < items.size(); index++) {
            String item = items.get(index);
            basket.add(new BasketLine(item, 1));
            quoted.add(saleQuote(item, 1, minimums[index]));
            expected.put(item, minimums[index]);
        }
        var failure = assertThrows(BasketTradeService.QuantityRequiredException.class,
                () -> BasketTradeService.requireRequestedQuantities(basket, quoted));
        assertEquals(expected, failure.requiredQuantities());
    }

    @Test void keepsOtherWarningsWhenAnItemIsUpdatedOrRemoved() {
        Map<String, Integer> warnings = Map.of("minecraft:stone", 4, "minecraft:copper_block", 24);
        BasketLine copper = new BasketLine("minecraft:copper_block", 9);
        assertEquals(warnings, BasketTradeService.remainingQuantityWarnings(
                List.of(new BasketLine("minecraft:stone", 2), copper), warnings));
        assertEquals(Map.of("minecraft:copper_block", 24), BasketTradeService.remainingQuantityWarnings(
                List.of(new BasketLine("minecraft:stone", 4), copper), warnings));
        assertEquals(Map.of("minecraft:copper_block", 24),
                BasketTradeService.remainingQuantityWarnings(List.of(copper), warnings));
        assertTrue(BasketTradeService.remainingQuantityWarnings(
                List.of(new BasketLine("minecraft:copper_block", 24)), warnings).isEmpty());
        assertTrue(BasketTradeService.remainingQuantityWarnings(List.of(), warnings).isEmpty());
    }

    private static MarketQuote saleQuote(String itemId, int quantity, int minimum) {
        TradeValueInput input = new TradeValueInput(itemId, itemId, 1,
                1, 1, 0, 1, 0, 0, 0, 1, 1, 1, 1, 0.5, 1);
        ClassifiedItem classified = new ClassifiedItem(input, 0.3, Set.of(), "labor", "supply", "", null);
        MarketConfig config = MarketConfig.defaults();
        double unitPrice = 1.0 / (minimum * config.sellPriceRatio());
        return QuoteLifecycleService.create(itemId, "player", classified, itemId, quantity,
                TradeDirection.SELL, unitPrice, unitPrice, null, List.of(), 0, config);
    }

    private static BasketQuote quote() {
        return new BasketQuote("basket", "player", List.of(
                new MarketQuote("one", "player", "minecraft:stone", 4, TradeDirection.SELL,
                        3, 3, 3, "steady", "", 0, 10, 100, false),
                new MarketQuote("two", "player", "minecraft:oak_log", 8, TradeDirection.SELL,
                        5, 5, 5, "steady", "", 0, 10, 100, false)), 8, 10, 100);
    }

    @Test void validatesWholeBasketBeforeSelling() {
        Inventory inventory = new Inventory();
        BasketQuote quote = quote();
        assertNull(BasketTradeService.validate(inventory, quote, false, 20, MarketConfig.defaults(), 64));
        assertTrue(BasketTradeService.validate(inventory, quote, false, 9, MarketConfig.defaults(), 64).contains("valid"));
        assertTrue(BasketTradeService.validate(inventory, quote, false, 100, MarketConfig.defaults(), 64).contains("expired"));
        assertTrue(BasketTradeService.validate(inventory, quote, true, 20, MarketConfig.defaults(), 64).contains("completed"));
        assertTrue(BasketTradeService.validate(inventory, quote, false, 20, MarketConfig.defaults(), 7).contains("daily"));
        inventory.goods.put("minecraft:oak_log", 7);
        assertTrue(BasketTradeService.validate(inventory, quote, false, 20, MarketConfig.defaults(), 64).contains("unmodified"));
    }

    @Test void rejectsDuplicateOrCurrencyBasketLines() {
        assertThrows(IllegalArgumentException.class, () -> new BasketLine("minecraft:diamond", 1));
        assertThrows(IllegalArgumentException.class, () -> new BasketLine("minecraft:stone", 1025));
        assertThrows(IllegalArgumentException.class, () -> new BasketInventoryMutation(
                List.of(new BasketLine("minecraft:stone", 1), new BasketLine("minecraft:stone", 2)), 1));
    }

    @Test void basketSaleAndPersistenceRollbackTogether() {
        Inventory inventory = new Inventory();
        BasketInventoryMutation mutation = BasketTradeService.mutation(quote());
        int[] marketUpdates = {0};
        var failed = MarketTransactionCoordinator.execute(inventory, mutation,
                () -> marketUpdates[0]++, () -> marketUpdates[0]--,
                () -> { throw new IOException("disk"); }, "sold");
        assertFalse(failed.success());
        assertEquals(0, marketUpdates[0]);
        assertEquals(4, inventory.goods.get("minecraft:stone"));
        assertEquals(8, inventory.goods.get("minecraft:oak_log"));
        assertEquals(0, inventory.goods.get("minecraft:diamond"));

        var sold = MarketTransactionCoordinator.execute(inventory, mutation,
                () -> marketUpdates[0]++, () -> marketUpdates[0]--, () -> {}, "sold");
        assertTrue(sold.success());
        assertEquals(1, marketUpdates[0]);
        assertEquals(0, inventory.goods.get("minecraft:stone"));
        assertEquals(0, inventory.goods.get("minecraft:oak_log"));
        assertEquals(8, inventory.goods.get("minecraft:diamond"));
    }

    @Test void partialInventoryFailureRestoresEveryLine() {
        Inventory inventory = new Inventory();
        inventory.failAfterFirst = true;
        var result = MarketTransactionCoordinator.execute(inventory, BasketTradeService.mutation(quote()),
                () -> fail("market must not update"), () -> fail("market must not roll back"),
                () -> fail("must not persist"), "sold");
        assertFalse(result.success());
        assertEquals(4, inventory.goods.get("minecraft:stone"));
        assertEquals(8, inventory.goods.get("minecraft:oak_log"));
        assertEquals(0, inventory.goods.get("minecraft:diamond"));
    }

    private static final class Inventory implements PlayerInventoryPort {
        private boolean failAfterFirst;
        private Map<String, Integer> goods = new HashMap<>(Map.of(
                "minecraft:stone", 4, "minecraft:oak_log", 8, "minecraft:diamond", 0));

        @Override public String playerId() { return "player"; }
        @Override public boolean restrictedGameMode() { return false; }
        @Override public int countDiamonds() { return goods.get("minecraft:diamond"); }
        @Override public int countPlainItems(String itemId) { return goods.getOrDefault(itemId, 0); }
        @Override public PendingInventoryMutation prepare(InventoryMutation mutation) {
            throw new UnsupportedOperationException();
        }
        @Override public PendingInventoryMutation prepareBasket(BasketInventoryMutation mutation) {
            Map<String, Integer> before = new HashMap<>(goods);
            return new PendingInventoryMutation() {
                @Override public void apply() {
                    for (BasketLine line : mutation.lines()) {
                        goods.merge(line.itemId(), -line.quantity(), Integer::sum);
                        if (failAfterFirst) throw new IllegalStateException("inventory changed during sale");
                    }
                    goods.merge("minecraft:diamond", mutation.diamonds(), Integer::sum);
                }
                @Override public void commit() { }
                @Override public void rollback() { goods = before; }
            };
        }
    }
}
