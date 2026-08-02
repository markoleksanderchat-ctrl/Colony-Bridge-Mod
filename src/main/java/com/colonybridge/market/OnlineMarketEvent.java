package com.colonybridge.market;

import java.util.List;

public record OnlineMarketEvent(String id, String title, String description, String severity, String phase,
                                List<String> affectedTickers, double impactPercent,
                                long startedAtEpochMillis, long endsAtEpochMillis) {
    public OnlineMarketEvent {
        affectedTickers = List.copyOf(affectedTickers);
    }
}
