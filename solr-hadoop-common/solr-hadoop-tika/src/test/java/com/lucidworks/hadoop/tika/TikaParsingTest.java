package com.lucidworks.hadoop.tika;

import org.apache.hadoop.mapred.JobConf;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.junit.Test;

import com.lucidworks.hadoop.io.LWDocument;
import com.lucidworks.hadoop.io.LWDocumentProvider;
import com.lucidworks.hadoop.io.impl.LWSolrDocument;

import junit.framework.Assert;

import java.util.*;

public class TikaParsingTest {

  @Test
  public void test() throws Exception {
    LWSolrDocument doc = new LWSolrDocument();
    // load tika parsing
    String expected = "this is a String";
    doc.setId("doc-1");
    doc.setContent(expected.getBytes());
    LWDocument[] docs = new TikaParsing().tikaParsing(doc);
    // one doc
    String body = (String) docs[0].getFirstFieldValue("body");
    Assert.assertTrue(expected.equals(body.trim()));
  }

  @Test
  public void testWithLoader() throws Exception {
    JobConf conf = new JobConf();
    conf.set(LWDocumentProvider.TIKA_PROCESS, "true");
    LWSolrDocument doc = new LWSolrDocument();
    // load tika parsing
    doc.setId("doc-1");
    String expected = "this is a String";
    doc.setContent(expected.getBytes());
    LWDocument[] docs = LWDocumentProvider.processIfNeeded(doc, conf);

    // one doc
    String body = (String) docs[0].getFirstFieldValue("body");
    Assert.assertTrue(expected.equals(body.trim()));
  }

  @Test
  public void testParseArchive() throws Exception {
    byte[] zipBody = IOUtils.toByteArray(TikaInputStream.get(getClass().getResource("test-documents.zip")));
    LWSolrDocument doc = new LWSolrDocument();
    doc.setId("test-documents.zip");
    doc.setContent(zipBody);

    LWDocument[] docs = new TikaParsing().tikaParsing(doc);
    final Comparator<LWDocument> docIdComparator = new Comparator<LWDocument>() {
      public int compare(LWDocument first, LWDocument second) {
        return second.getId().compareTo(first.getId());
      }
    };
    Arrays.sort(docs, docIdComparator);

    // validations
    Set<String> expectedDocs = new HashSet<>();
    expectedDocs.add("test-documents.zip#testEXCEL.xls");
    expectedDocs.add("test-documents.zip#testHTML.html");
    expectedDocs.add("test-documents.zip#testOpenOffice2.odt");
    expectedDocs.add("test-documents.zip#testPDF.pdf");
    expectedDocs.add("test-documents.zip#testPPT.ppt");
    expectedDocs.add("test-documents.zip#testRTF.rtf");
    expectedDocs.add("test-documents.zip#testTXT.txt");
    expectedDocs.add("test-documents.zip#testWORD.doc");
    expectedDocs.add("test-documents.zip#testXML.xml");

    Assert.assertEquals("Invalid parsed documents number", 12, docs.length);

    for (int index = 0; index < 9; index++) {
      Assert.assertTrue("Invalid parsed document", expectedDocs.contains(docs[index].getId()));
    }

    // Ensure one of the documents is for the zip itself
    final boolean zipRepresented = Arrays.stream(docs).anyMatch(
            singleDoc -> singleDoc.getFirstFieldValue("Content-Type").equals("application/zip"));
    Assert.assertTrue("Expected to find a document matching the ZIP itself", zipRepresented);
  }
}