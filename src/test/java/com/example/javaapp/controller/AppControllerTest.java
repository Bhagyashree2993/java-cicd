package com.example.javaapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeReturnsVersion() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("dev"));
    }

    @Test
    void greetEndpoint() throws Exception {
        mockMvc.perform(get("/api/greet").param("name", "Jenkins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeting").value("Hello, Jenkins!"));
    }

    @Test
    void addEndpoint() throws Exception {
        mockMvc.perform(get("/api/add").param("a", "4").param("b", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(10));
    }

    @Test
    void healthProbesAreUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
