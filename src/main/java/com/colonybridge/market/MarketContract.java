package com.colonybridge.market;

public record MarketContract(String id, String itemId, int quantity, int rewardDiamonds,
                             String title, String description, long creationTime, long expirationTime) {
    public boolean expired(long now) { return now >= expirationTime; }
}
