package com.lucidworks.hadoop.utils;

import com.lucidworks.hadoop.io.LWDocumentWritable;
import java.io.IOException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Progressable;

/**
 *
 *
 **/
public class MockMapRedOutputFormat implements OutputFormat<Text, LWDocumentWritable> {

  private MockRecordWriter writer;

  @Override
  public RecordWriter<Text, LWDocumentWritable> getRecordWriter(FileSystem fileSystem,
      JobConf entries, String s, Progressable progressable) throws IOException {
    writer = new MockRecordWriter();
    return new RecordWriter<Text, LWDocumentWritable>() {

      public void close(Reporter reporter) throws IOException {

      }

      public void write(Text key, LWDocumentWritable doc) throws IOException {
        writer.write(key, doc);
      }
    };
  }

  @Override
  public void checkOutputSpecs(FileSystem fileSystem, JobConf entries) throws IOException {

  }
}
