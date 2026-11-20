package com.example.habittracker.dto;

import com.example.habittracker.model.Habit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class HabitRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;

    private Habit.Frequency frequency = Habit.Frequency.DAILY;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Habit.Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Habit.Frequency frequency) {
        this.frequency = frequency;
    }
}
