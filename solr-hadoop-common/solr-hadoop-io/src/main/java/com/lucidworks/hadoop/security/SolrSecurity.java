package com.lucidworks.hadoop.security;

import com.sun.security.auth.login.ConfigFile;
import org.apache.hadoop.conf.Configuration;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.Krb5HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.AppConfigurationEntry;
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
      setupJassFile(job, jaasFile);
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

  private static void setupJassFile(Configuration job, String jaasFile) {
    log.info("Using kerberized Solr. "+ jaasFile);
    final javax.security.auth.login.Configuration hiveSolrJassConfiguration = new HiveSolrJaasConfiguration(jaasFile);
    javax.security.auth.login.Configuration.setConfiguration(hiveSolrJassConfiguration);
    Krb5HttpClientBuilder.regenerateJaasConfiguration();
    final String appname = job.get(LWW_JAAS_APPNAME, "Client");
    System.setProperty("solr.kerberos.jaas.appname", appname);

    final Krb5HttpClientBuilder builder = new Krb5HttpClientBuilder();
    HttpClientUtil.setHttpClientBuilder(builder.getHttpClientBuilder(Optional.empty()));
  }

  static class HiveSolrJaasConfiguration extends javax.security.auth.login.Configuration {

    private javax.security.auth.login.Configuration jaasConfig;
    private javax.security.auth.login.Configuration baseConfig;

    public HiveSolrJaasConfiguration(String jaasFile) {
      try {
        baseConfig = javax.security.auth.login.Configuration.getConfiguration();
      } catch (SecurityException e) {
        baseConfig = null;
      }
      System.setProperty("java.security.auth.login.config", jaasFile);
      // this creates a jaas config based on the java.security.auth.login.config system property
      jaasConfig = new ConfigFile();
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
      AppConfigurationEntry[] jaasConfigEntries = jaasConfig.getAppConfigurationEntry(name);
      if (jaasConfigEntries != null) {
        return jaasConfigEntries;
      } else if (baseConfig != null) {
        return baseConfig.getAppConfigurationEntry(name);
      } else {
        return null;
      }
    }
  }

}
