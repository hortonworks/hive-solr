package com.lucidworks.hadoop.fusion.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lucidworks.hadoop.fusion.dto.FusionDocument;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;

public class FusionResponseHandler implements ResponseHandler<FusionDocument> {

  private static final Logger LOG = LoggerFactory.getLogger(FusionResponseHandler.class);

  private int statusCode;
  private Gson json;

  private static final Type LIST_FUSION_DOCUMENT_TYPE = new TypeToken<FusionDocument>() {
  }.getType();

  public FusionResponseHandler(Gson json) {
    this.json = json;
  }

  @Override
  public FusionDocument handleResponse(HttpResponse httpResponse) throws IOException {
    StatusLine statusLine = httpResponse.getStatusLine();
    statusCode = statusLine.getStatusCode();
    if (statusCode >= 300) {
      throw new HttpResponseException(
          statusLine.getStatusCode(),
          statusLine.getReasonPhrase());
    }

    HttpEntity entity = httpResponse.getEntity();
    if (entity == null) {
      LOG.warn("Entity Response is null");
      return new FusionDocument();
    }
    return json.fromJson(EntityUtils.toString(entity), LIST_FUSION_DOCUMENT_TYPE);
  }

  public int getStatusCode() {
    return statusCode;
  }
}