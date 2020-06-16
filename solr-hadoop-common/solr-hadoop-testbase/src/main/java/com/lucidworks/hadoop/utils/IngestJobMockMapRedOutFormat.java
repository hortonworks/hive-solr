package com.lucidworks.hadoop.utils;

import com.lucidworks.hadoop.io.LWDocumentWritable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Progressable;

/**
 * Because we are passing in string args, we need to have a static store for the writers
 */
public class IngestJobMockMapRedOutFormat implements OutputFormat<Text, LWDocumentWritable> {
  public static Map<String, MockRecordWriter> writers = new HashMap<String, MockRecordWriter>();

  @Override
  public RecordWriter<Text, LWDocumentWritable> getRecordWriter(FileSystem fileSystem,
      JobConf jobConf, String s, Progressable progressable) throws IOException {
    if (writers.get(jobConf.getJobName()) == null) {
      MockRecordWriter writer = new MockRecordWriter();
      writers.put(jobConf.getJobName(), writer);
    }
    final MockRecordWriter writer = writers.get(jobConf.getJobName());
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
