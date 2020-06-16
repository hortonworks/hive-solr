package com.lucidworks.hadoop.io.impl;

import com.lucidworks.hadoop.io.LWDocument;

import org.apache.hadoop.io.Text;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import com.fasterxml.jackson.databind.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

public class LWSolrDocument implements LWDocument {

  private static final Logger log = LoggerFactory.getLogger(LWSolrDocument.class);
  private static final String RAW_CONTENT = "_raw_content_";
  private static final String ID = "id";
  private static final String DUMMY_HOLDER = "--NA--";

  private SolrInputDocument document;
  private static final ObjectMapper objectMapper = createMapper();

  public LWSolrDocument() {
    document = new SolrInputDocument();
  }

  @Override
  public void setId(String id) {
    document.setField(ID, id);
  }

  @Override
  public String getId() {
    SolrInputField id = document.get(ID);
    return id.getFirstValue().toString();
  }

  @Override
  public void setMetadata(Map<String, String> metadata) {
    if (metadata != null) {
      for (Map.Entry<String, String> entry : metadata.entrySet()) {
        document.addField(entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public Map<String, String> getMetadata() {
    // XXX:
    return Collections.emptyMap();
  }

  @Override
  public void write(DataOutput dataOutput) throws IOException {
    writeString(getId(), dataOutput);
    writeDocFields(document, dataOutput);
  }

  private void writeDocFields(SolrInputDocument doc, DataOutput dataOutput) throws IOException {
    if (doc != null && !doc.isEmpty()) {
      // doc - {id_field}
      dataOutput.writeInt(doc.size() - 1);
      for (Map.Entry<String, SolrInputField> entry : doc.entrySet()) {
        if (!ID.equals(entry.getKey())) {
          writeString(entry.getKey(), dataOutput);
          SolrInputField solrField = entry.getValue();
          writeString(solrField.getName(), dataOutput);
          dataOutput.writeInt(solrField.getValueCount());
          for (Iterator<Object> iterator = solrField.iterator(); iterator.hasNext(); ) {
            Object theVal = iterator.next();
            if (theVal != null) {
              String valAsString = objectMapper.writeValueAsString(theVal);
              writeString(valAsString, dataOutput);
            } else {
              // we have no object, is this an error?
              writeString(DUMMY_HOLDER, dataOutput);
            }
          }
        }
      }
    } else {
      dataOutput.writeInt(0);
    }
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    String id = readString(in);
    document = new SolrInputDocument();
    setId(id);
    readDocFields(in);
  }

  private void readDocFields(DataInput in) throws IOException {
    int len = in.readInt();
    for (int i = 0; i < len; i++) {
      String key = readString(in);// issue if this is empty?
      if (key != null) {
        String solrFieldName = readString(in);
        if (solrFieldName == null) {
          solrFieldName = key;
        }
        SolrInputField solrField = new SolrInputField(solrFieldName);
        int solrFieldlen = in.readInt();
        for (int j = 0; j < solrFieldlen; j++) {
          String theValStr = readString(in);// stored as JSON
          if (theValStr != null && !theValStr.equals(DUMMY_HOLDER)) {
            Object theVal = objectMapper.readValue(theValStr, Object.class);
            if (theVal != null) {
              solrField.addValue(theVal);
            } else {
              log.warn("Couldn't convert JSON string: {}", theValStr);
            }
          }
        }
        document.put(solrFieldName, solrField);
      }
    }
  }

  private void writeString(String str, DataOutput dataOutput) throws IOException {
    if (str != null) {
      Text.writeString(dataOutput, str);
    } else {
      Text.writeString(dataOutput, "");
    }
  }

  private String readString(DataInput in) throws IOException {
    return Text.readString(in);
  }

  @Override
  public void setContent(byte[] data) {
    document.addField(RAW_CONTENT, data);
  }

  @Override
  public LWDocument addField(String name, Object value) {
    if (name.equals(ID)) {
      document.setField(ID, value);
    } else {
      document.addField(name, value);
    }
    return this;
  }

  @Override
  public LWDocument removeField(String name) {
    document.removeField(name);
    return this;
  }

  @Override
  public LWDocument addMetadata(String name, String value) {
    document.addField(name, value);
    return this;
  }

  @Override
  public LWDocument checkId(String idField, String missingId) {
    SolrInputField id = document.get(idField);
    if (id == null) {
      setId(missingId);
    } else {
      setId((String) id.getValue());
    }
    return this;
  }

  @Override
  public Object getFirstFieldValue(String name) {
    SolrInputField field = document.get(name);
    if (field == null) {
      return null;
    }
    return field.getFirstValue();
  }

  @Override
  public SolrInputDocument convertToSolr() {
    return document;
  }

  private static ObjectMapper createMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    mapper.setDateFormat(df);
    return mapper;
  }

  @Override
  public String toString() {
    return document.toString();
  }

}
