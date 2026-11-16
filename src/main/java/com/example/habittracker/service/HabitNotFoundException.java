package com.example.habittracker.service;

public class HabitNotFoundException extends RuntimeException {

    public HabitNotFoundException(long id) {
        super("Habit " + id + " not found");
    }
}
