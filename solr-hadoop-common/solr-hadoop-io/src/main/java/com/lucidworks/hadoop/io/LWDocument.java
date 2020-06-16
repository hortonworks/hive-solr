package com.lucidworks.hadoop.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import org.apache.hadoop.mapred.JobConf;
import org.apache.solr.common.SolrInputDocument;

public interface LWDocument {

  void setId(String id);

  String getId();

  void setMetadata(Map<String, String> metadata);

  Map<String, String> getMetadata();

  void write(DataOutput dataOutput) throws IOException;

  void readFields(DataInput dataInput) throws IOException;

  void setContent(byte[] data);

  LWDocument addField(String name, Object value);

  LWDocument removeField(String name);

  LWDocument addMetadata(String name, String value);

  /* Check if the document has multiple id fields, use one of them or the missingId argument., */
  LWDocument checkId(String idField, String missingId);

  Object getFirstFieldValue(String name);

  SolrInputDocument convertToSolr();
}
