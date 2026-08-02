package com.colonybridge.market;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record MarketState(int version, long seed, Map<String, MarketRecord> records,
                          List<MarketEvent> activeEvents, Map<String, MarketQuote> quotes,
                          Set<String> completedQuoteIds, MarketConfig configuration,
                          Map<String, MarketContract> contracts, Set<String> completedContractIds,
                          Map<String, DailySellVolume> dailySellVolumes) {
    public static final int CURRENT_VERSION = 2;
}
