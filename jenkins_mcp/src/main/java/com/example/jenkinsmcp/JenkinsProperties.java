package com.example.jenkinsmcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jenkins")
public record JenkinsProperties(String url, String user, String token, Integer timeoutSeconds) {

    public int timeoutOrDefault() {
        return timeoutSeconds == null ? 20 : timeoutSeconds;
    }
}
