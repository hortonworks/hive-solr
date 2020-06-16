package com.lucidworks.hadoop.fusion;

/**
 * Fusion constants
 */
public final class Constants {
  private Constants(){} // can't construct
  public static final String FUSION_AUTHENABLED = "fusion.authenabled";
  public static final String FUSION_USER = "fusion.user";
  public static final String FUSION_PASS = "fusion.pass";
  public static final String FUSION_REALM = "fusion.realm";
  public static final String FUSION_INDEX_ENDPOINT = "fusion.endpoints";
  public static final String FUSION_QUERY_ENDPOINT = "fusion.query.endpoints";
  public static final String FUSION_QUERY = "fusion.query";

  // Kerberos
  public static final String FUSION_LOGIN_CONFIG = "java.security.auth.login.config";
  public static final String FUSION_LOGIN_APP_NAME = "fusion.jaas.appname";

  // Fusion fields
  public static final String LW_PREFIX = "_lw_";
  public static final String BATCH_ID_FIELD = LW_PREFIX + "batch_id_s";
  public static final String DATA_SOURCE_FIELD = LW_PREFIX + "data_source_s";
  public static final String DATA_SOURCE_TYPE_FIELD = LW_PREFIX + "data_source_type_s";
  public static final String DATA_SOURCE_PIPELINE_FIELD = LW_PREFIX + "data_source_pipeline_s";
  public static final String DATA_SOURCE_COLLECTION_FIELD = LW_PREFIX + "data_source_collection_s";

  // Fusion properties
  public static final String FUSION_BATCHID = "batchid";
  public static final String FUSION_DATASOURCE = "datasource";
  public static final String FUSION_DATASOURCE_TYPE = "datasource.type";
  public static final String FUSION_DATASOURCE_PIPELINE = "datasource.pipeline";
  public static final String UNKNOWN = "__unknown__";

  // -D Args
  public static final String FUSION_BATCHSIZE = "fusion.batchSize";
  public static final String FUSION_BUFFER_TIMEOUTMS = "fusion.buffer.timeoutms";
  public static final String FUSION_FAIL_ON_ERROR = "fusion.fail.on.error";
}
