package com.example.jenkinsmcp;

/** Error with a message that is safe and useful to show to the agent. */
public class JenkinsException extends RuntimeException {
    public JenkinsException(String message) {
        super(message);
    }
}
