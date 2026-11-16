package com.example.habittracker.controller;

import com.example.habittracker.dto.CreateHabitRequest;
import com.example.habittracker.dto.HabitResponse;
import com.example.habittracker.dto.StatsResponse;
import com.example.habittracker.model.Habit;
import com.example.habittracker.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class HabitController {

    private final HabitService service;

    public HabitController(HabitService service) {
        this.service = service;
    }

    @GetMapping("/habits")
    public List<HabitResponse> list() {
        return service.list();
    }

    @GetMapping("/habits/{id}")
    public HabitResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/habits")
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@Valid @RequestBody CreateHabitRequest request) {
        Habit habit = service.create(request.name(), request.description());
        return service.get(habit.getId());
    }

    @PostMapping("/habits/{id}/check-in")
    public HabitResponse checkIn(@PathVariable long id) {
        return service.checkIn(id);
    }

    @DeleteMapping("/habits/{id}/check-in")
    public HabitResponse undoCheckIn(@PathVariable long id) {
        return service.undoCheckIn(id);
    }

    @DeleteMapping("/habits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return service.stats();
    }
}
