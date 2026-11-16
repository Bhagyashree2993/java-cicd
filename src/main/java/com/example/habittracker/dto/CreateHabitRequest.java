package com.example.habittracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHabitRequest(
        @NotBlank(message = "name is required")
        @Size(max = 60, message = "name must be at most 60 characters")
        String name,

        @Size(max = 200, message = "description must be at most 200 characters")
        String description) {
}
