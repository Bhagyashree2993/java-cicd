package com.example.habittracker.controller;

import com.example.habittracker.dto.HabitRequest;
import com.example.habittracker.dto.HabitStats;
import com.example.habittracker.model.Habit;
import com.example.habittracker.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/habits")
@CrossOrigin(origins = "*")
public class HabitController {

    private final HabitService service;

    public HabitController(HabitService service) {
        this.service = service;
    }

    @GetMapping
    public List<Habit> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public Habit getOne(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Habit create(@Valid @RequestBody HabitRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Habit update(@PathVariable Long id, @Valid @RequestBody HabitRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/active")
    public Habit setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean active = body.getOrDefault("active", true);
        return service.setActive(id, active);
    }

    @PostMapping("/{id}/complete")
    public Habit markComplete(@PathVariable Long id,
                               @RequestParam(required = false)
                               @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                               LocalDate date) {
        return service.markComplete(id, date);
    }

    @DeleteMapping("/{id}/complete")
    public Habit unmarkComplete(@PathVariable Long id,
                                 @RequestParam(required = false)
                                 @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                 LocalDate date) {
        return service.unmarkComplete(id, date);
    }

    @GetMapping("/{id}/stats")
    public HabitStats getStats(@PathVariable Long id) {
        return service.getStats(id);
    }
}
