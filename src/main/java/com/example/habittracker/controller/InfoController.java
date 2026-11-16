package com.example.habittracker.controller;

import com.example.habittracker.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shows which version and which pod answered: useful evidence for rolling updates and replicas. */
@RestController
public class InfoController {

    private final AppProperties props;

    public InfoController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("app", "habit-tracker");
        info.put("version", props.getVersion());
        info.put("environment", props.getEnvironment());
        info.put("greeting", props.getGreeting());
        info.put("pod", hostname());
        return info;
    }

    static String hostname() {
        String fromEnv = System.getenv("HOSTNAME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
