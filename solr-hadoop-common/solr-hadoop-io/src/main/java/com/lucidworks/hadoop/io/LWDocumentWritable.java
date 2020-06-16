package com.lucidworks.hadoop.io;

import com.google.inject.Inject;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.io.Writable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 **/
public class LWDocumentWritable implements Writable {
  private transient static Logger log = LoggerFactory.getLogger(LWDocumentWritable.class);

  private LWDocument document;

  public LWDocumentWritable() {
    document = LWDocumentProvider.createDocument();
  }

  @Inject
  public LWDocumentWritable(LWDocument document) {
    this.document = document;
  }

  public LWDocument getLWDocument() {
    return document;
  }

  public void setLWDocument(LWDocument document) {
    this.document = document;
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
  public boolean equals(Object o) {
    LWDocumentWritable otherDocument = (LWDocumentWritable) o;
    return this.getLWDocument().equals(otherDocument.getLWDocument());
  }

  @Override
  public int hashCode() {
    return document.getId().hashCode();
  }

  @Override
  public String toString() {
    return "LWDocumentWritable{" + "document=" + document + "}";
  }
}
