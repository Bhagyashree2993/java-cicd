package com.example.habittracker.dto;

public class HabitStats {

    private Long habitId;
    private String name;
    private int totalCompletions;
    private int currentStreak;
    private int longestStreak;
    private double last7DaysCompletionRate;

    public HabitStats(Long habitId, String name, int totalCompletions,
                       int currentStreak, int longestStreak, double last7DaysCompletionRate) {
        this.habitId = habitId;
        this.name = name;
        this.totalCompletions = totalCompletions;
        this.currentStreak = currentStreak;
        this.longestStreak = longestStreak;
        this.last7DaysCompletionRate = last7DaysCompletionRate;
    }

    public Long getHabitId() {
        return habitId;
    }

    public String getName() {
        return name;
    }

    public int getTotalCompletions() {
        return totalCompletions;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public double getLast7DaysCompletionRate() {
        return last7DaysCompletionRate;
    }
}
