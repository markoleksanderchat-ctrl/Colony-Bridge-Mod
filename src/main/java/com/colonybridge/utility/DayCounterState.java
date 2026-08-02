package com.colonybridge.utility;

import java.util.OptionalLong;

public final class DayCounterState {
    private long lastObservedDay = -1;

    public boolean initialized() {
        return lastObservedDay >= 0;
    }

    public void reset(long currentDay) {
        lastObservedDay = currentDay;
    }

    public OptionalLong observe(long currentDay) {
        if (lastObservedDay < 0 || currentDay < lastObservedDay) {
            lastObservedDay = currentDay;
            return OptionalLong.empty();
        }
        if (currentDay == lastObservedDay) {
            return OptionalLong.empty();
        }

        lastObservedDay = currentDay;
        return OptionalLong.of(currentDay);
    }
}
