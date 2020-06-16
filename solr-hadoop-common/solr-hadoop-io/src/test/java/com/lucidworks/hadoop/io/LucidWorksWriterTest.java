package com.lucidworks.hadoop.io;

import com.google.common.collect.Lists;
import com.lucidworks.hadoop.io.impl.LWMockDocument;
import com.lucidworks.hadoop.utils.SolrCloudClusterSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.Progressable;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.util.*;

public class LucidWorksWriterTest extends SolrCloudClusterSupport {

  private transient static Logger log = LoggerFactory.getLogger(LucidWorksWriterTest.class);

  @Before
  public void setUp() throws Exception {
    removeAllDocs();
  }

  @Test
  public void test() throws IOException, SolrServerException {
    MockProgressable progressable = new MockProgressable();
    LucidWorksWriter lucidWorksWriter = new LucidWorksWriter(progressable);

    Configuration conf = new Configuration();
    conf.set(LucidWorksWriter.SOLR_ZKHOST, getBaseUrl());
    conf.set(LucidWorksWriter.SOLR_COLLECTION, DEFAULT_COLLECTION);
    conf.set("lww.commit.on.close", "true");
    conf.setBoolean("lw.annotations", true);
    conf.setBoolean("lw.metadata", true);
    conf.set("solr.f.junk_annot_1_s", "foo.annot_1");
    lucidWorksWriter.open(conf, "name");

    lucidWorksWriter.write(new Text("text"), LucidWorksWriterTest
        .createLWDocumentWritable("id-1", "field-1_s", "field-value-1", "field-2_s", "field-value-2",
            "field-3_s", "field-value-3"));

    LWDocumentWritable doc = LucidWorksWriterTest
        .createLWDocumentWritable("id-2", "field-1_s", "This is field value 1", "field-2_s",
            "This is field value 2.  It is longer than field value 1.", "field-3_s",
            "This is field value 3.  It is longer than both field value 1 and field value 2.");
    //add annotations
    LWDocument pipeDoc = doc.getLWDocument();
    pipeDoc.addField("field-2_ss", "This is a 2nd entry for field value 2");

    //add metadata
    for (int i = 0; i < 6; i++) {
      pipeDoc.addMetadata("meta_" + i, "meta_value_" + i);
    }
    lucidWorksWriter.write(new Text("text"), doc);
    lucidWorksWriter.commit();
    lucidWorksWriter.close();

    assertQ("id:id-1", 1, "field-1_s", "field-value-1", "field-2_s", "field-value-2", "field-3_s",
        "field-value-3");
    assertQ("*:*", 2);
    //Metadata
    assertQ("meta_0:meta_value_0", 1);
  }

  @Test
  public void testManyDocs() throws IOException, SolrServerException {
    MockProgressable progressable = new MockProgressable();
    LucidWorksWriter lucidWorksWriter = new LucidWorksWriter(progressable);
    Configuration conf = new Configuration();
    conf.set(LucidWorksWriter.SOLR_ZKHOST, getBaseUrl());
    conf.set(LucidWorksWriter.SOLR_COLLECTION, DEFAULT_COLLECTION);
    conf.set("lww.commit.on.close", "true");
    lucidWorksWriter.open(conf, "name");
    //make sure we trigger the buffering and have left overs
    int totalDocs = 5051;
    for (int counter = 0; counter < totalDocs; counter++) {
      lucidWorksWriter.write(new Text("text-" + counter), LucidWorksWriterTest
        .createLWDocumentWritable("id-" + counter, "field-1", "field-value-1", "field-2",
          "field-value-2", "field-3", "field-value-3"));

    }
    lucidWorksWriter.close();
    assertCount("*:*", totalDocs);
  }

  @Test
  public void testRetry() throws Exception {
    MockProgressable progressable = new MockProgressable();

    // Max retries
    int max = 10;

    // wrapping SolrCloud
    SolrClient fakeServer = new FakeRetrySolrServer(getBaseUrl(), false, max);
    LucidWorksWriter lucidWorksWriter = new LucidWorksWriter(fakeServer, progressable);
    Configuration conf = new Configuration();
    conf.set(LucidWorksWriter.SOLR_ZKHOST, getBaseUrl());
    conf.set("lww.commit.on.close", "true");
    conf.setInt("lww.retry.sleep.time", 1);//make the sleep really short
    conf.setInt("lww.buffer.docs.size", 1000);
    conf.setInt("lww.max.retries", max);

    lucidWorksWriter.open(conf, "name");
    //make sure we trigger the buffering
    int totalDocs = 5001;
    for (int counter = 0; counter < totalDocs; counter++) {
      lucidWorksWriter.write(new Text("text-" + counter), LucidWorksWriterTest
          .createLWDocumentWritable("id-" + counter, "field-1", "field-value-1", "field-2",
              "field-value-2", "field-3", "field-value-3"));

    }
    lucidWorksWriter.close();
    assertCount("*:*", totalDocs);
  }

  @Test(expected = IOException.class)
  public void testFailRetries() throws Exception {
    MockProgressable progressable = new MockProgressable();

    // Max retries
    int max = 10;
    // wrapping SolrCloud
    SolrClient fakeServer = new FakeRetrySolrServer(getBaseUrl(), false, max);
    LucidWorksWriter lucidWorksWriter = new LucidWorksWriter(fakeServer, progressable);
    Configuration conf = new Configuration();
    conf.set(LucidWorksWriter.SOLR_ZKHOST, getBaseUrl());
    conf.set("lww.commit.on.close", "true");
    conf.setInt("lww.retry.sleep.time", 1);//make the sleep really short
    conf.setInt("lww.buffer.docs.size", 1000);
    // Less retries than failures in FakeRetrySolrServer
    conf.setInt("lww.max.retries", max-1);
    lucidWorksWriter.open(conf, "name");
    //make sure we trigger the buffering
    int totalDocs = 5001;
    for (int counter = 0; counter < totalDocs; counter++) {
      lucidWorksWriter.write(new Text("text-" + counter), LucidWorksWriterTest
        .createLWDocumentWritable("id-" + counter, "field-1", "field-value-1", "field-2",
          "field-value-2", "field-3", "field-value-3"));

    }
  }

  @Test(expected = IOException.class)
  public void testFail() throws Exception {
    MockProgressable progressable = new MockProgressable();
    SolrClient fakeServer = new FakeRetrySolrServer(getBaseUrl(), true, 3);
    LucidWorksWriter lucidWorksWriter = new LucidWorksWriter(fakeServer, progressable);
    Configuration conf = new Configuration();
    conf.set(LucidWorksWriter.SOLR_ZKHOST, getBaseUrl());
    conf.set("lww.commit.on.close", "true");
    conf.setInt("lww.buffer.docs.size", 1); // buffer size small
    conf.setInt("lww.retry.sleep.time", 1);//make the sleep really short

    lucidWorksWriter.open(conf, "name");
      lucidWorksWriter.write(new Text("text-1"), LucidWorksWriterTest
        .createLWDocumentWritable("id-1", "field-1", "field-value-1", "field-2", "field-value-2", "field-3", "field-value-3"));
  }

  public class MockProgressable implements Progressable {

    @Override
    public void progress() {
      // Do nothing, just for test
    }
  }

  @SuppressWarnings("deprecation")
  private class FakeRetrySolrServer extends SolrClient {
    private final SolrClient delegate;
    int calls = 0;
    private boolean alwaysFail;
    int retries;

    public FakeRetrySolrServer(String zkString, boolean alwaysFail, int retries) {
      final List<String> zkHostList = Lists.newArrayList(zkString);
      final CloudSolrClient client = new CloudSolrClient.Builder(zkHostList, Optional.empty()).build();
      client.setDefaultCollection(DEFAULT_COLLECTION);
      client.connect();
      this.delegate = client;
      this.alwaysFail = alwaysFail;
      this.retries = retries;
    }

    private void failRequest() throws SolrServerException, IOException {
      if (alwaysFail) {
        throw new SolrServerException("alwaysFail is true");
      }
      if (calls <= retries) {
        throw new SocketException("fake socket exception to cause a retry");
      }
    }

    @Override
    public NamedList<Object> request(SolrRequest request, String collection)
        throws SolrServerException, IOException {
      calls++;
      failRequest();
      return delegate.request(request);
    }

    @Override
    public UpdateResponse add(Collection<SolrInputDocument> docs) throws SolrServerException, IOException {
      calls++;
      failRequest();
      return delegate.add(docs);
    }

    @Override
    public void close() {
      try {
        delegate.close();
      } catch (IOException e) {
        log.warn("closing");
      }
    }
  }

  public static LWDocumentWritable createLWDocumentWritable(String id, String... keyValues) {
    Map<String, String> fields = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      fields.put(keyValues[i], keyValues[i + 1]);
    }
    LWMockDocument doc = new LWMockDocument(id, fields);
    return new LWDocumentWritable(doc);
  }
}
