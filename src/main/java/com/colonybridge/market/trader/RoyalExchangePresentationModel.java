package com.colonybridge.market.trader;

import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class RoyalExchangePresentationModel {
    private RoyalExchangePresentationModel() {
    }

    public static int quantityStep(int quantity) {
        if (quantity < 8) return 1;
        if (quantity < 16) return 4;
        if (quantity < 32) return 8;
        if (quantity < 64) return 16;
        if (quantity < 128) return 32;
        if (quantity < 256) return 64;
        if (quantity < 512) return 128;
        return 256;
    }

    public static int clampScroll(int scroll, int itemCount) {
        return Math.max(0, Math.min(Math.max(0, itemCount - RoyalExchangeLayout.VISIBLE_ROWS), scroll));
    }

    public static String fit(String text, int maximumWidth, ToIntFunction<String> width,
                             Function<Integer, String> substringByWidth) {
        if (width.applyAsInt(text) <= maximumWidth) return text;
        String ellipsis = "...";
        return substringByWidth.apply(Math.max(0, maximumWidth - width.applyAsInt(ellipsis))).stripTrailing() + ellipsis;
    }

    public static Controls controls(boolean contractView, boolean sellMode, int quoteState, int contractCount) {
        return new Controls(!contractView, !contractView && quoteState == 2,
                contractView || sellMode, contractView || !sellMode, !contractView,
                contractView && contractCount > 1, contractView && contractCount > 0,
                sellMode ? "Request Sell Quote" : "Request Buy Quote",
                sellMode ? "Sell Goods" : "Buy Goods");
    }

    public record Controls(boolean tradeControlsVisible, boolean completeTradeVisible,
                           boolean buyModeActive, boolean sellModeActive, boolean contractsModeActive,
                           boolean contractNavigationVisible, boolean completeContractVisible,
                           String quoteLabel, String tradeLabel) {
    }
}
