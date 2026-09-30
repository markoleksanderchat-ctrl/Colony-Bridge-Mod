package com.colonybridge.market;

/** Stable, bounded result codes synchronized to the client. */
public final class TradeFeedback {
    private TradeFeedback() { }
    public static String diamondAmount(int amount) {
        return amount + (amount == 1 ? " diamond" : " diamonds");
    }

    public static int code(String message) {
        if (message.contains("Survival or Adventure")) return 1;
        if (message.contains("already been completed")) return 2;
        if (message.contains("expired")) return 3;
        if (message.contains("no longer") || message.contains("not available")) return 4;
        if (message.contains("buying is disabled")) return 5;
        if (message.contains("selling is disabled")) return 6;
        if (message.contains("not have enough diamonds")) return 7;
        if (message.contains("daily Exchange limit")) return 8;
        if (message.contains("unmodified")) return 9;
        if (message.contains("could not be saved")) return 10;
        if (message.contains("recovery failed")) return 11;
        if (message.contains("Make room")) return 12;
        if (message.contains("Currency")) return 13;
        return 0;
    }
}
