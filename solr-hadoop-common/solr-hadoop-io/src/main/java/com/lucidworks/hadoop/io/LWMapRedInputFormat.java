package com.lucidworks.hadoop.io;

import com.lucidworks.hadoop.security.SolrSecurity;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LWMapRedInputFormat implements InputFormat<IntWritable, LWDocumentWritable> {

  private static Logger log = LoggerFactory.getLogger(LWMapRedInputFormat.class);

  // Static constants
  public static final String SOLR_ZKHOST = "solr.zkhost";
  public static final String SOLR_COLLECTION = "solr.collection";
  public static final String SOLR_SERVER_URL = "solr.server.url";
  public static final String SOLR_QUERY = "solr.query";

  public static final int DEFAULT_PAGE_SIZE = 1000;

  private JobConf conf;

  // Input split
  protected static class LWInputSplit implements InputSplit {
    protected boolean isZk;
    protected boolean isShard;
    protected String connectionUri;

    public LWInputSplit(boolean isZk, boolean isShard, String connectionUri) {
      this.isZk = isZk;
      this.isShard = isShard;
      this.connectionUri = connectionUri;
    }

    @Override
    public void write(DataOutput out) throws IOException {
      out.writeBoolean(isZk);
      out.writeBoolean(isShard);
      out.writeUTF(connectionUri);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
      isZk = in.readBoolean();
      isShard = in.readBoolean();
      connectionUri = in.readUTF();
    }

    @Override
    public long getLength() throws IOException {
      return 1L;
    }

    @Override
    public String[] getLocations() throws IOException {
      final ArrayList<String> results = new ArrayList<String>();
      final String pattern = "([0-9]{1,3}[\\.]){3}[0-9]{1,3}";
      final Pattern p = Pattern.compile(pattern);
      final Matcher m = p.matcher(connectionUri);

      while (m.find()) {
        results.add(m.group());
      }

      return results.toArray(new String[results.size()]);
    }

    // Helpers
    public boolean isZk() {
      return isZk;
    }

    public boolean isShard() {
      return isShard;
    }

    public String getConnectionUri() {
      return connectionUri;
    }

  }

  // RecordReader
  protected class LWRecordReader implements RecordReader<IntWritable, LWDocumentWritable> {
    protected SolrClient solr;
    protected SolrQuery query;
    protected String cursorMark = null;
    protected QueryResponse response;
    protected SolrDocumentList docs;

    protected int docsFound;
    protected int docCurrent;
    protected int currentTop;
    protected int totalDocsCounter = 0;

    public LWRecordReader(SolrClient solr, SolrQuery query, String cursorMark) {
      this.solr = solr;
      this.query = query;
      this.cursorMark = cursorMark;
    }

    @Override
    public boolean next(IntWritable key, LWDocumentWritable value) throws IOException {
      if (response == null) {
        try {
          if (cursorMark != null) {
            query.setStart(0);
            query.set("cursorMark", cursorMark);
          } else {
            query.setStart(currentTop);
          }
          response = solr.query(query);
          docs = response.getResults();
          docsFound = (int) docs.getNumFound();
          if (cursorMark != null) {
            cursorMark = response.getNextCursorMark();
          }
        } catch (SolrServerException ex) {
          throw new IOException("error querying Solr", ex);
        }
      }
      int docsSize = docs.size();
      if (docCurrent < docsSize) {
        LWDocument newDoc = getNextDocument(docs.get(docCurrent));
        key.set(docCurrent);
        value.setLWDocument(newDoc);
        totalDocsCounter++;
        docCurrent++;
        currentTop++;
        return true;
      } else {
        try {
          log.debug("No more results, querying Solr, starting doc number in " + currentTop + " of "
              + docsFound);
          if (cursorMark != null) {
            query.setStart(0);
            query.set("cursorMark", cursorMark);
          } else {
            query.setStart(currentTop);
          }
          response = solr.query(query);
          docs = response.getResults();
          if (cursorMark != null) {
            String nextCursorMark = response.getNextCursorMark();
            if (cursorMark.equals(nextCursorMark)) {
              // No more docs.
              return false;
            }
            cursorMark = nextCursorMark;
            LWDocument newDoc = getNextDocument(docs.get(0));
            key.set(0);
            value.setLWDocument(newDoc);
            docCurrent = 1;
            return true;
          }
          if (docs.size() > 0) {
            docCurrent = 0;
            return true;
          }
        } catch (SolrServerException ex) {
          throw new IOException("error querying Solr", ex);
        }
      }

      log.info("Total documents retrieved: " + totalDocsCounter);
      return false;
    }

    @Override
    public IntWritable createKey() {
      return new IntWritable();
    }

    @Override
    public LWDocumentWritable createValue() {
        return new LWDocumentWritable(
            LWDocumentProvider.createDocument());

    }

    @Override
    public long getPos() throws IOException {
      return docCurrent;
    }

    @Override
    public void close() throws IOException {
      solr.close();
    }

    @Override
    public float getProgress() throws IOException {
      if (docsFound == 0) {
        return 1.0f;
      }
      return docCurrent / docsFound;
    }

    private LWDocument getNextDocument(SolrDocument curDoc) {
      LWDocument newDoc = LWDocumentProvider.createDocument();
      for (String field : curDoc.getFieldNames()) {
        if (field.equals("id")) {
          newDoc.setId((String) curDoc.get(field));
        } else {
          newDoc.addField(field, curDoc.get(field));
        }

      }
      return newDoc;
    }
  }

  @Override
  public InputSplit[] getSplits(JobConf job, int numSplits) throws IOException {
    this.conf = job;
    boolean isZk = false;
    String connectionUri = job.get(SOLR_ZKHOST);
    String collection = job.get(SOLR_COLLECTION);
    List<String> shards = null;

    if (collection == null) {
      throw new IOException(SOLR_COLLECTION + " not provided or is empty");
    }

    if (connectionUri != null && connectionUri != "") {
      isZk = true;
      CloudSolrClient solrCloud = null;
      try {
        solrCloud = new CloudSolrClient.Builder()
            .withZkHost(connectionUri)
            .build();
        solrCloud.setDefaultCollection(collection);
        solrCloud.connect();
        // first get a list of replicas to query for this collection
        shards = buildShardList(solrCloud, collection);
      } catch (Exception ex) {
        log.warn("Error getting the Shard: " + ex.getMessage());
      } finally {
        if (solrCloud != null) {
          solrCloud.close();
        }
      }

    } else {
      connectionUri = job.get(SOLR_SERVER_URL);
    }

    if (connectionUri == null || connectionUri == "") {
      throw new IOException(SOLR_ZKHOST + " nor " + SOLR_SERVER_URL + " provided or is empty");
    }

    InputSplit[] splits = null;
    if (shards != null) {
      //Create more splits.
      splits = new InputSplit[shards.size()];
      for (int i = 0; i < shards.size(); i++) {
        splits[i] = new LWInputSplit(isZk, true, shards.get(i));
      }
    } else {
      splits = new InputSplit[] { new LWInputSplit(isZk, false, connectionUri) };
    }

    return splits;
  }

  @Override
  public RecordReader<IntWritable, LWDocumentWritable>  getRecordReader(
      InputSplit split, JobConf job, Reporter reporter) throws IOException {

    LWInputSplit lwSplit = (LWInputSplit) split;
    SolrClient solr;
    String collection = job.get(SOLR_COLLECTION);
    String query = job.get(SOLR_QUERY, "*:*");
    if (lwSplit.isZk) {
      SolrSecurity.setSecurityConfig(job);
      if (lwSplit.isShard) {
        solr = new HttpSolrClient.Builder()
            .withBaseSolrUrl(lwSplit.getConnectionUri())
            .build();
      } else {
        // somehow the list of shard is unavailable
        solr = new CloudSolrClient.Builder()
            .withZkHost(lwSplit.getConnectionUri())
            .build();
        ((CloudSolrClient) solr).setDefaultCollection(collection);
      }

    } else {
      String connectionUri = lwSplit.getConnectionUri();
      int queueSize = job.getInt("solr.client.queue.size", 100);
      int threadCount = job.getInt("solr.client.threads", 1);

      if (!connectionUri.endsWith("/")) {
        connectionUri += "/";
      }

      connectionUri += collection;
      solr = new ConcurrentUpdateSolrClient.Builder(connectionUri)
          .withQueueSize(queueSize)
          .withThreadCount(threadCount)
          .build();
    }

    SolrQuery solrQuery = new SolrQuery(query);
    solrQuery.set("distrib", false);
    solrQuery.set("collection", collection);
    if (solrQuery.getRows() == null) {
      solrQuery.setRows(DEFAULT_PAGE_SIZE); // default page size
    }
    solrQuery.setSort(SolrQuery.SortClause.asc("id"));

    return new LWRecordReader(solr, solrQuery, "*");
  }

  protected List<String> buildShardList(CloudSolrClient cloudSolrServer, final String collection) {
    ZkStateReader zkStateReader = cloudSolrServer.getZkStateReader();

    ClusterState clusterState = zkStateReader.getClusterState();
    DocCollection docCollection = clusterState.getCollection(collection);
    Collection<Slice> slices = docCollection.getSlices();

    if (slices == null) {
      throw new IllegalArgumentException("Collection " + collection + " not found!");
    }

    Set<String> liveNodes = clusterState.getLiveNodes();
    Random random = new Random();
    List<String> shards = new ArrayList<String>();
    for (Slice slice : slices) {
      List<String> replicas = new ArrayList<String>();
      for (Replica r : slice.getReplicas()) {
        ZkCoreNodeProps replicaCoreProps = new ZkCoreNodeProps(r);
        if (liveNodes.contains(replicaCoreProps.getNodeName())) {
          replicas.add(replicaCoreProps.getCoreUrl());
        }
      }
      int numReplicas = replicas.size();
      if (numReplicas == 0) {
        throw new IllegalStateException(
            "Shard " + slice.getName() + " does not have any active replicas!");
      }

      String replicaUrl = (numReplicas == 1) ?
          replicas.get(0) :
          replicas.get(random.nextInt(replicas.size()));
      shards.add(replicaUrl);
    }

    return shards;
  }

}
