package com.example.bitbucketmcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** Read-only Bitbucket Data Center tools exposed over MCP. */
@Service
public class BitbucketTools {

    private static final String API = "rest/api/1.0/";
    private static final int MAX_LIST = 100;
    private static final int MAX_FILE_LINES = 2000;
    private static final Set<String> PR_STATES = Set.of("OPEN", "MERGED", "DECLINED", "ALL");

    public record RepoInfo(String project, String slug, String name, String url) {}

    public record BranchInfo(String name, String latestCommit, boolean isDefault) {}

    public record CommitInfo(String id, String author, String authorEmail, String committedAt, String message) {}

    public record PullRequestInfo(int id, String title, String state, String author, String fromBranch,
                                  String toBranch, String createdAt, String updatedAt, String url) {}

    public record Reviewer(String name, String status) {}

    public record BuildStatus(String state, String name, String key, String url) {}

    public record PullRequestDetails(int id, String title, String description, String state, String author,
                                     String fromBranch, String fromCommit, String toBranch, String createdAt,
                                     String updatedAt, List<Reviewer> reviewers, List<BuildStatus> builds,
                                     String buildsError, String url) {}

    public record FileContent(String path, String ref, int totalLines, int returnedLines, String content) {}

    private final BitbucketClient client;

    public BitbucketTools(BitbucketClient client) {
        this.client = client;
    }

    @Tool(name = "list_repos", description = "List Bitbucket repositories, optionally only those in one project. "
            + "Use it to discover the project key and repository slug of a service.")
    public List<RepoInfo> listRepos(
            @ToolParam(description = "Project key such as 'PRJ'. Empty to list all repositories you can see.",
                    required = false) String project,
            @ToolParam(description = "Maximum number of repositories (1-100). Default 100.", required = false)
            Integer limit) {
        String path = (project == null || project.isBlank())
                ? API + "repos"
                : API + "projects/" + BitbucketClient.segment("project", project) + "/repos";
        JsonNode values = client.getJson(path + BitbucketClient.query(params("limit", clamp(limit, 100))))
                .path("values");
        List<RepoInfo> result = new ArrayList<>();
        for (JsonNode r : values) {
            result.add(new RepoInfo(r.path("project").path("key").asText(), r.path("slug").asText(),
                    r.path("name").asText(), selfLink(r)));
        }
        return result;
    }

    @Tool(name = "list_branches", description = "List branches of a Bitbucket repository, most recently changed first.")
    public List<BranchInfo> listBranches(
            @ToolParam(description = "Project key, e.g. 'PRJ'.") String project,
            @ToolParam(description = "Repository slug, e.g. 'my-service'.") String repo,
            @ToolParam(description = "Only branches whose name contains this text, e.g. 'release'.", required = false)
            String filter,
            @ToolParam(description = "Maximum number of branches (1-100). Default 25.", required = false)
            Integer limit) {
        JsonNode values = client.getJson(repoPath(project, repo) + "/branches" + BitbucketClient.query(params(
                "filterText", filter, "orderBy", "MODIFICATION", "limit", clamp(limit, 25)))).path("values");
        List<BranchInfo> result = new ArrayList<>();
        for (JsonNode b : values) {
            result.add(new BranchInfo(b.path("displayId").asText(), b.path("latestCommit").asText(),
                    b.path("isDefault").asBoolean()));
        }
        return result;
    }

    @Tool(name = "list_commits", description = "List recent commits of a Bitbucket repository, newest first.")
    public List<CommitInfo> listCommits(
            @ToolParam(description = "Project key, e.g. 'PRJ'.") String project,
            @ToolParam(description = "Repository slug.") String repo,
            @ToolParam(description = "Branch, tag or commit to start from, e.g. 'release/6.6'. "
                    + "Empty for the default branch.", required = false) String branch,
            @ToolParam(description = "Maximum number of commits (1-100). Default 10.", required = false)
            Integer limit) {
        JsonNode values = client.getJson(repoPath(project, repo) + "/commits" + BitbucketClient.query(params(
                "until", branch, "limit", clamp(limit, 10)))).path("values");
        List<CommitInfo> result = new ArrayList<>();
        for (JsonNode c : values) {
            result.add(new CommitInfo(c.path("id").asText(), c.path("author").path("name").asText(null),
                    c.path("author").path("emailAddress").asText(null),
                    time(c.path("committerTimestamp")), c.path("message").asText()));
        }
        return result;
    }

    @Tool(name = "list_pull_requests", description = "List pull requests of a Bitbucket repository, newest first.")
    public List<PullRequestInfo> listPullRequests(
            @ToolParam(description = "Project key, e.g. 'PRJ'.") String project,
            @ToolParam(description = "Repository slug.") String repo,
            @ToolParam(description = "OPEN, MERGED, DECLINED or ALL. Default OPEN.", required = false) String state,
            @ToolParam(description = "Maximum number of pull requests (1-100). Default 10.", required = false)
            Integer limit) {
        String s = state == null || state.isBlank() ? "OPEN" : state.trim().toUpperCase(Locale.ROOT);
        if (!PR_STATES.contains(s)) {
            throw new BitbucketException("state must be OPEN, MERGED, DECLINED or ALL.");
        }
        JsonNode values = client.getJson(repoPath(project, repo) + "/pull-requests" + BitbucketClient.query(params(
                "state", s, "order", "NEWEST", "limit", clamp(limit, 10)))).path("values");
        List<PullRequestInfo> result = new ArrayList<>();
        for (JsonNode p : values) {
            result.add(new PullRequestInfo(p.path("id").asInt(), p.path("title").asText(), p.path("state").asText(),
                    p.path("author").path("user").path("displayName").asText(null),
                    p.path("fromRef").path("displayId").asText(), p.path("toRef").path("displayId").asText(),
                    time(p.path("createdDate")), time(p.path("updatedDate")), selfLink(p)));
        }
        return result;
    }

    @Tool(name = "get_pull_request", description = "Get one Bitbucket pull request with its description, "
            + "reviewers and their approval status, and the CI build status (e.g. Jenkins) of its latest commit.")
    public PullRequestDetails getPullRequest(
            @ToolParam(description = "Project key, e.g. 'PRJ'.") String project,
            @ToolParam(description = "Repository slug.") String repo,
            @ToolParam(description = "Pull request id, e.g. 123.") int pullRequestId) {
        if (pullRequestId < 1) {
            throw new BitbucketException("pullRequestId must be 1 or greater.");
        }
        JsonNode p = client.getJson(repoPath(project, repo) + "/pull-requests/" + pullRequestId);
        List<Reviewer> reviewers = new ArrayList<>();
        for (JsonNode r : p.path("reviewers")) {
            reviewers.add(new Reviewer(r.path("user").path("displayName").asText(), r.path("status").asText()));
        }
        String commit = p.path("fromRef").path("latestCommit").asText("");
        List<BuildStatus> builds = new ArrayList<>();
        String buildsError = null;
        if (!commit.isBlank()) {
            try {
                JsonNode values = client.getJson("rest/build-status/1.0/commits/"
                        + BitbucketClient.segment("commit", commit) + BitbucketClient.query(params("limit", 25)))
                        .path("values");
                for (JsonNode b : values) {
                    builds.add(new BuildStatus(b.path("state").asText(), b.path("name").asText(null),
                            b.path("key").asText(null), b.path("url").asText(null)));
                }
            } catch (BitbucketException e) {
                buildsError = e.getMessage();
            }
        }
        return new PullRequestDetails(p.path("id").asInt(), p.path("title").asText(),
                p.path("description").asText(null), p.path("state").asText(),
                p.path("author").path("user").path("displayName").asText(null),
                p.path("fromRef").path("displayId").asText(), commit.isBlank() ? null : commit,
                p.path("toRef").path("displayId").asText(), time(p.path("createdDate")),
                time(p.path("updatedDate")), reviewers, builds, buildsError, selfLink(p));
    }

    @Tool(name = "get_file", description = "Get the text content of one file from a Bitbucket repository "
            + "at a branch, tag or commit. Long files are cut after maxLines lines.")
    public FileContent getFile(
            @ToolParam(description = "Project key, e.g. 'PRJ'.") String project,
            @ToolParam(description = "Repository slug.") String repo,
            @ToolParam(description = "File path inside the repository, e.g. 'pom.xml' or 'src/main/App.java'.")
            String path,
            @ToolParam(description = "Branch, tag or commit, e.g. 'master'. Empty for the default branch.",
                    required = false) String ref,
            @ToolParam(description = "Maximum number of lines to return (1-2000). Default 500.", required = false)
            Integer maxLines) {
        int n = Math.max(1, Math.min(maxLines == null ? 500 : maxLines, MAX_FILE_LINES));
        String text = client.get(repoPath(project, repo) + "/raw/" + BitbucketClient.filePath(path)
                + BitbucketClient.query(params("at", ref)));
        if (text.indexOf('\0') >= 0) {
            throw new BitbucketException("File looks binary; only text files are supported.");
        }
        String[] lines = text.split("\\R", -1);
        int total = text.isEmpty() ? 0 : (text.endsWith("\n") ? lines.length - 1 : lines.length);
        int returned = Math.min(n, total);
        String content = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, returned));
        return new FileContent(path, ref == null || ref.isBlank() ? null : ref, total, returned, content);
    }

    private static String repoPath(String project, String repo) {
        return API + "projects/" + BitbucketClient.segment("project", project)
                + "/repos/" + BitbucketClient.segment("repo", repo);
    }

    private static int clamp(Integer value, int defaultValue) {
        return Math.max(1, Math.min(value == null ? defaultValue : value, MAX_LIST));
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private static String time(JsonNode epochMillis) {
        return epochMillis.isNumber() ? Instant.ofEpochMilli(epochMillis.asLong()).toString() : null;
    }

    private static String selfLink(JsonNode node) {
        return node.path("links").path("self").path(0).path("href").asText(null);
    }
}
