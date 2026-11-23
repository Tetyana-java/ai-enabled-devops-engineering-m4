package com.example.bitbucketmcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Minimal read-only HTTP client for the Bitbucket Data Center REST API (GET only). */
@Component
public class BitbucketClient {

    private final BitbucketProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public BitbucketClient(BitbucketProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.timeoutOrDefault()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Percent-encodes one path segment (project key, repo slug, commit id). Rejects empty, '/', '.' and '..'. */
    static String segment(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new BitbucketException(name + " is required.");
        }
        String v = value.trim();
        if (v.contains("/") || v.equals(".") || v.equals("..")) {
            throw new BitbucketException("Invalid " + name + ": " + value);
        }
        return encode(v);
    }

    /** Encodes a file path like "src/main/App.java" segment by segment. Rejects '.' and '..'. */
    static String filePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BitbucketException("path is required.");
        }
        StringBuilder sb = new StringBuilder();
        for (String s : path.split("/")) {
            if (s.isEmpty()) {
                continue;
            }
            if (s.equals(".") || s.equals("..")) {
                throw new BitbucketException("Invalid path: " + path);
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(encode(s));
        }
        if (sb.length() == 0) {
            throw new BitbucketException("path is required.");
        }
        return sb.toString();
    }

    /** Builds "?a=1&b=2" with every value percent-encoded; null or blank values are skipped. */
    static String query(Map<String, ?> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().toString().isBlank()) {
                continue;
            }
            sb.append(sb.length() == 0 ? '?' : '&')
                    .append(encode(e.getKey())).append('=').append(encode(e.getValue().toString()));
        }
        return sb.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** GET {bitbucket}/{relativePath}; relativePath must not start with a slash and must already be encoded. */
    public String get(String relativePath) {
        if (props.url() == null || props.url().isBlank() || props.token() == null || props.token().isBlank()) {
            throw new BitbucketException("Bitbucket is not configured. Set BITBUCKET_URL and BITBUCKET_TOKEN.");
        }
        String base = props.url().endsWith("/") ? props.url() : props.url() + "/";
        URI uri;
        try {
            uri = URI.create(base + relativePath);
        } catch (IllegalArgumentException e) {
            throw new BitbucketException("Could not build a valid Bitbucket URL.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(props.timeoutOrDefault()))
                .header("Authorization", "Bearer " + props.token())
                .header("Accept", "application/json, text/plain")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            String detail = errorMessage(response.body());
            throw new BitbucketException(switch (status) {
                case 401 -> "Bitbucket returned 401: invalid or expired access token.";
                case 403 -> "Bitbucket returned 403: the token lacks permission for this resource.";
                case 404 -> "Bitbucket returned 404: project, repository, branch or file not found." + detail;
                default -> "Bitbucket returned HTTP " + status + "." + detail;
            });
        } catch (java.io.IOException e) {
            throw new BitbucketException("Could not reach Bitbucket: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BitbucketException("Request to Bitbucket was interrupted.");
        }
    }

    /** Extracts the first message from Bitbucket's {"errors":[{"message":...}]} body, if present. */
    private String errorMessage(String body) {
        try {
            String msg = mapper.readTree(body).path("errors").path(0).path("message").asText("");
            if (msg.isBlank()) {
                return "";
            }
            return " " + (msg.length() > 300 ? msg.substring(0, 300) + "..." : msg);
        } catch (Exception e) {
            return "";
        }
    }

    /** GET and parse as JSON. */
    public JsonNode getJson(String relativePath) {
        String body = get(relativePath);
        try {
            return mapper.readTree(body);
        } catch (java.io.IOException e) {
            throw new BitbucketException("Bitbucket returned a response that is not valid JSON.");
        }
    }
}
