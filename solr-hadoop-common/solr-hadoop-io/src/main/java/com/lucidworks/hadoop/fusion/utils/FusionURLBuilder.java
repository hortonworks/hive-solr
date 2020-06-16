package com.lucidworks.hadoop.fusion.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class FusionURLBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(FusionURLBuilder.class);

  private static final char PATH_SEPARATOR = '/';
  private static final String UTF8 = "UTF-8";
  private static final String WT_JSON = "&wt=json";
  private static final String HANDLER = "select"; // Support only select handler
  private static final String QUERY = "?q=";
  private static final String START = "&start=";
  private static final String ROWS = "&rows=";

  private URI baseUrl;
  private int start = 0; // starts at 0 by default
  private int rows = 100; // default 100 rows
  private String query = "*:*"; // default all fields

  public FusionURLBuilder(URI base) throws MalformedURLException {
    this.setBase(base);
  }

  public void setBase(URI base) throws MalformedURLException {
    StringBuilder newUrl = new StringBuilder();
    if (base.isOpaque()) {
      throw new MalformedURLException("Unsupported opaque URI: " + base.toString());
    }
    if (base.getScheme() != null) {
      // we assume you have scheme and authority
      newUrl.append(base.getScheme());
      newUrl.append("://");
      newUrl.append(base.getAuthority());
    }
    newUrl.append(base.getPath());
    try {
      this.baseUrl = new URI(newUrl.toString());
    } catch (URISyntaxException e) {
      throw new MalformedURLException("Malformed URL: " + newUrl.toString());
    }
  }

  public FusionURLBuilder addQuery(String query) {
    if (query == null) {
      return this;
    }
    this.query = query;
    return this;
  }

  public FusionURLBuilder addStart(int start) {
    this.start = start;
    return this;
  }

  public FusionURLBuilder addRows(int rows) {
    this.rows = rows;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder uri = new StringBuilder(baseUrl.toString());

    if (uri.length() != 0) {
      if (uri.charAt(uri.length() - 1) != PATH_SEPARATOR) {
        uri.append(PATH_SEPARATOR);
      }
    }
    // add handler
    uri.append(HANDLER);

    // add  query
    uri.append(QUERY);
    uri.append(query);

    // adds start at
    uri.append(START);
    uri.append(start);

    // adds rows
    uri.append(ROWS);
    uri.append(rows);

    // add wt json
    uri.append(WT_JSON);
    return uri.toString();
  }

  public URI toURI() throws URISyntaxException {
    return new URI(toString());
  }
}

