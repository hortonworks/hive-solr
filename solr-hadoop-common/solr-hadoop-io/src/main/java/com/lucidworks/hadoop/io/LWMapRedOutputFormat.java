package com.lucidworks.hadoop.io;

import java.io.IOException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Progressable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LWMapRedOutputFormat implements OutputFormat<Text, LWDocumentWritable> {
  private transient static Logger log = LoggerFactory.getLogger(LWMapRedOutputFormat.class);

  @Override
  public void checkOutputSpecs(FileSystem fileSystem, JobConf entries) throws IOException {
    //N/A
  }

  public RecordWriter<Text, LWDocumentWritable> getRecordWriter(FileSystem ignored, JobConf job,
      String name, Progressable progress) throws IOException {

    final LucidWorksWriter writer = new LucidWorksWriter(progress);
    writer.open(job, name);

    return new RecordWriter<Text, LWDocumentWritable>() {

      public void close(Reporter reporter) throws IOException {
        writer.close();
      }

      public void write(Text key, LWDocumentWritable doc) throws IOException {
        writer.write(key, doc);
      }
    };
  }
}
