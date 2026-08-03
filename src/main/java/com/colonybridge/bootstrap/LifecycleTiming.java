package com.colonybridge.bootstrap;

import java.time.Duration;

public final class LifecycleTiming {
    public static final int TICKS_PER_SECOND = 20;
    public static final int CONFIG_REFRESH_INTERVAL_TICKS = TICKS_PER_SECOND;
    public static final int DAY_CHECK_INTERVAL_TICKS = TICKS_PER_SECOND;
    public static final int MARKET_REFRESH_INTERVAL_TICKS = TICKS_PER_SECOND;
    public static final Duration SHUTDOWN_EXPORT_TIMEOUT = Duration.ofSeconds(20);
    public static final Duration BACKGROUND_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration JOIN_EXPORT_DEDUP_WINDOW = Duration.ofSeconds(10);
    public static final Duration SHUTDOWN_EXPORT_DEDUP_WINDOW = Duration.ofSeconds(10);

    private LifecycleTiming() {
    }

    public static int ticksForSeconds(int seconds) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE,
                (long) Math.max(1, seconds) * TICKS_PER_SECOND));
    }
}
