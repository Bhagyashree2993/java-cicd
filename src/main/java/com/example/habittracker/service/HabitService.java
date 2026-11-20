package com.example.habittracker.service;

import com.example.habittracker.dto.HabitRequest;
import com.example.habittracker.dto.HabitStats;
import com.example.habittracker.model.Habit;
import com.example.habittracker.repository.HabitRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

@Service
public class HabitService {

    private final HabitRepository repository;

    public HabitService(HabitRepository repository) {
        this.repository = repository;
    }

    public Habit create(HabitRequest request) {
        Habit habit = new Habit(null, request.getName(), request.getDescription(), request.getFrequency());
        return repository.save(habit);
    }

    public List<Habit> getAll() {
        return repository.findAll();
    }

    public Habit getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new HabitNotFoundException(id));
    }

    public Habit update(Long id, HabitRequest request) {
        Habit habit = getById(id);
        habit.setName(request.getName());
        habit.setDescription(request.getDescription());
        if (request.getFrequency() != null) {
            habit.setFrequency(request.getFrequency());
        }
        return repository.save(habit);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new HabitNotFoundException(id);
        }
        repository.deleteById(id);
    }

    public Habit setActive(Long id, boolean active) {
        Habit habit = getById(id);
        habit.setActive(active);
        return repository.save(habit);
    }

    /**
     * Marks a habit complete for today (or a given date) and recalculates streaks.
     */
    public Habit markComplete(Long id, LocalDate date) {
        Habit habit = getById(id);
        LocalDate day = date != null ? date : LocalDate.now();
        habit.getCompletedDates().add(day);
        recalculateStreaks(habit);
        return repository.save(habit);
    }

    public Habit unmarkComplete(Long id, LocalDate date) {
        Habit habit = getById(id);
        LocalDate day = date != null ? date : LocalDate.now();
        habit.getCompletedDates().remove(day);
        recalculateStreaks(habit);
        return repository.save(habit);
    }

    private void recalculateStreaks(Habit habit) {
        TreeSet<LocalDate> sorted = new TreeSet<>(habit.getCompletedDates());
        if (sorted.isEmpty()) {
            habit.setCurrentStreak(0);
            habit.setLongestStreak(0);
            return;
        }

        int longest = 1;
        int running = 1;
        LocalDate previous = null;

        for (LocalDate day : sorted) {
            if (previous != null && previous.plusDays(1).equals(day)) {
                running++;
            } else if (previous != null) {
                running = 1;
            }
            longest = Math.max(longest, running);
            previous = day;
        }

        // current streak: consecutive days ending today or yesterday
        int current = 0;
        LocalDate cursor = sorted.last();
        LocalDate today = LocalDate.now();
        if (cursor.equals(today) || cursor.equals(today.minusDays(1))) {
            current = 1;
            LocalDate walker = cursor;
            while (sorted.contains(walker.minusDays(1))) {
                current++;
                walker = walker.minusDays(1);
            }
        }

        habit.setCurrentStreak(current);
        habit.setLongestStreak(longest);
    }

    public HabitStats getStats(Long id) {
        Habit habit = getById(id);
        LocalDate today = LocalDate.now();
        long completionsLast7 = habit.getCompletedDates().stream()
                .filter(d -> !d.isBefore(today.minusDays(6)) && !d.isAfter(today))
                .count();
        double rate = (completionsLast7 / 7.0) * 100.0;

        return new HabitStats(
                habit.getId(),
                habit.getName(),
                habit.getCompletedDates().size(),
                habit.getCurrentStreak(),
                habit.getLongestStreak(),
                Math.round(rate * 10.0) / 10.0
        );
    }
}
