package com.example.bitbucketmcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bitbucket")
public record BitbucketProperties(String url, String token, Integer timeoutSeconds) {

    public int timeoutOrDefault() {
        return timeoutSeconds == null ? 20 : timeoutSeconds;
    }
}
