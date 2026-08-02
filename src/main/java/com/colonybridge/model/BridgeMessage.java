package com.colonybridge.model;

public record BridgeMessage(String scope, String entityId, String code, String message) {
}
