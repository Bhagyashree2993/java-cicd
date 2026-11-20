package com.example.habittracker.model;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Habit domain object. Kept as a plain in-memory model - no JPA / DB annotations,
 * since this application intentionally has no external database dependency.
 */
public class Habit {

    public enum Frequency {
        DAILY, WEEKLY
    }

    private Long id;
    private String name;
    private String description;
    private Frequency frequency = Frequency.DAILY;
    private boolean active = true;
    private LocalDate createdDate = LocalDate.now();

    // dates (yyyy-MM-dd) on which the habit was marked as completed
    private Set<LocalDate> completedDates = new LinkedHashSet<>();

    private int currentStreak = 0;
    private int longestStreak = 0;

    public Habit() {
    }

    public Habit(Long id, String name, String description, Frequency frequency) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.frequency = frequency == null ? Frequency.DAILY : frequency;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public Set<LocalDate> getCompletedDates() {
        return completedDates;
    }

    public void setCompletedDates(Set<LocalDate> completedDates) {
        this.completedDates = completedDates;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(int longestStreak) {
        this.longestStreak = longestStreak;
    }
}
