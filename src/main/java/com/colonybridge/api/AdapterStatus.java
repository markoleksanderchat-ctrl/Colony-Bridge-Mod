package com.colonybridge.api;

public record AdapterStatus(
        boolean available,
        String adapterName,
        String mineColoniesVersion,
        String message
) {
    public static AdapterStatus unavailable(String message) {
        return new AdapterStatus(false, "unavailable", null, message);
    }
}
