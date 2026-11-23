package com.example.jenkinsmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JenkinsClientTest {

    @Test
    void convertsFolderPathToJenkinsPath() {
        assertEquals("job/qa/job/payments%20api/", JenkinsClient.jobPath("qa/payments api"));
    }

    @Test
    void emptyJobMeansRoot() {
        assertEquals("", JenkinsClient.jobPath(""));
        assertEquals("", JenkinsClient.jobPath(null));
    }

    @Test
    void treeQueryIsAValidUri() {
        String path = JenkinsClient.jobPath("TEAM/my-service")
                + JenkinsClient.apiJson("builds[number,result]{0,3}");
        assertEquals("job/TEAM/job/my-service/api/json?tree=builds%5Bnumber%2Cresult%5D%7B0%2C3%7D", path);
        java.net.URI.create("https://jenkins.example.com/" + path);
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(JenkinsException.class, () -> JenkinsClient.jobPath("qa/../admin"));
    }
}
