package com.example.habittracker.service;

import com.example.habittracker.config.AppProperties;
import com.example.habittracker.dto.HabitResponse;
import com.example.habittracker.dto.StatsResponse;
import com.example.habittracker.model.Habit;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores habits in memory. Each pod keeps its own data; this keeps the demo
 * dependency-free (no database) while the CI/CD pipeline is the focus.
 */
@Service
public class HabitService {

    private static final Logger log = LoggerFactory.getLogger(HabitService.class);

    private final Map<Long, Habit> habits = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final Clock clock;
    private final AppProperties props;

    public HabitService(Clock clock, AppProperties props) {
        this.clock = clock;
        this.props = props;
    }

    @PostConstruct
    void seedDemoData() {
        if (!props.isSeedDemoData()) {
            return;
        }
        LocalDate today = today();
        Habit water = create("Drink 8 glasses of water", "Stay hydrated through the day");
        Habit read = create("Read 20 pages", "One chapter a day");
        create("30 minutes exercise", "Walk, run or gym");
        for (int i = 1; i <= 5; i++) {
            water.complete(today.minusDays(i));
        }
        read.complete(today.minusDays(1));
        read.complete(today.minusDays(2));
        log.info("Seeded {} demo habits", habits.size());
    }

    public Habit create(String name, String description) {
        long id = ids.incrementAndGet();
        Habit habit = new Habit(id, name.trim(), description == null ? "" : description.trim(), today());
        habits.put(id, habit);
        log.info("Created habit id={} name='{}'", id, habit.getName());
        return habit;
    }

    public List<HabitResponse> list() {
        LocalDate today = today();
        return habits.values().stream()
                .sorted(Comparator.comparingLong(Habit::getId))
                .map(h -> HabitResponse.from(h, today))
                .toList();
    }

    public HabitResponse get(long id) {
        return HabitResponse.from(find(id), today());
    }

    public HabitResponse checkIn(long id) {
        Habit habit = find(id);
        boolean added = habit.complete(today());
        log.info("Check-in habit id={} newCompletion={} streak={}", id, added, habit.currentStreak(today()));
        return HabitResponse.from(habit, today());
    }

    public HabitResponse undoCheckIn(long id) {
        Habit habit = find(id);
        habit.undo(today());
        log.info("Undo check-in habit id={}", id);
        return HabitResponse.from(habit, today());
    }

    public void delete(long id) {
        if (habits.remove(id) == null) {
            throw new HabitNotFoundException(id);
        }
        log.info("Deleted habit id={}", id);
    }

    public int reset() {
        int removed = habits.size();
        habits.clear();
        log.warn("All habits reset by admin ({} removed)", removed);
        return removed;
    }

    public StatsResponse stats() {
        List<HabitResponse> all = list();
        int done = (int) all.stream().filter(HabitResponse::completedToday).count();
        int rate = all.isEmpty() ? 0 : Math.round(done * 100f / all.size());
        int best = all.stream().mapToInt(HabitResponse::currentStreak).max().orElse(0);
        return new StatsResponse(all.size(), done, rate, best);
    }

    private Habit find(long id) {
        Habit habit = habits.get(id);
        if (habit == null) {
            throw new HabitNotFoundException(id);
        }
        return habit;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
