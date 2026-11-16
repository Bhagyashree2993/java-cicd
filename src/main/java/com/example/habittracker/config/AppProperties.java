package com.example.habittracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings that come from the Kubernetes ConfigMap (environment, greeting, seed data)
 * and Secret (admin token). See helm/habit-tracker/templates/configmap.yaml and secret.yaml.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String version = "dev";
    private String environment = "local";
    private String greeting = "Build better habits, one day at a time.";
    private boolean seedDemoData = true;
    private String adminToken = "";

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }

    public boolean isSeedDemoData() { return seedDemoData; }
    public void setSeedDemoData(boolean seedDemoData) { this.seedDemoData = seedDemoData; }

    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }
}
