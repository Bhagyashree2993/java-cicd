package com.example.habittracker.service;

public class HabitNotFoundException extends RuntimeException {
    public HabitNotFoundException(Long id) {
        super("Habit not found with id: " + id);
    }
}
