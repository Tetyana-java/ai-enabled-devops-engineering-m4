package com.example.bitbucketmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BitbucketClientTest {

    @Test
    void encodesSegment() {
        assertEquals("~john.doe", BitbucketClient.segment("project", "~john.doe").replace("%7E", "~"));
        assertEquals("my%20repo", BitbucketClient.segment("repo", "my repo"));
    }

    @Test
    void rejectsBadSegments() {
        assertThrows(BitbucketException.class, () -> BitbucketClient.segment("repo", "a/b"));
        assertThrows(BitbucketException.class, () -> BitbucketClient.segment("repo", ".."));
        assertThrows(BitbucketException.class, () -> BitbucketClient.segment("repo", " "));
        assertThrows(BitbucketException.class, () -> BitbucketClient.segment("repo", null));
    }

    @Test
    void encodesFilePath() {
        assertEquals("src/main/My%20App.java", BitbucketClient.filePath("/src/main/My App.java"));
        assertThrows(BitbucketException.class, () -> BitbucketClient.filePath("src/../../etc/passwd"));
        assertThrows(BitbucketException.class, () -> BitbucketClient.filePath("/"));
    }

    @Test
    void queryIsEncodedAndSkipsBlankValues() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("until", "release/6.6");
        p.put("filterText", "");
        p.put("at", null);
        p.put("limit", 10);
        String q = BitbucketClient.query(p);
        assertEquals("?until=release%2F6.6&limit=10", q);
        URI.create("https://bitbucket.example.com/rest/api/1.0/projects/P/repos/r/commits" + q);
        assertEquals("", BitbucketClient.query(Map.of()));
    }

    @Test
    void failsClearlyWhenNotConfigured() {
        BitbucketClient client = new BitbucketClient(new BitbucketProperties("", "", null));
        BitbucketException e = assertThrows(BitbucketException.class, () -> client.get("rest/api/1.0/repos"));
        assertEquals("Bitbucket is not configured. Set BITBUCKET_URL and BITBUCKET_TOKEN.", e.getMessage());
    }
}
