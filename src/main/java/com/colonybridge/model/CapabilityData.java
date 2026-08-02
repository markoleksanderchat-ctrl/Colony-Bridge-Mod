package com.colonybridge.model;

public record CapabilityData(boolean supported, String reason) {
    public static CapabilityData available() {
        return new CapabilityData(true, null);
    }

    public static CapabilityData unsupported(String reason) {
        return new CapabilityData(false, reason);
    }
}
