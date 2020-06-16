package com.lucidworks.hadoop.io;

import com.lucidworks.hadoop.fusion.QueryPipelineClient;
import com.lucidworks.hadoop.fusion.utils.FusionURLBuilder;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.lucidworks.hadoop.fusion.Constants.FUSION_AUTHENABLED;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_LOGIN_APP_NAME;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_LOGIN_CONFIG;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_PASS;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_QUERY;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_QUERY_ENDPOINT;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_REALM;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_USER;

public class FusionInputFormat implements InputFormat<IntWritable, LWDocumentWritable> {

  private static Logger log = LoggerFactory.getLogger(FusionInputFormat.class);

  private JobConf conf;

  // Input split
  protected static class FusionInputSplit implements InputSplit {
    protected String queryPipeline;

    public FusionInputSplit(String queryPipeline) {
      this.queryPipeline = queryPipeline;
    }

    @Override
    public void write(DataOutput out) throws IOException {
      out.writeUTF(queryPipeline);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
      queryPipeline = in.readUTF();
    }

    @Override
    public long getLength() throws IOException {
      return 1L;
    }

    @Override
    public String[] getLocations() throws IOException {
      //Dummy location
      return new String[0];
    }

    // Helpers
    public String getQueryPipeline() {
      return queryPipeline;
    }

  }

  // RecordReader
  protected class FusionRecordReader implements RecordReader<IntWritable, LWDocumentWritable> {

    protected QueryPipelineClient fusion;
    protected List<LWDocument> docs;

    protected int docsFound;
    protected int docCurrent = 1;
    protected int start = 0;
    protected int batchSize = 100; // configurable?
    protected int totalDocsCounter = 0;

    public FusionRecordReader(QueryPipelineClient fusion) {
      this.fusion = fusion;

    }

    @Override
    public boolean next(IntWritable key, LWDocumentWritable value) throws IOException {
      if (docs == null) {
        // First Query
        try {
          docs = fusion.getBatchFromStartPointWithRetry(start, batchSize);
          start += batchSize + 1;
          docsFound = fusion.getDocumentAmount(); // total docs
        } catch (Exception ex) {
          throw new IOException("error querying Fusion", ex);
        }
      }
      int docsSize = docs.size();
      if (docCurrent < docsSize) {
        LWDocument newDoc = (docs.get(docCurrent));
        key.set(docCurrent);
        value.setLWDocument(newDoc);
        totalDocsCounter++;
        docCurrent++;
        return true;
      } else {
        try {
          if (start >= docsFound) {
            log.info("Total documents retrieved: " + totalDocsCounter);
            return false;
          }
          log.debug("No more results, querying Fusion");
          docs = fusion.getBatchFromStartPointWithRetry(start, batchSize);
          start += batchSize + 1;
          LWDocument newDoc = (docs.get(0));
          key.set(0);
          value.setLWDocument(newDoc);
          docCurrent = 1;
          return true;
        } catch (Exception ex) {
          throw new IOException("error querying Fusion", ex);
        }
      }
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
      fusion.shutdown();
    }

    @Override
    public float getProgress() throws IOException {
      if (docsFound == 0) {
        return 1.0f;
      }
      return docCurrent / docsFound;
    }
  }

  @Override
  public InputSplit[] getSplits(JobConf job, int numSplits) throws IOException {
    this.conf = job;
    String queryEndpoints = job.get(FUSION_QUERY_ENDPOINT);
    Set<String> endpointsSet = new HashSet<>(Arrays.asList(queryEndpoints.split(",")));
    InputSplit[] splits = new InputSplit[endpointsSet.size()];
    int i = 0;
    for (String endpoint : endpointsSet) {
      // check url here; failing fast
      try {
        new FusionURLBuilder(new URI(endpoint));
      } catch (MalformedURLException | URISyntaxException e) {
        throw new IOException("The url provided is malformed [" + endpoint + "]: {}", e);
      }
      splits[i++] = new FusionInputSplit(endpoint);
    }
    return splits;
  }

  @Override
  public RecordReader<IntWritable, LWDocumentWritable> getRecordReader(
      InputSplit split, JobConf job, Reporter reporter) throws IOException {
    FusionInputSplit fusionSplit = (FusionInputSplit) split;

    boolean fusionAuthEnabled = "true".equals(job.get(FUSION_AUTHENABLED, "true"));
    String fusionUser = job.get(FUSION_USER);
    String fusionPass = job.get(FUSION_PASS);
    String fusionRealm = job.get(FUSION_REALM);
    String fusionQuery = job.get(FUSION_QUERY, "*:*");

    String fusionLoginConfig = job.get(FUSION_LOGIN_CONFIG);
    if (fusionLoginConfig != null) {
      System.setProperty(FUSION_LOGIN_CONFIG, fusionLoginConfig);
    }

    String fusionLoginAppName =  job.get(FUSION_LOGIN_APP_NAME);
    if (fusionLoginAppName != null) {
      System.setProperty(FUSION_LOGIN_APP_NAME, fusionLoginAppName);
    }

    // One query endpoint per client
    QueryPipelineClient queryClient = fusionAuthEnabled
        ? new QueryPipelineClient(reporter, fusionSplit.getQueryPipeline(), fusionUser, fusionPass, fusionRealm,
        fusionQuery)
        : new QueryPipelineClient(reporter, fusionSplit.getQueryPipeline(), fusionQuery);
    return new FusionRecordReader(queryClient);
  }
}
