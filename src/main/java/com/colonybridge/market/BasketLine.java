package com.colonybridge.market;

public record BasketLine(String itemId, int quantity) {
    public BasketLine {
        if (!MarketItemIds.isTradable(itemId) || quantity < 1 || quantity > 1024) {
            throw new IllegalArgumentException("Basket lines require 1–1024 tradable vanilla items.");
        }
    }
}
