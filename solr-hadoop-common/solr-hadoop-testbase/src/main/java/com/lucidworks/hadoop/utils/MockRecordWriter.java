package com.lucidworks.hadoop.utils;

import com.lucidworks.hadoop.io.LWDocumentWritable;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.io.Text;

/**
 *
 *
 **/
public class MockRecordWriter {
  public Map<String, LWDocumentWritable> map = new HashMap<String, LWDocumentWritable>();

  public void write(Text text, LWDocumentWritable doc) {
    map.put(text.toString(), doc);
  }
}
