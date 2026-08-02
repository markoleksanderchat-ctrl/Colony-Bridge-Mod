package com.colonybridge.api;

public enum ExportTrigger {
    MANUAL("manual"),
    STARTUP("startup"),
    PLAYER_JOIN("player_join"),
    INTERVAL("interval"),
    DISCONNECT("disconnect"),
    SHUTDOWN("shutdown");

    private final String jsonName;

    ExportTrigger(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }
}
