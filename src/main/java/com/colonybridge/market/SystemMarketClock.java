package com.colonybridge.market;

public enum SystemMarketClock implements MarketClock {
    INSTANCE;

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
