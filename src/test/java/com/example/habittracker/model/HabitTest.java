package com.example.habittracker.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HabitTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private Habit habit() {
        return new Habit(1, "Read", "", TODAY.minusDays(30));
    }

    @Test
    void newHabitHasNoStreak() {
        Habit h = habit();
        assertThat(h.currentStreak(TODAY)).isZero();
        assertThat(h.longestStreak()).isZero();
    }

    @Test
    void streakCountsConsecutiveDaysIncludingToday() {
        Habit h = habit();
        h.complete(TODAY);
        h.complete(TODAY.minusDays(1));
        h.complete(TODAY.minusDays(2));
        assertThat(h.currentStreak(TODAY)).isEqualTo(3);
    }

    @Test
    void streakIsKeptUntilTodayIsMissed() {
        Habit h = habit();
        h.complete(TODAY.minusDays(1));
        h.complete(TODAY.minusDays(2));
        assertThat(h.currentStreak(TODAY)).isEqualTo(2);
    }

    @Test
    void gapBreaksTheStreak() {
        Habit h = habit();
        h.complete(TODAY);
        h.complete(TODAY.minusDays(2));
        assertThat(h.currentStreak(TODAY)).isEqualTo(1);
    }

    @Test
    void longestStreakFindsBestRun() {
        Habit h = habit();
        for (int i = 10; i <= 13; i++) {
            h.complete(TODAY.minusDays(i));
        }
        h.complete(TODAY);
        assertThat(h.longestStreak()).isEqualTo(4);
    }

    @Test
    void completingTwiceCountsOnce() {
        Habit h = habit();
        assertThat(h.complete(TODAY)).isTrue();
        assertThat(h.complete(TODAY)).isFalse();
        assertThat(h.getCompletions()).hasSize(1);
    }

    @Test
    void undoRemovesCompletion() {
        Habit h = habit();
        h.complete(TODAY);
        assertThat(h.undo(TODAY)).isTrue();
        assertThat(h.isCompletedOn(TODAY)).isFalse();
    }
}
