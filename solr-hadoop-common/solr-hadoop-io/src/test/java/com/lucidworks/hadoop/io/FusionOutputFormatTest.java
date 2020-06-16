package com.lucidworks.hadoop.io;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.lucidworks.hadoop.fusion.FusionPipelineClientTest;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.lucidworks.hadoop.fusion.Constants.BATCH_ID_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_COLLECTION_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_PIPELINE_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.DATA_SOURCE_TYPE_FIELD;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_BATCHID;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_DATASOURCE;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_DATASOURCE_PIPELINE;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_DATASOURCE_TYPE;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_INDEX_ENDPOINT;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_PASS;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_REALM;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_USER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FusionOutputFormatTest extends FusionPipelineClientTest {

  @Test
  public void testFusionOutputFormatTest() throws Exception {
    // mock out the Pipeline API
    stubFor(post(urlEqualTo(fusionPipelineUrlWithoutHostAndPort)).willReturn(aResponse().withStatus(200)));

    // mock out the Session API
    stubFor(post(urlEqualTo("/api/session?realmName=" + fusionRealm)).willReturn(aResponse().withStatus(200)));

    Configuration conf = new Configuration();
    conf.set(FUSION_INDEX_ENDPOINT, fusionUrl);
    conf.set(FUSION_USER, fusionUser);
    conf.set(FUSION_PASS, fusionPass);
    conf.set(FUSION_REALM, fusionRealm);

    conf.set(FUSION_DATASOURCE, "fusion_data_source");
    conf.set(FUSION_DATASOURCE_PIPELINE, "fusion_pipeline");
    conf.set(FUSION_DATASOURCE_TYPE, "fusion_type");
    conf.set(FUSION_BATCHID, "fusion_batch");
    conf.set(LucidWorksWriter.SOLR_COLLECTION, "coll");

    FusionOutputFormat outputFormat = new FusionOutputFormat();
    RecordWriter<Text, LWDocumentWritable> writer =
        outputFormat.getRecordWriter(null, new JobConf(conf), "test", Reporter.NULL);
    writer.write(new Text("text"), LucidWorksWriterTest
        .createLWDocumentWritable("id-1", "field-1", "field-value-1", "field-2", "field-value-2", "field-3",
            "field-value-3"));
    writer.close(Reporter.NULL);

    String docsSentToFusion = null;
    for (LoggedRequest req : loggedRequests) {
      String body = req.getBodyAsString().trim();
      if (body.startsWith("[") && body.endsWith("]")) {
        docsSentToFusion = body;
        break;
      }
    }

    assertNotNull("Expected 1 doc to have been written by FusionOutputFormat", docsSentToFusion);

    ObjectMapper objectMapper = new ObjectMapper();
    List docs = objectMapper.readValue(docsSentToFusion, List.class);
    assertTrue(docs.size() == 1);
    Map<String, Object> doc = (Map<String, Object>) docs.get(0);
    assertNotNull(doc);
    assertEquals(doc.get("id"), "id-1");
    List<Map<String, Object>> fields = (List<Map<String, Object>>) doc.get("fields");
    assertTrue(!fields.isEmpty());

    String[] expectedFields = new String[]{"field-1", "field-2", "field-3", "_hadoop_tdt", DATA_SOURCE_FIELD,
        DATA_SOURCE_COLLECTION_FIELD, DATA_SOURCE_PIPELINE_FIELD, BATCH_ID_FIELD, DATA_SOURCE_TYPE_FIELD};
    for (String f : expectedFields) {
      boolean foundIt = false;
      for (Map<String, Object> map : fields) {
        if (f.equals(map.get("name"))) {
          foundIt = true;
          break;
        }
      }
      if (!foundIt) {
        fail("Expected field " + f + " not found in doc sent to Fusion!");
      }
    }
  }
}
