package com.example.habittracker.dto;

public record StatsResponse(
        int totalHabits,
        int completedToday,
        int completionRateToday,
        int bestCurrentStreak) {
}
