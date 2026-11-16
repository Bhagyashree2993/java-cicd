package com.example.habittracker.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;

/** A habit the user wants to build, plus the days it was completed. */
public class Habit {

    private final long id;
    private final String name;
    private final String description;
    private final LocalDate createdOn;
    private final NavigableSet<LocalDate> completions = new ConcurrentSkipListSet<>();

    public Habit(long id, String name, String description, LocalDate createdOn) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdOn = createdOn;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDate getCreatedOn() { return createdOn; }

    public NavigableSet<LocalDate> getCompletions() {
        return Collections.unmodifiableNavigableSet(completions);
    }

    public boolean complete(LocalDate day) { return completions.add(day); }

    public boolean undo(LocalDate day) { return completions.remove(day); }

    public boolean isCompletedOn(LocalDate day) { return completions.contains(day); }

    /**
     * Consecutive completed days ending today. If today is not done yet, the streak
     * still counts up to yesterday, so it does not reset until a day is actually missed.
     */
    public int currentStreak(LocalDate today) {
        LocalDate day = completions.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (completions.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /** Longest run of consecutive completed days ever. */
    public int longestStreak() {
        int best = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : completions) {
            run = (previous != null && previous.plusDays(1).equals(day)) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = day;
        }
        return best;
    }
}
