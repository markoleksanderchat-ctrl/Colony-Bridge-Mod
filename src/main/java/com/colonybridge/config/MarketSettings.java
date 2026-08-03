package com.colonybridge.config;

import com.colonybridge.market.MarketConfig;
import com.colonybridge.market.OnlineMarketConfig;

import java.util.Objects;

public record MarketSettings(MarketConfig local, OnlineMarketConfig online) {
    public MarketSettings {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(online, "online");
    }
}
