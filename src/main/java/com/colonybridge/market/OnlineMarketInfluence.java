package com.colonybridge.market;

public record OnlineMarketInfluence(String ticker, double changePercent, double multiplier) {
    public String summary() {
        return ticker + " " + (changePercent >= 0 ? "+" : "")
                + String.format(java.util.Locale.ROOT, "%.2f%%", changePercent);
    }
}
