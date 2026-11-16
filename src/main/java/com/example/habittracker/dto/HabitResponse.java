package com.example.habittracker.dto;

import com.example.habittracker.model.Habit;

import java.time.LocalDate;

public record HabitResponse(
        long id,
        String name,
        String description,
        LocalDate createdOn,
        boolean completedToday,
        int currentStreak,
        int longestStreak,
        int totalCompletions) {

    public static HabitResponse from(Habit habit, LocalDate today) {
        return new HabitResponse(
                habit.getId(),
                habit.getName(),
                habit.getDescription(),
                habit.getCreatedOn(),
                habit.isCompletedOn(today),
                habit.currentStreak(today),
                habit.longestStreak(),
                habit.getCompletions().size());
    }
}
