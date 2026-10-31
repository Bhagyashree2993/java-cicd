package com.example.javaapp.service;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String greet(String name) {
        if (name == null || name.isBlank()) {
            return "Hello, World!";
        }
        return "Hello, " + name.trim() + "!";
    }

    public int add(int a, int b) {
        return Math.addExact(a, b);
    }
}
