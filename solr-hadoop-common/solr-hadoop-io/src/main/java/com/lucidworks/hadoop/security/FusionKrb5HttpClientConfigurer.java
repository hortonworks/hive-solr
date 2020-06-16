package com.lucidworks.hadoop.security;

import com.google.common.collect.Sets;
import com.lucidworks.hadoop.fusion.Constants;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.client.solrj.impl.HttpClientBuilderFactory;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.SolrHttpClientBuilder;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.lucidworks.hadoop.fusion.Constants.FUSION_LOGIN_CONFIG;
import static com.lucidworks.hadoop.fusion.Constants.FUSION_LOGIN_APP_NAME;


public class FusionKrb5HttpClientConfigurer implements HttpClientBuilderFactory {

  private static final Logger logger = LoggerFactory.getLogger(FusionKrb5HttpClientConfigurer.class);
  private String fusionPrincipal = null;

  private Configuration jaasConfig = null;

  public FusionKrb5HttpClientConfigurer(String fusionPrincipal) {
    this.fusionPrincipal = fusionPrincipal;
    jaasConfig = new FusionKrb5HttpClientConfigurer.FusionJaasConfiguration(fusionPrincipal);
  }

  @Override
  public SolrHttpClientBuilder getHttpClientBuilder(Optional<SolrHttpClientBuilder> builder) {
    return builder.isPresent() ? getBuilder(builder.get()) : getBuilder();
  }

  private SolrHttpClientBuilder getBuilder(SolrHttpClientBuilder builder) {
    if (System.getProperty(FUSION_LOGIN_CONFIG) == null) return builder;

    final String configValue = System.getProperty(FUSION_LOGIN_CONFIG);
    logger.debug("Setting up kerberos auth with config: " + configValue);
    System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
    if (fusionPrincipal != null) {
      Subject subject = new Subject(false, Sets.newHashSet(new KerberosPrincipal(fusionPrincipal)),
              Collections.emptySet(), Collections.emptySet());
      try {
        final LoginContext loginContext = new LoginContext("", subject, null, jaasConfig);
        loginContext.login();
        logger.debug("Successful Fusion Login with principal: " + fusionPrincipal);
      } catch (LoginException e) {
        String errorMessage = "Unsuccessful Fusion Login with principal: " + fusionPrincipal;
        logger.error(errorMessage, e);
        throw new RuntimeException(errorMessage, e);
      }
    }

    Configuration.setConfiguration(jaasConfig);
    builder.setAuthSchemeRegistryProvider(() -> {
      Lookup<AuthSchemeProvider> authProviders = RegistryBuilder.<AuthSchemeProvider>create()
              .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true, false))
              .build();
      return authProviders;
    });

    Credentials useJaasCreds = new Credentials() {
      public String getPassword() {
        return null;
      }

      public Principal getUserPrincipal() {
        return null;
      }
    };
    builder.setDefaultCredentialsProvider(() -> {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY, useJaasCreds);
      return credentialsProvider;
    });

    HttpClientUtil.addRequestInterceptor(this.bufferedEntityInterceptor);

    return builder;
  }

  public void close() {
    HttpClientUtil.removeRequestInterceptor(this.bufferedEntityInterceptor);
  }

  private SolrHttpClientBuilder getBuilder() {
    return getBuilder(HttpClientUtil.getHttpClientBuilder());
  }

  public static synchronized CloseableHttpClient createClient(String fusionPrincipal) {
    if (logger.isDebugEnabled()) {
      System.setProperty("sun.security.krb5.debug", "true");
    }
    if (fusionPrincipal == null) {
      logger.error("fusion.user [principal] must be set in order to use kerberos");
    }

    final HttpClientBuilderFactory builder = new FusionKrb5HttpClientConfigurer(fusionPrincipal);
    HttpClientUtil.setHttpClientBuilder(builder.getHttpClientBuilder(Optional.empty()));
    ModifiableSolrParams clientParams = new ModifiableSolrParams();
    clientParams.set(HttpClientUtil.PROP_MAX_CONNECTIONS_PER_HOST, 100);
    clientParams.set(HttpClientUtil.PROP_MAX_CONNECTIONS, 500);

    return HttpClientUtil.createClient(clientParams);
  }

  private HttpRequestInterceptor bufferedEntityInterceptor = new HttpRequestInterceptor() {
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
      if (request instanceof HttpEntityEnclosingRequest) {
        HttpEntityEnclosingRequest enclosingRequest = (HttpEntityEnclosingRequest) request;
        HttpEntity requestEntity = enclosingRequest.getEntity();
        enclosingRequest.setEntity(new BufferedHttpEntity(requestEntity));
      }
    }
  };

  private static class FusionJaasConfiguration extends Configuration {
    private Configuration baseConfig;
    private String fusionPrincipal;
    private AppConfigurationEntry[] globalAppConfigurationEntry;

    public FusionJaasConfiguration(String fusionPrincipal) {
      this.fusionPrincipal = fusionPrincipal;
      try {
        this.baseConfig = Configuration.getConfiguration();
      } catch (SecurityException var2) {
        this.baseConfig = null;
      }
      if (this.baseConfig != null) {
        String clientAppName = System.getProperty(FUSION_LOGIN_APP_NAME, "FusionClient"); // FusionClient by default
        this.globalAppConfigurationEntry = this.baseConfig.getAppConfigurationEntry(clientAppName);
      }
    }

    private AppConfigurationEntry overwriteOptions(AppConfigurationEntry app) {
      Map<String, ?> options = app.getOptions();
      AppConfigurationEntry.LoginModuleControlFlag flag = app.getControlFlag();
      String loginModule = app.getLoginModuleName();

      // Overwriting options
      Map<String, Object> overwriteOptions = new HashMap<String, Object>(options);
      overwriteOptions.put("principal", fusionPrincipal);
      overwriteOptions.put("doNotPrompt", "true");

      FusionKrb5HttpClientConfigurer.logger.debug("Overwriting kerberos principal with [: " + fusionPrincipal + "]");

      return new AppConfigurationEntry(loginModule, flag, overwriteOptions);
    }

    public AppConfigurationEntry[] getAppConfigurationEntry(String appName) {
      if (this.baseConfig == null) {
        return null;
      } else {
        FusionKrb5HttpClientConfigurer.logger.debug("Login prop: " + System.getProperty(FUSION_LOGIN_CONFIG));
        if (fusionPrincipal == null) {
          FusionKrb5HttpClientConfigurer.logger.debug("fusionPrincipal is null using principal from JAAS file.");
          return globalAppConfigurationEntry;
        }
        if (globalAppConfigurationEntry == null) {
          return null;
        }

        // Must be only one Entry, if more use the first one.
        return new AppConfigurationEntry[]{overwriteOptions(globalAppConfigurationEntry[0])};
      }
    }
  }

}
