package com.example.habittracker;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CI tests for the given Habit Tracker REST API (added for the pipeline; no application code changed).
 * Each test creates its own habit and works with the id returned, so tests are independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HabitApiTest {

    @Autowired
    private MockMvc mvc;

    private long createHabit(String name, String frequency) throws Exception {
        String json = mvc.perform(post("/api/habits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"test\",\"frequency\":\"" + frequency + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.frequency").value(frequency))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void createAndGetHabit() throws Exception {
        long id = createHabit("Drink water", "DAILY");
        mvc.perform(get("/api/habits/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Drink water"))
                .andExpect(jsonPath("$.currentStreak").value(0));
        mvc.perform(get("/api/habits")).andExpect(status().isOk());
    }

    @Test
    void markCompleteBuildsStreaksAndStats() throws Exception {
        long id = createHabit("Read 10 pages", "DAILY");
        LocalDate today = LocalDate.now();

        mvc.perform(post("/api/habits/" + id + "/complete").param("date", today.minusDays(2).toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/habits/" + id + "/complete").param("date", today.minusDays(1).toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/habits/" + id + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.longestStreak").value(3));

        mvc.perform(get("/api/habits/" + id + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletions").value(3))
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.last7DaysCompletionRate").value(42.9));
    }

    @Test
    void gapResetsCurrentStreakButKeepsLongest() throws Exception {
        long id = createHabit("Gym", "WEEKLY");
        LocalDate today = LocalDate.now();
        for (int d = 10; d >= 8; d--) {
            mvc.perform(post("/api/habits/" + id + "/complete").param("date", today.minusDays(d).toString()));
        }
        mvc.perform(post("/api/habits/" + id + "/complete"))
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(3));
    }

    @Test
    void unmarkCompleteResetsStreak() throws Exception {
        long id = createHabit("Meditate", "DAILY");
        mvc.perform(post("/api/habits/" + id + "/complete"));
        mvc.perform(delete("/api/habits/" + id + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.longestStreak").value(0));
    }

    @Test
    void updateAndDeactivateHabit() throws Exception {
        long id = createHabit("Walk", "DAILY");
        mvc.perform(put("/api/habits/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Walk 5 km\",\"description\":\"evening\",\"frequency\":\"WEEKLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Walk 5 km"))
                .andExpect(jsonPath("$.frequency").value("WEEKLY"));
        mvc.perform(patch("/api/habits/" + id + "/active").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteHabitThenNotFound() throws Exception {
        long id = createHabit("Temp", "DAILY");
        mvc.perform(delete("/api/habits/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/habits/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Habit not found with id: " + id));
        mvc.perform(delete("/api/habits/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void validationRejectsBlankName() throws Exception {
        mvc.perform(post("/api/habits").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                // spring.mvc.problemdetails.enabled=true -> standard ProblemDetail error body
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void dashboardIsServedOnRoot() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Habit Tracker")));
    }
}
