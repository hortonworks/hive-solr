package com.lucidworks.hadoop.fusion;

import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapred.Reporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class FusionPipelineClientTest {
  public static final String defaultWireMockRulePort = "8089";
  public static final String defaultFusionPort = "8764";
  public static final String defaultFusionServerHttpString = "http://";
  public static final String defaultHost = "localhost";
  public static final String defaultCollection = "test";
  public static final String defaultFusionIndexingPipeline = "conn_solr";
  public static final String defaultFusionProxyBaseUrl = "/api/apollo";
  public static final String defaultFusionIndexingPipelineUrlExtension = "/index-pipelines";
  public static final String defaultFusionSessionApi = "/api/session?realmName=";
  public static final String defaultFusionUser = "admin";
  public static final String defaultFusionPass = "password123";
  public static final String defaultFusionRealm = "native";
  public static final String defaultFusionIndexingPipelineUrlTerminatingString = "/index";
  public static final String defaultFusionSolrProxyUrlExtension = "/solr";

  public static String fusionServerHttpString;
  public static String fusionHost;
  public static String fusionApiPort;
  public static String wireMockRulePort = defaultWireMockRulePort;
  public static String fusionCollection;
  public static String fusionCollectionForUrl;
  public static String fusionIndexingPipeline;
  public static String fusionProxyBaseUrl;
  public static String fusionIndexingPipelineUrlExtension;
  public static String fusionSessionApi;
  public static String fusionUser;
  public static String fusionPass;
  public static String fusionRealm;
  public static String fusionSolrProxyUrlExtension;
  public static Boolean useWireMockRule = true;

  private static final Log log = LogFactory.getLog(FusionPipelineClient.class);

  static {

    Properties prop = new Properties();
    // you can override the built-in properties to test against a real fusion server if needed, default is to
    // use wire mock though
    File testPropsOverrideFile = new File("resources/FusionPipelineClientTest.xml");
    if (testPropsOverrideFile.isFile()) {
      try (InputStream in = new FileInputStream(testPropsOverrideFile)) {
        prop.loadFromXML(in);
        log.info("Loaded test properties override from: "+testPropsOverrideFile.getAbsolutePath());
      } catch (Exception exc) {
        throw new RuntimeException(exc);
      }
    }

    useWireMockRule = "true".equalsIgnoreCase(String.valueOf(prop.getProperty("useWireMockRule", "true")));
    if (useWireMockRule) {
      // Set host and port when using WireMockRules.
      fusionHost = prop.getProperty("wireMockRuleHost", defaultHost) + ":";
      fusionApiPort = prop.getProperty("wireMockRulePort", defaultWireMockRulePort);
      wireMockRulePort = fusionApiPort;
    } else {
      // Set host and port when connecting to Fusion.
      fusionHost = prop.getProperty("fusionHost", defaultHost) + ":";
      fusionApiPort = prop.getProperty("fusionApiPort", defaultFusionPort);
    }

    // Set http string (probably always either http:// or https://).
    fusionServerHttpString = prop.getProperty("fusionServerHttpString", defaultFusionServerHttpString);

    // Set collection.
    fusionCollection = prop.getProperty("fusionCollection", defaultCollection);
    fusionCollectionForUrl = "/" + fusionCollection;

    // Set the fusion indexing pipeline.
    fusionIndexingPipeline = "/" +  prop.getProperty("fusionIndexingPipeline", defaultFusionIndexingPipeline);

    // Set the fusion proxy base URL.
    fusionProxyBaseUrl = prop.getProperty("fusionProxyBaseUrl", defaultFusionProxyBaseUrl);

    // Set the fusion indexing pipeline URL extension.
    fusionIndexingPipelineUrlExtension = prop.getProperty("fusionIndexingPipelineUrlExtension", defaultFusionIndexingPipelineUrlExtension);

    // Set the fusion session API.
    fusionSessionApi = prop.getProperty("fusionSessionApi", defaultFusionSessionApi);

    // Set the fusion user.
    fusionUser = prop.getProperty("fusionUser", defaultFusionUser);

    // Set the fusion password.
    fusionPass = prop.getProperty("fusionPass", defaultFusionPass);

    // Set the fusion realm.
    fusionRealm = prop.getProperty("fusionRealm", defaultFusionRealm);

    // Set the fusion solr proxy URL extension.
    fusionSolrProxyUrlExtension = prop.getProperty("fusionSolrProxyUrlExtension", defaultFusionSolrProxyUrlExtension);
  }

  private static final String fusionCollectionApiStrValForUrl = "/collections";

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(Integer.parseInt(wireMockRulePort));

  protected List<LoggedRequest> loggedRequests = new ArrayList<>();

  protected String fusionUrl;
  protected String fusionPipelineUrlWithoutHostAndPort;
  protected String fusionSolrProxyWithoutHostAndPort;

  @Before
  public void setupWireMock() throws Exception {
    String fusionHostAndPort = "http://" + fusionHost + fusionApiPort;

    fusionPipelineUrlWithoutHostAndPort = fusionProxyBaseUrl + fusionIndexingPipelineUrlExtension +
            fusionIndexingPipeline + fusionCollectionApiStrValForUrl + fusionCollectionForUrl +
            defaultFusionIndexingPipelineUrlTerminatingString;
    fusionUrl = fusionHostAndPort + fusionPipelineUrlWithoutHostAndPort;
    fusionSolrProxyWithoutHostAndPort = fusionSolrProxyUrlExtension + fusionCollectionForUrl;

    wireMockRule.addMockServiceRequestListener(new RequestListener() {
      @Override
      public void requestReceived(Request request, Response response) {
        loggedRequests.add(LoggedRequest.createFrom(request));
      }
    });

    log.info("running with: fusionSolrProxyWithoutHostAndPort=" + fusionSolrProxyWithoutHostAndPort +
            " fusionPipelineUrlWithoutHostAndPort=" + fusionPipelineUrlWithoutHostAndPort +
            " wireMockRulePort=" + wireMockRulePort + " useWireMockRule=" + useWireMockRule);
  }

  @Test
  public void test() throws Exception {
    String badPath = "/api/apollo/index-pipelines/scottsCollection-default/collections/badCollection/index";
    String unauthPath = "/api/apollo/index-pipelines/scottsCollection-default/collections/unauthCollection/index";
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

    String fusionEndpoints = useWireMockRule ?
      fusionUrl+
        ",http://localhost:"+wireMockRulePort+"/not_and_endpoint/api" +
        ",http://localhost:"+wireMockRulePort+badPath+
        ",http://localhost:"+wireMockRulePort+unauthPath : fusionUrl;

    final FusionPipelineClient pipelineClient =
      new FusionPipelineClient(Reporter.NULL, fusionEndpoints, fusionUser, fusionPass, fusionRealm);

    int numThreads = 3;
    ExecutorService pool = Executors.newFixedThreadPool(numThreads);
    for (int t=0; t < numThreads; t++) {
      pool.submit(new Callable<Object>() {
        @Override
        public Object call() throws Exception {
          for (int i = 0; i < 10; i++) {
            try {
              pipelineClient.postBatchToPipeline(buildDocs(1));
            } catch (Exception exc) {
              log.error("\n\nFailed to postBatch due to: " + exc+"\n\n");
              throw new RuntimeException(exc);
            }
          }
          return null;
        }
      });
    }

    pool.shutdown(); // Disable new tasks from being submitted
    try {
      // Wait a while for existing tasks to terminate
      if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
        pool.shutdownNow(); // Cancel currently executing tasks
        // Wait a while for tasks to respond to being cancelled
        if (!pool.awaitTermination(30, TimeUnit.SECONDS))
          log.error("Pool did not terminate");
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      pool.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
    Thread.sleep(2000);
  }

  protected List<Map<String,Object>> buildDocs(int numDocs) {
    List<Map<String,Object>> docs = new ArrayList<Map<String,Object>>(numDocs);
    for (int n=0; n < numDocs; n++) {
      Map<String,Object> doc = new HashMap<String, Object>();
      doc.put("id", "doc"+n);
      doc.put("str_s", "str "+n);
      docs.add(doc);
    }
    return docs;
  }
}
