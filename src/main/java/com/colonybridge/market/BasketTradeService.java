package com.colonybridge.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BasketTradeService {
    private BasketTradeService() { }

    public static void requireRequestedQuantities(List<BasketLine> requested, List<MarketQuote> quoted) {
        if (requested.size() != quoted.size()) throw new IllegalArgumentException("Basket items changed.");
        Map<String, Integer> required = new LinkedHashMap<>();
        for (int index = 0; index < requested.size(); index++) {
            BasketLine line = requested.get(index);
            MarketQuote quote = quoted.get(index);
            if (!line.itemId().equals(quote.itemId())) throw new IllegalArgumentException("Basket items changed.");
            if (quote.quantity() != line.quantity()) required.put(line.itemId(), quote.quantity());
        }
        if (!required.isEmpty()) throw new QuantityRequiredException(required);
    }

    public static Map<String, Integer> remainingQuantityWarnings(List<BasketLine> basket,
                                                                 Map<String, Integer> required) {
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (BasketLine line : basket) {
            int minimum = required.getOrDefault(line.itemId(), 0);
            if (line.quantity() < minimum) remaining.put(line.itemId(), minimum);
        }
        return remaining;
    }

    public static final class QuantityRequiredException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final HashMap<String, Integer> requiredQuantities;

        private QuantityRequiredException(Map<String, Integer> requiredQuantities) {
            super(requiredQuantities.size() == 1
                    ? "This item needs a larger sale quantity (at least " + requiredQuantities.values().iterator().next()
                            + ") for a quote. Adjust the basket."
                    : "These " + requiredQuantities.size() + " items need a larger sale quantity. Adjust the highlighted basket rows.");
            this.requiredQuantities = new HashMap<>(requiredQuantities);
        }

        public Map<String, Integer> requiredQuantities() { return Map.copyOf(requiredQuantities); }
    }

    public static String validate(PlayerInventoryPort inventory, BasketQuote quote, boolean completed,
                                  long now, MarketConfig config, int remainingSellAllowance) {
        if (inventory.restrictedGameMode()) return "Royal Exchange trades require Survival or Adventure mode.";
        if (completed) return "That quote has already been completed.";
        if (quote == null || !quote.playerId().equals(inventory.playerId()) || !quote.ready(now)) {
            return "That quote is no longer valid.";
        }
        if (quote.expired(now)) return "That quote has expired.";
        if (!config.sellingEnabled()) return "Royal Exchange selling is disabled.";
        if (quote.totalDiamonds() > remainingSellAllowance) {
            return "Your daily Exchange limit has " + remainingSellAllowance + " diamonds remaining.";
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (MarketQuote line : quote.lines()) {
            if (line.direction() != TradeDirection.SELL || !line.playerId().equals(inventory.playerId())
                    || !MarketItemIds.isTradable(line.itemId()) || line.quantity() < 1 || line.quantity() > 1024
                    || !seen.add(line.itemId()) || !line.ready(now) || line.expired(now)) {
                return "That quote is no longer valid.";
            }
            if (inventory.countPlainItems(line.itemId()) < line.quantity()) {
                return "You do not have enough unmodified items.";
            }
            total += line.totalDiamondCost();
        }
        return total == quote.totalDiamonds() ? null : "That quote is no longer valid.";
    }

    public static BasketInventoryMutation mutation(BasketQuote quote) {
        var lines = new ArrayList<BasketLine>();
        for (MarketQuote line : quote.lines()) {
            if (line.direction() != TradeDirection.SELL) throw new IllegalArgumentException("Basket contains a non-sale quote.");
            lines.add(new BasketLine(line.itemId(), line.quantity()));
        }
        return new BasketInventoryMutation(lines, quote.totalDiamonds());
    }
}
