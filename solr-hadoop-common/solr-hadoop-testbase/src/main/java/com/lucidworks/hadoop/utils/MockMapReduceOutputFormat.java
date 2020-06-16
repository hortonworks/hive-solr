package com.lucidworks.hadoop.utils;

import com.lucidworks.hadoop.io.LWDocumentWritable;
import java.io.IOException;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

/**
 *
 *
 **/
public class MockMapReduceOutputFormat extends OutputFormat<Text, LWDocumentWritable> {

  public MockRecordWriter writer;

  @Override
  public RecordWriter<Text, LWDocumentWritable> getRecordWriter(
      TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
    writer = new MockRecordWriter();
    return new RecordWriter<Text, LWDocumentWritable>() {
      @Override
      public void write(Text text, LWDocumentWritable doc)
          throws IOException, InterruptedException {
        writer.write(text, doc);
      }

      @Override
      public void close(TaskAttemptContext context) throws IOException, InterruptedException {

      }
    };
  }

  @Override
  public void checkOutputSpecs(JobContext jobContext) throws IOException, InterruptedException {

  }

  @Override
  public OutputCommitter getOutputCommitter(TaskAttemptContext taskAttemptContext)
      throws IOException, InterruptedException {
    return new OutputCommitter() {
      @Override
      public void setupJob(JobContext jobContext) throws IOException {

      }

      @Override
      public void setupTask(TaskAttemptContext taskAttemptContext) throws IOException {

      }

      @Override
      public boolean needsTaskCommit(TaskAttemptContext taskAttemptContext) throws IOException {
        boolean result = false;
        return result;
      }

      @Override
      public void commitTask(TaskAttemptContext taskAttemptContext) throws IOException {

      }

      @Override
      public void abortTask(TaskAttemptContext taskAttemptContext) throws IOException {

      }
    };
  }
}
