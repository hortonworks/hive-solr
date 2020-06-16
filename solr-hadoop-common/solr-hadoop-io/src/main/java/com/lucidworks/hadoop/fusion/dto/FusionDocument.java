package com.lucidworks.hadoop.fusion.dto;

import com.google.gson.annotations.Expose;
import com.lucidworks.hadoop.io.LWDocument;
import com.lucidworks.hadoop.io.LWDocumentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FusionDocument {

  private static final Logger LOG = LoggerFactory.getLogger(FusionDocument.class);
  private static final String DOC_ID = "id";

  // XXX: should use the /responseHeader and /facet_counts?
  @Expose
  private Response response;

  public Response getResponse() {
    return response;
  }

  public void setResponse(Response response) {
    this.response = response;
  }

  public int numFound() {
    return response.getNumFound();
  }

  public int start() {
    return response.getStart();
  }

  public List<LWDocument> docs() {
    // Convert Json to LWDocument
    List<LWDocument> docs = new ArrayList<>();
    for (Object doc : response.getDocs()) {
      LWDocument lwDocument = LWDocumentProvider.createDocument();
      addKeyValue(lwDocument, doc, "");
      docs.add(lwDocument);
    }
    return docs;
  }

  private class Response {
    @Expose
    private List<Object> docs;
    @Expose
    private int numFound;
    @Expose
    private int start;

    public List<Object> getDocs() {
      return docs;
    }

    public void setDocs(List<Object> docs) {
      this.docs = docs;
    }

    public int getNumFound() {
      return numFound;
    }

    public void setNumFound(int numFound) {
      this.numFound = numFound;
    }

    public int getStart() {
      return start;
    }

    public void setStart(int start) {
      this.start = start;
    }
  }

  private void addKeyValue(LWDocument lwDocument, Object object, String parentKey) {
    if (object instanceof Map) {
      Map map = (Map) object;
      @SuppressWarnings("unchecked")
      Set<String> keys = (Set<String>) map.keySet();
      for (String key : keys) {
        Object value = map.get(key);
        // Nested docs?
        key = parentKey.isEmpty() ? key : parentKey + "_" + key;
        if (DOC_ID.equals(key)) {
          // ID should be always string
          lwDocument.setId(value.toString());
        } else if (value instanceof Map) {
          // Adding  Nested docs
          addKeyValue(lwDocument, value, key);
        } else {
          LOG.debug("LWDoc key[" + key + "]:value[" + value + "]");
          // XXX cast value?
          lwDocument.addField(key, value);
        }
      }
      map.clear();
    }
  }
}


