package com.example.javaapp.controller;

import com.example.javaapp.service.GreetingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AppController {

    private final GreetingService greetingService;
    private final String appVersion;
    private final String environment;

    public AppController(GreetingService greetingService,
                         @Value("${app.version:dev}") String appVersion,
                         @Value("${app.environment:local}") String environment) {
        this.greetingService = greetingService;
        this.appVersion = appVersion;
        this.environment = environment;
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "message", "Java app deployed with Jenkins, SonarQube, Helm & Argo CD",
                "version", appVersion,
                "environment", environment);
    }

    @GetMapping("/api/greet")
    public Map<String, String> greet(@RequestParam(required = false) String name) {
        return Map.of("greeting", greetingService.greet(name));
    }

    @GetMapping("/api/add")
    public Map<String, Integer> add(@RequestParam int a, @RequestParam int b) {
        return Map.of("result", greetingService.add(a, b));
    }
}
