package com.lucidworks.hadoop.fusion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lucidworks.hadoop.fusion.dto.FusionDocument;
import com.lucidworks.hadoop.fusion.utils.FusionResponseHandler;
import com.lucidworks.hadoop.fusion.utils.FusionURLBuilder;
import com.lucidworks.hadoop.io.LWDocument;
import com.lucidworks.hadoop.security.FusionKrb5HttpClientConfigurer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapred.Reporter;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class QueryPipelineClient extends FusionPipelineClient {

  private static final Log LOG = LogFactory.getLog(QueryPipelineClient.class);

  private String query = null;
  private int docsFound = 0;
  private boolean hasMoreDocs = false;

  private static final FusionResponseHandler HANDLER;
  static {
    Gson json = new GsonBuilder().setPrettyPrinting().create();
    HANDLER = new FusionResponseHandler(json);
  }

  public QueryPipelineClient(Reporter reporter, String endpointUrl, String query) throws
      MalformedURLException {
    super(reporter, endpointUrl);
    this.query = query;
  }

  public QueryPipelineClient(Reporter reporter, String endpointUrl, String fusionUser, String fusionPass, String
      fusionRealm, String query) throws
      MalformedURLException {
    super(reporter, endpointUrl, fusionUser, fusionPass, fusionRealm);
    this.query = query;
  }

  public synchronized List<LWDocument> getBatchFromStartPointWithRetry(int start, int rows) throws IOException {

    if (originalEndpoints.size() != 1) {
      throw new UnsupportedOperationException("Must only retrieve from one endpoint.");
    }

    String endpoint = originalEndpoints.get(0);
    int requestId = requestCounter.incrementAndGet();

    try {
      return getBatchFromStartPoint(endpoint, start, rows, requestId);
    } catch (MalformedURLException e) {
      // Can't send the request
      throw new MalformedURLException("The url provided is malformed [" + endpoint + "]");
    } catch (IOException exc) {
      LOG.warn("Failed to get request " + requestId + " to '" + endpoint + "' due to: " + exc);
      // Waiting and retry
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ignore) {
        Thread.interrupted();
      }
      // Incrementing the request id debugging
      requestId = requestCounter.incrementAndGet();
      return getBatchFromStartPoint(endpoint, start, rows, requestId);
    }
  }

  private List<LWDocument> getBatchFromStartPoint(String endpoint, int start, int rows, int requestId) throws
      IOException {
    try {
      String queryEndpoint = new FusionURLBuilder(new URI(endpoint))
          .addQuery(query)
          .addStart(start)
          .addRows(rows)
          .toString();
      LOG.debug("Requesting docs from: " + queryEndpoint + " requestId[" + requestId + "]");

      List<LWDocument> list;
      list = executeGet(queryEndpoint).docs();
      hasMoreDocs = rows == list.size();
      return list;

    } catch (URISyntaxException | MalformedURLException e) {
      throw new MalformedURLException("The url provided is malformed [" + endpoint + "]");
    }
  }

  public void postBatchToPipeline(List docs) throws Exception {
    throw new UnsupportedOperationException("Can not post docs through a query endpoint.");
  }

  public int getDocumentAmount() {
    return docsFound;
  }

  private FusionDocument executeGet(String uri) throws IOException {
    HttpGet getRequest = new HttpGet(uri);
    FusionDocument document = null;
    HttpClientContext context = null;

    if (isKerberos) {
      httpClient = FusionKrb5HttpClientConfigurer.createClient(fusionUser);
      document = httpClient.execute(getRequest, HANDLER);
    } else {
      context = HttpClientContext.create();
      if (cookieStore != null) {
        context.setCookieStore(cookieStore);
      }
      document = httpClient.execute(getRequest, HANDLER, context);
    }
    docsFound = document.numFound();
    return document;
  }
}
