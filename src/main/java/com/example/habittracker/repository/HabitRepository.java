package com.example.habittracker.repository;

import com.example.habittracker.model.Habit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure in-memory storage - no external database, no JPA, no H2.
 * Data lives for the lifetime of the running JVM process only.
 */
@Repository
public class HabitRepository {

    private final Map<Long, Habit> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    public Habit save(Habit habit) {
        if (habit.getId() == null) {
            habit.setId(idSequence.incrementAndGet());
        }
        store.put(habit.getId(), habit);
        return habit;
    }

    public List<Habit> findAll() {
        return List.copyOf(store.values());
    }

    public Optional<Habit> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    public void deleteById(Long id) {
        store.remove(id);
    }

    public long count() {
        return store.size();
    }
}
