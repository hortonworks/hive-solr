package com.lucidworks.hadoop.fusion;

import com.lucidworks.hadoop.io.LWDocument;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapred.Reporter;
import org.apache.http.client.HttpResponseException;
import org.apache.solr.common.SolrException;
import org.junit.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class QueryPipelineClientTest extends FusionPipelineClientTest {

  private static final Log log = LogFactory.getLog(QueryPipelineClientTest.class);

  private String fusionEndpoints;
  private String badPathEndpoint;
  private String unauthPathEndpoint;
  private String notAnEndpoint;

  @Override
  public void setupWireMock() throws Exception {
    super.setupWireMock();

    String badPath = "/api/apollo/query-pipelines/scottsCollection-default/collections/badCollection/index";
    String unauthPath = "/api/apollo/query-pipeline/scottsCollection-default/collections/unauthCollection/index";
    if (useWireMockRule) {
      // mock out the Pipeline API
      stubFor(post(urlEqualTo(fusionPipelineUrlWithoutHostAndPort)).willReturn(aResponse().withStatus(200)));

      // a bad node in the mix ... to test FusionPipelineClient error handling
      stubFor(post(urlEqualTo(badPath)).willReturn(aResponse().withStatus(500)));

      // another bad node in the mix which produces un-authorized errors... to test FusionPipelineClient error handling
      stubFor(post(urlEqualTo(unauthPath)).willReturn(aResponse().withStatus(401)));

      // mock out the Session API
      stubFor(post(urlEqualTo("/api/session?realmName=" + fusionRealm)).willReturn(aResponse().withStatus(200)));
    }

    badPathEndpoint = "http://localhost:" + wireMockRulePort + badPath;
    unauthPathEndpoint = "http://localhost:" + wireMockRulePort + unauthPath;
    notAnEndpoint = "http://localhost:" + wireMockRulePort + "/not_an_endpoint/api";

    fusionEndpoints = useWireMockRule ? fusionUrl +
        "," + notAnEndpoint +
        "," + badPathEndpoint +
        "," + unauthPathEndpoint : fusionUrl;
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testPostBatchToPipeline() throws Exception {
    final QueryPipelineClient pipelineClient =
        new QueryPipelineClient(Reporter.NULL, fusionEndpoints, fusionUser, fusionPass, fusionRealm, null);
    pipelineClient.postBatchToPipeline(buildDocs(1));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testGetBatchMultipleEndpoints() throws Exception {
    final QueryPipelineClient pipelineClient =
        new QueryPipelineClient(Reporter.NULL, fusionEndpoints, fusionUser, fusionPass, fusionRealm, null);
    pipelineClient.getBatchFromStartPointWithRetry(0, 100);
  }

  @Test(expected = HttpResponseException.class)
  public void testGetBatchBadEndpoint() throws Exception {
    final QueryPipelineClient pipelineClient =
        new QueryPipelineClient(Reporter.NULL, badPathEndpoint, fusionUser, fusionPass, fusionRealm, null);
    pipelineClient.getBatchFromStartPointWithRetry(0, 100);
  }

  @Test(expected = HttpResponseException.class)
  public void testGetBatchUnauthPathEndpoint() throws Exception {
    final QueryPipelineClient pipelineClient =
        new QueryPipelineClient(Reporter.NULL, unauthPathEndpoint, fusionUser, fusionPass, fusionRealm, null);
    pipelineClient.getBatchFromStartPointWithRetry(0, 100);
  }

  @Test(expected = SolrException.class)
  public void testGetBatchNotanEndpoint() throws Exception {
    final QueryPipelineClient pipelineClient =
        new QueryPipelineClient(Reporter.NULL, notAnEndpoint, fusionUser, fusionPass, fusionRealm, null);
    pipelineClient.getBatchFromStartPointWithRetry(0, 100);
  }

}
