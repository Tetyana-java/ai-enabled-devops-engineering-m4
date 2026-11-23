package com.example.jenkinsmcp;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Minimal read-only HTTP client for the Jenkins REST API (GET only). */
@Component
public class JenkinsClient {

    private final JenkinsProperties props;
    private final HttpClient http;

    public JenkinsClient(JenkinsProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.timeoutOrDefault()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Converts "folder/sub/job" to "job/folder/job/sub/job/job" and rejects unsafe segments. */
    static String jobPath(String job) {
        if (job == null || job.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : job.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new JenkinsException("Invalid job name: " + job);
            }
            sb.append("job/").append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")).append('/');
        }
        return sb.toString();
    }

    /** Builds "api/json?tree=..." with the tree value percent-encoded ([ ] { } are illegal in a URI query). */
    static String apiJson(String tree) {
        return "api/json?tree=" + URLEncoder.encode(tree, StandardCharsets.UTF_8);
    }

    /** GET {jenkins}/{relativePath}; relativePath must not start with a slash. */
    public String get(String relativePath) {
        if (props.url() == null || props.url().isBlank()
                || props.user() == null || props.user().isBlank()
                || props.token() == null || props.token().isBlank()) {
            throw new JenkinsException("Jenkins is not configured. Set JENKINS_URL, JENKINS_USER and JENKINS_TOKEN.");
        }
        String base = props.url().endsWith("/") ? props.url() : props.url() + "/";
        String auth = Base64.getEncoder().encodeToString(
                (props.user() + ":" + props.token()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + relativePath))
                .timeout(Duration.ofSeconds(props.timeoutOrDefault()))
                .header("Authorization", "Basic " + auth)
                .header("Accept", "application/json, text/plain")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            throw new JenkinsException(switch (status) {
                case 401 -> "Jenkins returned 401: invalid username or API token.";
                case 403 -> "Jenkins returned 403: the user lacks permission for this resource.";
                case 404 -> "Jenkins returned 404: job or build not found.";
                default -> "Jenkins returned HTTP " + status + ".";
            });
        } catch (java.io.IOException e) {
            throw new JenkinsException("Could not reach Jenkins: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JenkinsException("Request to Jenkins was interrupted.");
        }
    }
}
