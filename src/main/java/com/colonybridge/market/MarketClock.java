package com.colonybridge.market;

import java.time.Instant;
import java.time.ZoneOffset;

public interface MarketClock {
    long nowMillis();

    default long utcEpochDay() {
        return Instant.ofEpochMilli(nowMillis()).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
    }
}
