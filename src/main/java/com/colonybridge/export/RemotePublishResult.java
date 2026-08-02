package com.colonybridge.export;

public record RemotePublishResult(int statusCode, int attempts, int payloadBytes) {
}
