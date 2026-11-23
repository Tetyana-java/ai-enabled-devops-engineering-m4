package com.example.jenkinsmcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(JenkinsProperties.class)
public class JenkinsMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(JenkinsMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider jenkinsToolCallbacks(JenkinsTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
