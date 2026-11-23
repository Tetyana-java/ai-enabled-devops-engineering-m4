package com.example.jenkinsmcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** Read-only Jenkins tools exposed over MCP. */
@Service
public class JenkinsTools {

    private static final int MAX_BUILDS = 25;
    private static final int MAX_LOG_LINES = 500;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|token|secret|api[_-]?key)(\\s*[=:]\\s*)\\S+");

    public record JobInfo(String name, String fullName, String color, String url) {}

    public record BuildInfo(int number, String result, boolean building, String startedAt,
                            long durationMs, String url) {}

    public record BuildLog(String job, int build, int totalLines, int returnedLines, String log) {}

    private final JenkinsClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public JenkinsTools(JenkinsClient client) {
        this.client = client;
    }

    @Tool(name = "list_jobs", description = "List Jenkins jobs, optionally inside a folder. "
            + "Use it to discover the exact job name of a service. Returns name, full name, status color and URL.")
    public List<JobInfo> listJobs(
            @ToolParam(description = "Folder path such as 'qa' or 'team/qa'. Empty for the root.", required = false)
            String folder) {
        String path = JenkinsClient.jobPath(folder)
                + JenkinsClient.apiJson("jobs[name,fullName,color,url]");
        JsonNode jobs = read(path).path("jobs");
        List<JobInfo> result = new ArrayList<>();
        for (JsonNode j : jobs) {
            result.add(new JobInfo(j.path("name").asText(), j.path("fullName").asText(),
                    j.path("color").asText(null), j.path("url").asText()));
        }
        return result;
    }

    @Tool(name = "get_last_builds", description = "Get the most recent builds of a Jenkins job, newest first. "
            + "Returns build number, result (SUCCESS, FAILURE, UNSTABLE, ABORTED or null if running), "
            + "start time and duration. Use it to find out whether a build failed and when.")
    public List<BuildInfo> getLastBuilds(
            @ToolParam(description = "Full job name, using '/' for folders, e.g. 'qa/payments-api'.") String job,
            @ToolParam(description = "How many builds to return (1-25). Default 5.", required = false) Integer count) {
        int n = Math.max(1, Math.min(count == null ? 5 : count, MAX_BUILDS));
        String path = JenkinsClient.jobPath(requireJob(job))
                + JenkinsClient.apiJson("builds[number,result,building,timestamp,duration,url]{0," + n + "}");
        JsonNode builds = read(path).path("builds");
        List<BuildInfo> result = new ArrayList<>();
        for (JsonNode b : builds) {
            result.add(new BuildInfo(b.path("number").asInt(), b.path("result").asText(null),
                    b.path("building").asBoolean(),
                    Instant.ofEpochMilli(b.path("timestamp").asLong()).toString(),
                    b.path("duration").asLong(), b.path("url").asText()));
        }
        return result;
    }

    @Tool(name = "get_build_log", description = "Get the last lines of the console log of one build. "
            + "Use it to find the reason a build failed. Secrets like password=... are masked.")
    public BuildLog getBuildLog(
            @ToolParam(description = "Full job name, using '/' for folders.") String job,
            @ToolParam(description = "Build number, e.g. 42.") int buildNumber,
            @ToolParam(description = "How many lines from the end to return (1-500). Default 100.", required = false)
            Integer tailLines) {
        if (buildNumber < 1) {
            throw new JenkinsException("buildNumber must be 1 or greater.");
        }
        int n = Math.max(1, Math.min(tailLines == null ? 100 : tailLines, MAX_LOG_LINES));
        String text = client.get(JenkinsClient.jobPath(requireJob(job)) + buildNumber + "/consoleText");
        String[] lines = text.split("\\R");
        int from = Math.max(0, lines.length - n);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.length; i++) {
            sb.append(SECRET.matcher(lines[i]).replaceAll("$1$2****")).append('\n');
        }
        return new BuildLog(job, buildNumber, lines.length, lines.length - from, sb.toString());
    }

    private static String requireJob(String job) {
        if (job == null || job.isBlank()) {
            throw new JenkinsException("job is required.");
        }
        return job;
    }

    private JsonNode read(String path) {
        try {
            return mapper.readTree(client.get(path));
        } catch (java.io.IOException e) {
            throw new JenkinsException("Jenkins returned a response that is not valid JSON.");
        }
    }
}
