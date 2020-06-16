package com.lucidworks.hadoop.io;

import com.lucidworks.hadoop.fusion.FusionPipelineClient;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Progressable;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.lucidworks.hadoop.fusion.Constants.BATCH_ID_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_COLLECTION_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_PIPELINE_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_TYPE_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_AUTHENABLED;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_BATCHID;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_BATCHSIZE;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_BUFFER_TIMEOUTMS;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_DATASOURCE;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_DATASOURCE_PIPELINE;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_DATASOURCE_TYPE;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_FAIL_ON_ERROR;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_INDEX_ENDPOINT;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_LOGIN_APP_NAME;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_LOGIN_CONFIG;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_PASS;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_REALM;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_USER;
import static com.lucidworks.hadoop.fusion.Constants.UNKNOWN;

public class FusionOutputFormat implements OutputFormat<Text, LWDocumentWritable> {

  private transient static Logger log = LoggerFactory.getLogger(FusionOutputFormat.class);

  public static class FusionRecordWriter implements RecordWriter<Text, LWDocumentWritable> {

    private final FusionPipelineClient fusionPipelineClient;
    private final Progressable progressable;
    private final DocBuffer docBuffer;
    private final boolean failOnError;
    private Configuration jobConf;

    public FusionRecordWriter(Configuration job, String name, Progressable progressable) throws IOException {
      this.progressable = progressable;

      int batchSize = Integer.parseInt(job.get(FUSION_BATCHSIZE, "500"));
      long bufferTimeoutMs = Long.parseLong(job.get(FUSION_BUFFER_TIMEOUTMS, "1000"));
      this.docBuffer = new DocBuffer(batchSize, bufferTimeoutMs);

      this.failOnError = "true".equals(job.get(FUSION_FAIL_ON_ERROR, "false"));

      this.jobConf = job;
      boolean fusionAuthEnabled = "true".equals(job.get(FUSION_AUTHENABLED, "true"));
      String fusionUser = job.get(FUSION_USER);
      String fusionPass = job.get(FUSION_PASS);
      String fusionRealm = job.get(FUSION_REALM);
      String endpoints = job.get(FUSION_INDEX_ENDPOINT);

      String fusionLoginConfig = job.get(FUSION_LOGIN_CONFIG);
      if (fusionLoginConfig != null) {
        System.setProperty(FUSION_LOGIN_CONFIG, fusionLoginConfig);
      }

      String fusionLoginAppName = job.get(FUSION_LOGIN_APP_NAME);
      if (fusionLoginAppName != null) {
        System.setProperty(FUSION_LOGIN_APP_NAME, fusionLoginAppName);
      }

      Reporter reporter = null;
      if (progressable instanceof Reporter) {
        reporter = (Reporter) progressable;
      } else {
        reporter = Reporter.NULL;
        log.warn("Progressable passed to FusionOutputFormat is not a Reporter, so no counters will be provided.");
      }

      fusionPipelineClient = fusionAuthEnabled
          ? new FusionPipelineClient(reporter, endpoints, fusionUser, fusionPass, fusionRealm)
          : new FusionPipelineClient(reporter, endpoints);
    }

    @Override
    public void write(Text text, LWDocumentWritable documentWritable) throws IOException {
      docBuffer.add(doc2json(documentWritable.getLWDocument().convertToSolr()));

      if (docBuffer.shouldFlushBuffer()) {
        try {
          fusionPipelineClient.postBatchToPipeline(docBuffer.buffer);
          progressable.progress();
        } catch (Exception exc) {
          processWriteException(exc);
        } finally {
          docBuffer.reset();
        }
      }
    }

    private void processWriteException(Exception exc) throws IOException {
      if(failOnError) {
        if (exc instanceof IOException) {
          throw (IOException) exc;
        } else {
          throw new IOException("Failed to send final batch to Fusion due to: " + exc, exc);
        }
      }
      for (Object doc: docBuffer.buffer) {
        try{
          fusionPipelineClient.postBatchToPipeline(new ArrayList<>(Arrays.asList(doc)));
        } catch (Exception excSingle) {
          log.warn("Failed to send: " + doc);
        }
      }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> doc2json(SolrInputDocument solrDoc) {
      Map<String, Object> json = new HashMap<String, Object>();
      String docId = (String) solrDoc.getFieldValue("id");
      if (docId == null) {
        throw new IllegalStateException("Couldn't resolve the id for document: " + solrDoc);
      }
      json.put("id", docId);

      List fields = new ArrayList();
      for (String f : solrDoc.getFieldNames()) {
        if (!"id".equals(f)) { // id already added
          appendField(solrDoc, f, null, fields);
        }
      }
      // keep track of the time we saw this doc on hadoop side
      fields.add(mapField("_hadoop_tdt", null, DateTimeFormatter.ISO_INSTANT.format(Instant.now())));

      // Adds some fields for fusion
      addFusionFieldsIfNeeded(fields);

      json.put("fields", fields);
      return json;
    }

    @SuppressWarnings("unchecked")
    protected void appendField(SolrInputDocument doc, String f, String pfx, List fields) {
      SolrInputField field = doc.getField(f);
      int vc = field.getValueCount();
      if (vc <= 0)
        return; // no values to add for this field

      if (vc == 1) {
        Map<String, Object> fieldMap = mapField(f, pfx, field.getFirstValue());
        if (fieldMap != null)
          fields.add(fieldMap);
      } else {
        for (Object val : field.getValues()) {
          Map<String, Object> fieldMap = mapField(f, pfx, val);
          if (fieldMap != null)
            fields.add(fieldMap);
        }
      }
    }

    protected Map<String, Object> mapField(String f, String pfx, Object val) {
      Map<String, Object> fieldMap = new HashMap<String, Object>(10);
      String fieldName = (pfx != null) ? pfx + f : f;
      fieldMap.put("name", fieldName);
      fieldMap.put("value", val);
      return fieldMap;
    }

    public void close(Reporter reporter) throws IOException {
      if (!docBuffer.buffer.isEmpty()) {
        try {
          fusionPipelineClient.postBatchToPipeline(docBuffer.buffer);
        } catch (Exception exc) {
          processWriteException(exc);
        } finally {
          docBuffer.reset();
        }
      }
      fusionPipelineClient.shutdown();
    }

    @SuppressWarnings("unchecked")
    private void addFusionFieldsIfNeeded(List fields) {
      String datasource = jobConf.get(FUSION_DATASOURCE);
      if (datasource != null && !datasource.isEmpty()) {
        fields.add(mapField(DATA_SOURCE_FIELD, null, datasource));
        fields.add(mapField(DATA_SOURCE_COLLECTION_FIELD, null,
            jobConf.get(LucidWorksWriter.SOLR_COLLECTION, UNKNOWN)));
        fields.add(mapField(DATA_SOURCE_PIPELINE_FIELD, null, jobConf.get(FUSION_DATASOURCE_PIPELINE, UNKNOWN)));
        fields.add(mapField(DATA_SOURCE_TYPE_FIELD, null, jobConf.get(FUSION_DATASOURCE_TYPE, UNKNOWN)));
        fields.add(mapField(BATCH_ID_FIELD, null, jobConf.get(FUSION_BATCHID, UNKNOWN)));
      }
    }
  } // end FusionRecordWriter class

  public static class DocBuffer {
    public final List<Object> buffer;
    public final long bufferTimeoutMs;
    public final int maxBufferSize;

    private long bufferTimeoutAtNanos = -1L;

    public DocBuffer(int maxBufferSize, long bufferTimeoutMs) {
      this.maxBufferSize = maxBufferSize;
      this.bufferTimeoutMs = bufferTimeoutMs;
      this.buffer = new ArrayList<>(maxBufferSize);
    }

    public void add(Object doc) {
      buffer.add(doc);

      // start the timer when the first doc arrives in this batch
      if (bufferTimeoutAtNanos == -1L) {
        bufferTimeoutAtNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(bufferTimeoutMs, TimeUnit.MILLISECONDS);
      }
    }

    public void reset() {
      bufferTimeoutAtNanos = -1L;
      buffer.clear();
    }

    public boolean shouldFlushBuffer() {
      if (buffer.isEmpty()) {
        return false;
      }
      return (buffer.size() >= maxBufferSize) || System.nanoTime() >= bufferTimeoutAtNanos;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("DocBuffer: ").append(buffer.size()).append(", shouldFlush? ").append(shouldFlushBuffer());
      return sb.toString();
    }
  }

  public RecordWriter<Text, LWDocumentWritable> getRecordWriter(FileSystem fileSystem, JobConf jobConf, String name,
                                                                Progressable progressable) throws IOException {
    return new FusionRecordWriter(jobConf, name, progressable);
  }

  public void checkOutputSpecs(FileSystem fileSystem, JobConf jobConf) throws IOException {
    // no-op
  }
}