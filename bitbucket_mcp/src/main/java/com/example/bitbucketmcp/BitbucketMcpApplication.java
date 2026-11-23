package com.example.bitbucketmcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(BitbucketProperties.class)
public class BitbucketMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(BitbucketMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider bitbucketToolCallbacks(BitbucketTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
