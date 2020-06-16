package com.lucidworks.hadoop.io.impl;

import com.lucidworks.hadoop.io.LWDocument;
import org.apache.solr.common.SolrInputDocument;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class LWMockDocument implements LWDocument {

  private LWSolrDocument document;

  public LWMockDocument() {
    document = new LWSolrDocument();
  }

  public LWMockDocument(String id, Map<String, String> metadata) {
    document = new LWSolrDocument();
    if (metadata != null) {
      document.setMetadata(metadata);
    }
    document.setId(id);
  }

  @Override
  public String getId() {
    return document.getId();
  }

  @Override
  public void setMetadata(Map<String, String> metadata) {
    document.setMetadata(metadata);
  }

  @Override
  public void setId(String id) {
    document.setId(id);
  }

  @Override
  public Map<String, String> getMetadata() {
    return document.getMetadata();
  }

  @Override
  public void write(DataOutput dataOutput) throws IOException {
    document.write(dataOutput);
  }

  @Override
  public void readFields(DataInput dataInput) throws IOException {
    document.readFields(dataInput);
  }

  @Override
  public void setContent(byte[] data) {
    document.setContent(data);
  }

  @Override
  public LWDocument addField(String name, Object value) {
    document.addField(name, value);
    return this;
  }

  @Override
  public LWDocument removeField(String name) {
    return document.removeField(name);
  }

  @Override
  public LWDocument addMetadata(String name, String value) {
    document.addMetadata(name, value);
    return this;
  }

  @Override
  public LWDocument checkId(String idField, String missingId) {
    document.checkId(idField, missingId);
    return this;
  }

  @Override
  public Object getFirstFieldValue(String name) {
    return document.getFirstFieldValue(name);
  }

  @Override
  public String toString() {
    return document.toString();
  }

  @Override
  public SolrInputDocument convertToSolr() {
    return document.convertToSolr();
  }

  @Override
  public boolean equals(Object other) {
    LWMockDocument otherDoc = (LWMockDocument) other;
    return assertSolrInputDocumentEquals(document.convertToSolr(), otherDoc.document.convertToSolr());
  }

  // From SolrTestCaseJ4
  public static boolean assertSolrInputDocumentEquals(Object expected, Object actual) {

    if (!(expected instanceof SolrInputDocument) || !(actual instanceof SolrInputDocument)) {
      return false;
    }

    if (expected == actual) {
      return true;
    }

    SolrInputDocument sdoc1 = (SolrInputDocument) expected;
    SolrInputDocument sdoc2 = (SolrInputDocument) actual;

    if (sdoc1.getFieldNames().size() != sdoc2.getFieldNames().size()) {
      return false;
    }

    Iterator<String> iter1 = sdoc1.getFieldNames().iterator();
    Iterator<String> iter2 = sdoc2.getFieldNames().iterator();

    if (iter1.hasNext()) {
      String key1 = iter1.next();
      String key2 = iter2.next();

      Object val1 = sdoc1.getFieldValues(key1);
      Object val2 = sdoc2.getFieldValues(key2);

      if (!key1.equals(key2) || !val1.equals(val2)) {
        return false;
      }
    }
    if (sdoc1.getChildDocuments() == null && sdoc2.getChildDocuments() == null) {
      return true;
    }
    if (sdoc1.getChildDocuments() == null || sdoc2.getChildDocuments() == null) {
      return false;
    } else if (sdoc1.getChildDocuments().size() != sdoc2.getChildDocuments().size()) {
      return false;
    } else {
      Iterator<SolrInputDocument> childDocsIter1 = sdoc1.getChildDocuments().iterator();
      Iterator<SolrInputDocument> childDocsIter2 = sdoc2.getChildDocuments().iterator();
      while (childDocsIter1.hasNext()) {
        if (!assertSolrInputDocumentEquals(childDocsIter1.next(), childDocsIter2.next())) {
          return false;
        }
      }
      return true;
    }
  }

}
