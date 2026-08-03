package com.colonybridge.export;

public record MainThreadTimingSummary(int samples, long p50Ms, long p95Ms, long maxMs) {
    public static final MainThreadTimingSummary EMPTY = new MainThreadTimingSummary(0, 0, 0, 0);

    public MainThreadTimingSummary {
        if (samples < 0 || p50Ms < 0 || p95Ms < 0 || maxMs < 0 || p50Ms > p95Ms || p95Ms > maxMs) {
            throw new IllegalArgumentException("Main-thread timing summary is invalid.");
        }
    }
}
