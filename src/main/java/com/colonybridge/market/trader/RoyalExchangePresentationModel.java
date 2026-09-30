package com.colonybridge.market.trader;

import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class RoyalExchangePresentationModel {
    private RoyalExchangePresentationModel() {
    }

    public static int quoteState(int current, boolean ready, boolean expired) {
        if (current != 1 && current != 2) return current;
        return expired ? 5 : ready ? 2 : current;
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
        return Math.max(0, Math.min(Math.max(0, catalogRows(itemCount) - RoyalExchangeLayout.VISIBLE_ROWS), scroll));
    }

    public static int catalogRows(int itemCount) {
        return Math.max(0, itemCount) / RoyalExchangeLayout.CATALOG_COLUMNS
                + (itemCount > 0 && itemCount % RoyalExchangeLayout.CATALOG_COLUMNS != 0 ? 1 : 0);
    }

    public static int catalogIndex(int scroll, int cell) {
        return Math.max(0, scroll) * RoyalExchangeLayout.CATALOG_COLUMNS + cell;
    }

    public static int catalogIndexAt(int scroll, double x, double y) {
        if (x < RoyalExchangeLayout.ITEM_LIST_LEFT || x >= RoyalExchangeLayout.ITEM_LIST_RIGHT
                || y < RoyalExchangeLayout.ITEM_LIST_TOP || y >= RoyalExchangeLayout.ITEM_LIST_BOTTOM) return -1;
        int column = (int) (x - RoyalExchangeLayout.ITEM_LIST_LEFT) / RoyalExchangeLayout.ITEM_ROW_HEIGHT;
        int row = (int) (y - RoyalExchangeLayout.ITEM_LIST_TOP) / RoyalExchangeLayout.ITEM_ROW_HEIGHT;
        return catalogIndex(scroll, row * RoyalExchangeLayout.CATALOG_COLUMNS + column);
    }

    public static Scrollbar scrollbar(int itemCount, int scroll) {
        int trackTop = RoyalExchangeLayout.SCROLLBAR_TOP;
        int trackBottom = RoyalExchangeLayout.SCROLLBAR_BOTTOM;
        int maximumScroll = Math.max(0, catalogRows(itemCount) - RoyalExchangeLayout.VISIBLE_ROWS);
        int thumbHeight = maximumScroll == 0 ? trackBottom - trackTop
                : Math.min(27, trackBottom - trackTop);
        int currentScroll = clampScroll(scroll, itemCount);
        int thumbTop = trackTop + (trackBottom - trackTop - thumbHeight) * currentScroll / Math.max(1, maximumScroll);
        return new Scrollbar(trackTop, trackBottom, thumbTop, thumbHeight, maximumScroll, currentScroll);
    }

    public record Scrollbar(int trackTop, int trackBottom, int thumbTop, int thumbHeight,
                            int maximumScroll, int scroll) {
        public boolean scrollable() { return maximumScroll > 0; }

        public double grabOffsetAt(double pointerY) {
            // Keep the exact position behind the rounded thumb so grabbing it does not jump rows.
            return pointerY - trackTop - (trackBottom - trackTop - thumbHeight)
                    * (double) scroll / Math.max(1, maximumScroll);
        }

        public int scrollAt(double pointerY, double grabOffset) {
            int travel = trackBottom - trackTop - thumbHeight;
            if (!scrollable() || travel <= 0) return 0;
            double fraction = Math.max(0, Math.min(1, (pointerY - trackTop - grabOffset) / travel));
            return (int) Math.round(fraction * maximumScroll);
        }
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
