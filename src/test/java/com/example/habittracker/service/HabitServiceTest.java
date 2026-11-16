package com.example.habittracker.service;

import com.example.habittracker.config.AppProperties;
import com.example.habittracker.dto.HabitResponse;
import com.example.habittracker.dto.StatsResponse;
import com.example.habittracker.model.Habit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HabitServiceTest {

    private HabitService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
        AppProperties props = new AppProperties();
        props.setSeedDemoData(false);
        service = new HabitService(clock, props);
    }

    @Test
    void createAndList() {
        service.create("  Meditate  ", "10 minutes");
        assertThat(service.list()).extracting(HabitResponse::name).containsExactly("Meditate");
    }

    @Test
    void checkInUpdatesStreakAndStats() {
        Habit h = service.create("Walk", null);
        HabitResponse r = service.checkIn(h.getId());
        assertThat(r.completedToday()).isTrue();
        assertThat(r.currentStreak()).isEqualTo(1);

        StatsResponse stats = service.stats();
        assertThat(stats.totalHabits()).isEqualTo(1);
        assertThat(stats.completedToday()).isEqualTo(1);
        assertThat(stats.completionRateToday()).isEqualTo(100);
    }

    @Test
    void undoCheckIn() {
        Habit h = service.create("Walk", null);
        service.checkIn(h.getId());
        assertThat(service.undoCheckIn(h.getId()).completedToday()).isFalse();
    }

    @Test
    void deleteUnknownHabitThrows() {
        assertThatThrownBy(() -> service.delete(99)).isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void statsForNoHabits() {
        StatsResponse stats = service.stats();
        assertThat(stats.totalHabits()).isZero();
        assertThat(stats.completionRateToday()).isZero();
    }

    @Test
    void seedDemoDataCreatesHabits() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
        HabitService seeded = new HabitService(clock, new AppProperties());
        seeded.seedDemoData();
        assertThat(seeded.list()).hasSize(3);
        assertThat(seeded.list().get(0).currentStreak()).isEqualTo(5);
    }

    @Test
    void resetRemovesEverything() {
        service.create("A", null);
        service.create("B", null);
        assertThat(service.reset()).isEqualTo(2);
        assertThat(service.list()).isEmpty();
    }
}
