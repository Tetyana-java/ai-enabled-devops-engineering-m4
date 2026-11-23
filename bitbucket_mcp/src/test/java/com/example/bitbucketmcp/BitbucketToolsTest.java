package com.example.bitbucketmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BitbucketToolsTest {

    /** Returns canned responses by exact path and records every requested path. */
    static class FakeClient extends BitbucketClient {
        final Map<String, String> responses = new HashMap<>();
        final List<String> requested = new ArrayList<>();

        FakeClient() {
            super(new BitbucketProperties("https://bb.example.com", "t", null));
        }

        @Override
        public String get(String relativePath) {
            requested.add(relativePath);
            String body = responses.get(relativePath);
            if (body == null) {
                throw new BitbucketException("Bitbucket returned 404: not found.");
            }
            return body;
        }
    }

    private final FakeClient client = new FakeClient();
    private final BitbucketTools tools = new BitbucketTools(client);

    @Test
    void listCommitsEncodesBranchAndParses() {
        client.responses.put("rest/api/1.0/projects/PRJ/repos/svc/commits?until=release%2F6.6&limit=3", """
                {"values":[{"id":"abc123","message":"Fix bug","committerTimestamp":1763640000000,
                  "author":{"name":"jdoe","emailAddress":"j@x.com"}}]}""");
        List<BitbucketTools.CommitInfo> commits = tools.listCommits("PRJ", "svc", "release/6.6", 3);
        assertEquals(1, commits.size());
        assertEquals("abc123", commits.get(0).id());
        assertEquals("jdoe", commits.get(0).author());
        assertEquals("2025-11-20T12:00:00Z", commits.get(0).committedAt());
    }

    @Test
    void listReposWithoutProjectUsesGlobalEndpoint() {
        client.responses.put("rest/api/1.0/repos?limit=100", """
                {"values":[{"slug":"svc","name":"Svc","project":{"key":"PRJ"},
                  "links":{"self":[{"href":"https://bb/projects/PRJ/repos/svc/browse"}]}}]}""");
        List<BitbucketTools.RepoInfo> repos = tools.listRepos(null, null);
        assertEquals("PRJ", repos.get(0).project());
        assertEquals("https://bb/projects/PRJ/repos/svc/browse", repos.get(0).url());
    }

    @Test
    void pullRequestIncludesReviewersAndBuilds() {
        client.responses.put("rest/api/1.0/projects/PRJ/repos/svc/pull-requests/7", """
                {"id":7,"title":"T","state":"OPEN","createdDate":1,"updatedDate":2,
                 "author":{"user":{"displayName":"Ann"}},
                 "fromRef":{"displayId":"feature/x","latestCommit":"deadbeef"},"toRef":{"displayId":"master"},
                 "reviewers":[{"user":{"displayName":"Bob"},"status":"APPROVED"}]}""");
        client.responses.put("rest/build-status/1.0/commits/deadbeef?limit=25", """
                {"values":[{"state":"FAILED","key":"jenkins-1","name":"svc #5","url":"https://jenkins/5"}]}""");
        BitbucketTools.PullRequestDetails pr = tools.getPullRequest("PRJ", "svc", 7);
        assertEquals("Bob", pr.reviewers().get(0).name());
        assertEquals("APPROVED", pr.reviewers().get(0).status());
        assertEquals("FAILED", pr.builds().get(0).state());
        assertNull(pr.buildsError());
    }

    @Test
    void pullRequestStillReturnedWhenBuildStatusFails() {
        client.responses.put("rest/api/1.0/projects/PRJ/repos/svc/pull-requests/7", """
                {"id":7,"title":"T","state":"OPEN","fromRef":{"displayId":"f","latestCommit":"deadbeef"},
                 "toRef":{"displayId":"master"},"reviewers":[]}""");
        BitbucketTools.PullRequestDetails pr = tools.getPullRequest("PRJ", "svc", 7);
        assertEquals(7, pr.id());
        assertEquals(0, pr.builds().size());
        assertEquals("Bitbucket returned 404: not found.", pr.buildsError());
    }

    @Test
    void rejectsUnknownPullRequestState() {
        assertThrows(BitbucketException.class, () -> tools.listPullRequests("PRJ", "svc", "closed", null));
    }

    @Test
    void getFileTruncatesAndRejectsBinary() {
        client.responses.put("rest/api/1.0/projects/PRJ/repos/svc/raw/src/a.txt?at=master", "l1\nl2\nl3\n");
        BitbucketTools.FileContent f = tools.getFile("PRJ", "svc", "src/a.txt", "master", 2);
        assertEquals(3, f.totalLines());
        assertEquals(2, f.returnedLines());
        assertEquals("l1\nl2", f.content());

        client.responses.put("rest/api/1.0/projects/PRJ/repos/svc/raw/b.bin", "ab\0cd");
        assertThrows(BitbucketException.class, () -> tools.getFile("PRJ", "svc", "b.bin", null, null));
    }
}
