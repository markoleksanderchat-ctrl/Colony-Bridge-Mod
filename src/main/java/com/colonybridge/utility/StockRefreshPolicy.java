package com.colonybridge.utility;

public final class StockRefreshPolicy {
    private StockRefreshPolicy() {
    }

    public static boolean shouldRefresh(Integer currentColonyDay, Integer refreshedColonyDay, int intervalDays) {
        if (refreshedColonyDay == null) {
            return true;
        }
        if (currentColonyDay == null) {
            return false;
        }
        return currentColonyDay < refreshedColonyDay || currentColonyDay - refreshedColonyDay >= intervalDays;
    }

    public static boolean shouldRefresh(Integer currentColonyDay, Integer refreshedColonyDay, int intervalDays,
                                        boolean cacheCreatedAtStartup, boolean currentExportIsStartup) {
        if (cacheCreatedAtStartup && !currentExportIsStartup) {
            return true;
        }
        return shouldRefresh(currentColonyDay, refreshedColonyDay, intervalDays);
    }

    public static Integer nextRefreshDay(Integer refreshedColonyDay, int intervalDays) {
        return refreshedColonyDay == null ? null : refreshedColonyDay + intervalDays;
    }

    public static Integer ageDays(Integer currentColonyDay, Integer refreshedColonyDay) {
        if (currentColonyDay == null || refreshedColonyDay == null) {
            return null;
        }
        return Math.max(0, currentColonyDay - refreshedColonyDay);
    }
}
