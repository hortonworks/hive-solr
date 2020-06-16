package com.lucidworks.hadoop.security;

import org.apache.hadoop.conf.Configuration;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.Krb5HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class SolrSecurity {
  public static final String LWW_JAAS_FILE = "lww.jaas.file";
  public static final String LWW_JAAS_APPNAME = "lww.jaas.appname";
  public static final String LWW_KEYSTORE = "lww.keystore";
  public static final String LWW_KEYSTOREPASSWORD = "lww.keystore.password";
  public static final String LWW_TRUSTSTORE = "lww.truststore";
  public static final String LWW_TRUSTSTOREPASSWORD = "lww.truststore.password";

  private static Logger log = LoggerFactory.getLogger(SolrSecurity.class);

  // Sets Security features if needed
  public static void setSecurityConfig(Configuration job) {
    final String jaasFile = job.get(LWW_JAAS_FILE);
    if (jaasFile != null) {
      log.debug("Using kerberized Solr.");
      System.setProperty("java.security.auth.login.config", jaasFile);
      final String appname = job.get(LWW_JAAS_APPNAME, "Client");
      System.setProperty("solr.kerberos.jaas.appname", appname);

      final Krb5HttpClientBuilder builder = new Krb5HttpClientBuilder();
      HttpClientUtil.setHttpClientBuilder(builder.getHttpClientBuilder(Optional.empty()));
    }
    final String keystore = job.get(LWW_KEYSTORE);
    if (keystore != null) {
      log.debug("Using keystore: " + keystore);
      System.setProperty("javax.net.ssl.keyStore", keystore);
    }
    final String keystorePassword = job.get(LWW_KEYSTOREPASSWORD);
    if (keystorePassword != null) {
      System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);
    }
    final String truststore = job.get(LWW_TRUSTSTORE);
    if (truststore != null) {
      log.debug("Using truststore: " + truststore);
      System.setProperty("javax.net.ssl.trustStore", truststore);
    }
    final String truststorePassword = job.get(LWW_TRUSTSTOREPASSWORD);
    if (truststorePassword != null) {
      System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
    }
  }
}
