package com.example.habittracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin-token=test-token", "app.environment=test"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HabitApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsSeededHabits() throws Exception {
        mvc.perform(get("/api/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[0].name").value("Drink 8 glasses of water"));
    }

    @Test
    void createCheckInAndDelete() throws Exception {
        mvc.perform(post("/api/habits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Meditate\",\"description\":\"10 minutes\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.completedToday").value(false));

        mvc.perform(post("/api/habits/4/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedToday").value(true))
                .andExpect(jsonPath("$.currentStreak").value(1));

        mvc.perform(delete("/api/habits/4/check-in"))
                .andExpect(jsonPath("$.completedToday").value(false));

        mvc.perform(delete("/api/habits/4")).andExpect(status().isNoContent());
        mvc.perform(get("/api/habits/4")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankName() throws Exception {
        mvc.perform(post("/api/habits").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));
    }

    @Test
    void statsEndpoint() throws Exception {
        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHabits").value(3));
    }

    @Test
    void infoShowsVersionAndEnvironment() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("habit-tracker"))
                .andExpect(jsonPath("$.environment").value("test"));
    }

    @Test
    void adminResetNeedsToken() throws Exception {
        mvc.perform(post("/api/admin/reset")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/reset").header("X-Admin-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/reset").header("X-Admin-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(3));
    }

    @Test
    void uiIsServed() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Habit Tracker")));
    }

    @Test
    void healthProbesAreUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
