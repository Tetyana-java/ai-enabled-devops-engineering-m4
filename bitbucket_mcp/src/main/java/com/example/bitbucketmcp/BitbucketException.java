package com.example.bitbucketmcp;

/** Error with a message that is safe to show to the MCP client (never contains credentials). */
public class BitbucketException extends RuntimeException {

    public BitbucketException(String message) {
        super(message);
    }
}
