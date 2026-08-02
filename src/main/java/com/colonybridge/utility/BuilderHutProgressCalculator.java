package com.colonybridge.utility;

public final class BuilderHutProgressCalculator {
    private BuilderHutProgressCalculator() {
    }

    public static int percent(int totalResources, long remainingResources) {
        if (totalResources <= 0) {
            return 0;
        }

        int remainingPercent = (int) ((Math.max(0L, remainingResources) / (double) totalResources) * 100.0);
        return Math.max(0, Math.min(100, 100 - remainingPercent));
    }
}
