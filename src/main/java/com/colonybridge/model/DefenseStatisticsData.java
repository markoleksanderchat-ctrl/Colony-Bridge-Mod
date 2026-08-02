package com.colonybridge.model;

public record DefenseStatisticsData(
        DefenseKillBreakdownData lifetime,
        DefenseKillBreakdownData today,
        DefenseKillBreakdownData recentWindow,
        Integer currentDay,
        Integer windowDays,
        Integer animalsButchered,
        Integer animalsButcheredToday,
        Integer animalsButcheredRecentWindow
) {
}
