package com.lucidworks.hadoop.io;

import com.lucidworks.hadoop.io.impl.LWMockDocument;
import com.lucidworks.hadoop.utils.SolrCloudClusterSupport;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.util.Progressable;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LWMapRedInputFormatTest extends SolrCloudClusterSupport {

  @Test
  public void testInputFormat() throws IOException {
    JobConf conf = new JobConf();
    conf.set(LWMapRedInputFormat.SOLR_ZKHOST, getBaseUrl());
    conf.set(LWMapRedInputFormat.SOLR_COLLECTION, DEFAULT_COLLECTION);
    conf.set("lww.commit.on.close", "true");

    // lots of docs
    int docsCount = 10005;

    LucidWorksWriter writer = new LucidWorksWriter(new MockProgressable());
    writer.open(conf, "test");
    for (int i = 0; i < docsCount; i++) {
      writer.write(new Text("text" + i),
          createLWDocumentWritable("id" + i, "field1", "fieldValue1", "field2", "fieldValue2"));
    }
    writer.close();

    LWMapRedInputFormat inputFormat = new LWMapRedInputFormat();
    InputSplit[] splits = inputFormat.getSplits(conf, 0);
    RecordReader<IntWritable, LWDocumentWritable> reader = inputFormat
        .getRecordReader(splits[0], conf, null);

    IntWritable key = reader.createKey();
    LWDocumentWritable value = reader.createValue();
    int count = 0;
    while (reader.next(key, value)) {
      count++;
    }
    reader.close();

    assertEquals(docsCount, count);
  }

  public static class MockProgressable implements Progressable {
    @Override
    public void progress() {
    }

  }

  private static LWDocumentWritable createLWDocumentWritable(String id, String... keyValues) {
    Map<String, String> fields = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      fields.put(keyValues[i], keyValues[i + 1]);
    }
    LWMockDocument doc = new LWMockDocument(id, fields);
    return new LWDocumentWritable(doc);
  }
}
