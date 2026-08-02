package com.colonybridge.market;

import java.util.Set;

public record ClassifiedItem(TradeValueInput input, double volatility, Set<String> tags,
                             String strongestIncrease, String strongestDecrease, String loreSummary,
                             Double fixedDiamondValue) {
}
