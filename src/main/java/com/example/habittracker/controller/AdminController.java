package com.example.habittracker.controller;

import com.example.habittracker.config.AppProperties;
import com.example.habittracker.service.HabitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Admin endpoint protected by a token that comes from a Kubernetes Secret. */
@RestController
public class AdminController {

    private final AppProperties props;
    private final HabitService service;

    public AdminController(AppProperties props, HabitService service) {
        this.props = props;
        this.service = service;
    }

    @PostMapping("/api/admin/reset")
    public ResponseEntity<Map<String, Object>> reset(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!isValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "missing or invalid X-Admin-Token"));
        }
        return ResponseEntity.ok(Map.of("removed", service.reset()));
    }

    private boolean isValid(String token) {
        String expected = props.getAdminToken();
        if (expected == null || expected.isBlank() || token == null) {
            return false;
        }
        // constant-time comparison
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
