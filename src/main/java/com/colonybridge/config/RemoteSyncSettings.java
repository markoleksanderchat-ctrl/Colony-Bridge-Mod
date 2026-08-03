package com.colonybridge.config;

public record RemoteSyncSettings(boolean enabled, String endpoint, String token) {
    public RemoteSyncSettings {
        endpoint = endpoint == null ? "" : endpoint.trim();
        token = token == null ? "" : token.trim();
    }
}
