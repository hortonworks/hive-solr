package com.lucidworks.hadoop.io;

import java.io.IOException;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 **/
public class LWMapReduceOutputFormat extends OutputFormat<Text, LWDocumentWritable> {
  private transient static Logger log = LoggerFactory.getLogger(LWMapReduceOutputFormat.class);

  @Override
  public RecordWriter<Text, LWDocumentWritable> getRecordWriter(TaskAttemptContext context)
      throws IOException, InterruptedException {
    final LucidWorksWriter writer = new LucidWorksWriter(context);
    writer.open(context.getConfiguration(), context.getJobName());
    return new RecordWriter<Text, LWDocumentWritable>() {
      @Override
      public void write(Text text, LWDocumentWritable doc)
          throws IOException, InterruptedException {
        writer.write(text, doc);
      }

      @Override
      public void close(TaskAttemptContext context) throws IOException, InterruptedException {
        writer.close();
      }
    };
  }

  @Override
  public void checkOutputSpecs(JobContext jobContext) throws IOException, InterruptedException {
    //NA
  }

  @Override
  public OutputCommitter getOutputCommitter(TaskAttemptContext context)
      throws IOException, InterruptedException {
    //TODO: do we need this
    return new OutputCommitter() {
      @Override
      public void setupJob(JobContext jobContext) throws IOException {

      }

      @Override
      public void setupTask(TaskAttemptContext taskAttemptContext) throws IOException {

      }

      @Override
      public boolean needsTaskCommit(TaskAttemptContext taskAttemptContext) throws IOException {
        return true;
      }

      @Override
      public void commitTask(TaskAttemptContext context) throws IOException {
        //get a new writer and force a commit then close it
        LucidWorksWriter writer = new LucidWorksWriter(context);
        writer.open(context.getConfiguration(), context.getJobName());

        try {
          writer.commit();
        } catch (SolrServerException e) {
          final IOException ioe = new IOException();
          ioe.initCause(e);
          throw ioe;
        }
      }

      @Override
      public void abortTask(TaskAttemptContext taskAttemptContext) throws IOException {

      }
    };
  }
}
