package com.lucidworks.hadoop.io;

import com.lucidworks.hadoop.security.SolrSecurity;

import org.apache.http.NoHttpResponseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.net.ConnectTimeoutException;
import org.apache.hadoop.util.Progressable;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class LucidWorksWriter {

  public static final String SOLR_ZKHOST = "solr.zkhost";
  public static final String SOLR_SERVER_URL = "solr.server.url";
  public static final String SOLR_COLLECTION = "solr.collection";

  private static Logger log = LoggerFactory.getLogger(LucidWorksWriter.class);
  protected int bufferSize;

  protected SolrClient solr;

  // key = Annotation type ; value = feature name / SOLR field
  protected Map<String, Map<String, String>> fieldMapping = new HashMap<String, Map<String, String>>();
  protected Progressable progress;
  private boolean includeMetadata = false;
  protected boolean includeAnnotations = false;
  protected boolean includeAllAnnotations = false;
  protected ModifiableSolrParams params = null;
  protected Collection<SolrInputDocument> buffer;
  protected boolean commitOnClose;
  protected int maxRetries = 3;
  protected int sleep = 5000;
  protected String name;

  public LucidWorksWriter(SolrClient server, Progressable progress) {
    this.solr = server;
    this.progress = progress;
  }

  public LucidWorksWriter(Progressable progress) {
    this.progress = progress;
  }

  public void open(Configuration job, String name) throws IOException {
    this.name = name;
    log.info("Opening LucidWorksWriter for {}", name);
    //If the solr server has not already been set, then set it from configs.
    if (solr == null) {
      SolrSecurity.setSecurityConfig(job);
      String zkHost = job.get(SOLR_ZKHOST);
      String collection = job.get(SOLR_COLLECTION, "collection1");
      if (zkHost != null && !zkHost.equals("")) {
        log.info("Indexing to collection: " + collection + " w/ ZK host: " + zkHost);
        solr = new CloudSolrClient.Builder()
            .withZkHost(zkHost)
            .build();
        ((CloudSolrClient) solr).setDefaultCollection(collection);
        ((CloudSolrClient) solr).connect();
      } else {
        String solrURL = job.get(SOLR_SERVER_URL);
        if (!solrURL.endsWith("/")) {
          solrURL += "/";
        }
        solrURL += collection;
        int queueSize = job.getInt("solr.client.queue.size", 100);
        int threadCount = job.getInt("solr.client.threads", 1);
        solr = new ConcurrentUpdateSolrClient.Builder(solrURL)
            .withQueueSize(queueSize)
            .withThreadCount(threadCount)
            .build();
      }
    }
    String paramsString = job.get("solr.params");
    if (paramsString != null) {
      params = new ModifiableSolrParams();
      String[] pars = paramsString.trim().split("\\&");
      for (String kvs : pars) {
        String[] kv = kvs.split("=");
        if (kv.length < 2) {
          log.warn("Invalid Solr param " + kvs + ", skipping...");
          continue;
        }
        params.add(kv[0], kv[1]);
      }
      log.info("Using Solr params: " + params.toString());
    }
    includeMetadata = job.getBoolean("lw.metadata", false);
    includeAnnotations = job.getBoolean("lw.annotations", false);
    popMappingsFromAnnotsTypesAndFeats(job);
    //Should we support buffering when writing?
    bufferSize = job.getInt("lww.buffer.docs.size", 500);
    if (bufferSize > 0) {
      buffer = new ArrayList<SolrInputDocument>(bufferSize);
    } else {
      buffer = new ArrayList<SolrInputDocument>();
    }
    commitOnClose = job.getBoolean("lww.commit.on.close", false);
    maxRetries = job.getInt("lww.max.retries", 3);
    sleep = job.getInt("lww.retry.sleep.time", 5000);
  }

  /**
   * Load up the types and features
   *
   * @param job
   */
  protected void popMappingsFromAnnotsTypesAndFeats(Configuration job) {
    // get the annotations types and features
    // to store as SOLR fields
    // solr.f.name = AnnotationType.featureName
    // e.g. solr.f.person = Person.string will map the "string" feature of "Person" annotations onto the Solr field "person"
    Iterator<Entry<String, String>> iterator = job.iterator();
    while (iterator.hasNext()) {
      Entry<String, String> entry = iterator.next();
      if (entry.getKey().startsWith("solr.f.") == false) {
        continue;
      }
      String solrFieldName = entry.getKey().substring("solr.f.".length());
      populateMapping(solrFieldName, entry.getValue());
    }
    // process lw.annotations.list
    String list = job.get("lw.annotations.list");
    if (list == null || list.trim().length() == 0) {
      return;
    }
    String[] names = list.split("\\s+");
    for (String name : names) {
      // support all annotations denoted by '*'
      if (name.equals("*")) {
        includeAllAnnotations = true;
      } else {
        String solrFieldName = "annotation_" + name;
        populateMapping(solrFieldName, name);
      }
    }
  }

  private void populateMapping(String solrFieldName, String value) {
    // see if a feature has been specified
    // if not we'll use '*' to indicate that we want
    // the text covered by the annotation
    //HashMap<String, String> featureValMap = new HashMap<String, String>();

    String[] toks = value.split("\\.");
    String annotationName = null;
    String featureName = null;
    if (toks.length == 1) {
      annotationName = toks[0];
    } else if (toks.length == 2) {
      annotationName = toks[0];
      featureName = toks[1];
    } else {
      log.warn("Invalid annotation field mapping: " + value);
    }

    Map<String, String> featureMap = fieldMapping.get(annotationName);
    if (featureMap == null) {
      featureMap = new HashMap<String, String>();
    }

    if (featureName == null) {
      featureName = "*";
    }

    featureMap.put(featureName, solrFieldName);
    fieldMapping.put(annotationName, featureMap);

    log.info("Adding mapping for annotation " + annotationName +
        ", feature '" + featureName + "' to  Solr field '" + solrFieldName + "'");
  }

  public void write(Text text, LWDocumentWritable doc) throws IOException {
    SolrInputDocument inputDoc = doc.getLWDocument().convertToSolr();
    try {
      log.debug("Buffering: {}", doc.getLWDocument().getId());
      buffer.add(inputDoc);
      if (buffer.size() >= bufferSize) {
        progress.progress();
        sendBuffer();
      }
    } catch (Exception e) {
      log.info("Enter retry logic with Exception" , e);
      maybeRetry(e);
    }
  }

  private void maybeRetry(Exception exc) throws IOException {
    maybeRetry(exc, 0);
  }

  private void maybeRetry(Exception exc, int times) throws IOException {
    Throwable rootCause = SolrException.getRootCause(exc);
    boolean wasCommonError = (rootCause instanceof ConnectException || rootCause instanceof ConnectTimeoutException ||
        rootCause instanceof NoHttpResponseException || rootCause instanceof SocketException);
    if (!wasCommonError) {
      // it was not a common exception just throw it.
      log.error("Not retying got: ", exc);
      throw makeIOException(exc);
    }
    if (times >= maxRetries) {
      log.info("Max retries reach: throwing the Exception", exc);
      throw makeIOException(exc);
    }
    try {
      Thread.sleep(sleep * times);
    } catch (InterruptedException e) {
      // ignore
    }
    try {
      times++;
      log.info("maybeRetry: Retrying " + times + " times.");
      sendBuffer();
      //success
    } catch (Exception e) {
      log.info("Failed again retrying ..." , e);
      maybeRetry(e, times);
    }

  }

  private void sendBuffer() throws SolrServerException, IOException {
    log.info("Sending {} documents", buffer.size());
    //flush the buffer
    if (params == null) {
      solr.add(buffer);
    } else {
      UpdateRequest req = new UpdateRequest();
      req.setParams(params);
      req.add(buffer);
      solr.request(req);
    }
    buffer.clear();
    //this shouldn't get hit if there are exceptions, so it should be fine here as part of our retry logic
  }

  public void commit() throws IOException, SolrServerException {
    solr.commit(false, false);
  }

  public void close() throws IOException {
    log.info("Closing the Writer");
    try {
      if (!buffer.isEmpty()) {
        try {
          sendBuffer();
          if (solr instanceof ConcurrentUpdateSolrClient) {
            log.info("Blocking until finished sending docs");
            ((ConcurrentUpdateSolrClient) solr).blockUntilFinished();
          }
          log.info("Done sending docs");
        } catch (Exception e) {
          log.info("Enter retry logic with Exception" , e);
          maybeRetry(e);
        }
      }
      if (commitOnClose) {
        log.info("Sending commit");
        solr.commit(false, false);
      }
      solr.close();
    } catch (final Exception e) {
      throw makeIOException(e);
    }
  }

  private static IOException makeIOException(Exception e) {
    final IOException ioe = new IOException();
    ioe.initCause(e);
    return ioe;
  }

  public void ping() throws IOException, SolrServerException {
    if (solr == null) {
      throw new SolrServerException("Solr server does not exist");
    } else {
      // Sending ping request. 
      solr.ping();
    }
  }
}
