package com.example.javaapp.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    @Test
    void greetsByName() {
        assertThat(service.greet("Bhagyashree")).isEqualTo("Hello, Bhagyashree!");
    }

    @Test
    void trimsName() {
        assertThat(service.greet("  Dev  ")).isEqualTo("Hello, Dev!");
    }

    @Test
    void defaultsWhenNameMissing() {
        assertThat(service.greet(null)).isEqualTo("Hello, World!");
        assertThat(service.greet("   ")).isEqualTo("Hello, World!");
    }

    @Test
    void addsNumbers() {
        assertThat(service.add(2, 3)).isEqualTo(5);
    }

    @Test
    void addDetectsOverflow() {
        assertThatThrownBy(() -> service.add(Integer.MAX_VALUE, 1))
                .isInstanceOf(ArithmeticException.class);
    }
}
