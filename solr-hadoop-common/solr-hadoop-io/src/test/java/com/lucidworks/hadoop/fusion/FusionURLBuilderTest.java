package com.lucidworks.hadoop.fusion;

import com.lucidworks.hadoop.fusion.utils.FusionURLBuilder;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.net.URI;

public class FusionURLBuilderTest {

  @Test
  public void testQueryUrl() throws Exception {
    String baseQueryURL = "http://192.168.0.1:8764/api/apollo/query-pipelines/pipeline-id/collections/collection-id";
    String expected = baseQueryURL + "/select?q=*:*&start=0&rows=100&wt=json";
    String url = new FusionURLBuilder(new URI(baseQueryURL)).toString();
    assertEquals(url, expected);
  }

  @Test
  public void testQueryUrlwithParam() throws Exception {
    String baseQueryURL = "http://some.fake.host:aport/api/apollo/query-pipelines/pipeline-id/collections/collection-id";
    String expected = baseQueryURL + "/select?q=key:value&start=61&rows=16&wt=json";
    String url = new FusionURLBuilder(new URI(baseQueryURL))
        .addQuery("key:value") // We are not validating the query
        .addRows(16)
        .addStart(61)
        .toString();
    assertEquals(url, expected);
  }
}
