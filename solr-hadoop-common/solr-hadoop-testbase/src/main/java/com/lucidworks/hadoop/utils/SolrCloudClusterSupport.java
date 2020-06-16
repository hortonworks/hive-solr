package com.lucidworks.hadoop.utils;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakAction;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakLingering;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakZombies;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.JettyConfig;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkStateReader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.noggit.CharArr;
import org.noggit.JSONWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@ThreadLeakAction({ ThreadLeakAction.Action.WARN })
@ThreadLeakLingering(linger = 0)
@ThreadLeakZombies(ThreadLeakZombies.Consequence.CONTINUE)
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class SolrCloudClusterSupport {

  static final Logger log = Logger.getLogger(SolrCloudClusterSupport.class);

  private static MiniSolrCloudCluster cluster;
  private static CloudSolrClient cloudSolrClient;
  private static Path TEMP_DIR;
  public static final String DEFAULT_COLLECTION = "default-collection";
  public static final String DEFAULT_ZK_CONF = "default";


  @BeforeClass
  public static void startCluster() throws Exception {
    TEMP_DIR = Files.createTempDirectory("MiniSolrCloudCluster");

    JettyConfig.Builder jettyConfig = JettyConfig.builder();
    jettyConfig.waitForLoadingCoresToFinish(null);

    cluster = new MiniSolrCloudCluster(1, TEMP_DIR, jettyConfig.build());
    cloudSolrClient = cluster.getSolrClient();
    cloudSolrClient.connect();
    assertTrue(!cloudSolrClient.getZkStateReader().getClusterState().getLiveNodes().isEmpty());

    uploadDefaultConfigSet();
    createDefaultCollection();
    verifyCluster();
    log.info("Start Solr Cluster");
  }

  @AfterClass
  public static void stopCluster() throws Exception {
    cloudSolrClient.close();
    cluster.shutdown();

    // Delete TEMP_DIR content
    Files.walkFileTree(TEMP_DIR, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }

    });

    TEMP_DIR = null;
    cluster = null;
    cloudSolrClient = null;
    log.info("Stop Solr Cluster");
  }

  private static void uploadDefaultConfigSet() {
    try {
      Path confDir = Paths.get(ClassLoader.getSystemClassLoader().getResource("conf").toURI());
      log.info(String.format("Adding default conf from [%s]", confDir));

      cluster.uploadConfigSet(confDir, DEFAULT_ZK_CONF);
    } catch (Exception e) {
      log.error(String.format("Unable to upload default config set [%s]", e.getMessage()));
    }
  }

  private static void createDefaultCollection() throws Exception {
    log.info("Creating default collection");
    cloudSolrClient.setDefaultCollection(DEFAULT_COLLECTION);
    int numShards = 1;
    int replicationFactor = 1;
    createCollection(DEFAULT_COLLECTION, numShards, replicationFactor);
  }

  private static void verifyCluster() throws IOException, SolrServerException {
    // Ping cluster to verify
    SolrPingResponse response = cloudSolrClient.ping();
    assertEquals(response.getStatus(), 0);
  }

  protected static void createCollection(String collectionName, int numShards,
      int replicationFactor) throws Exception {
    createCollection(collectionName, numShards, replicationFactor, DEFAULT_ZK_CONF, null);
  }

  protected static void createCollection(String collectionName, int numShards,
      int replicationFactor, String configName, File confDir) throws Exception {
    if (confDir != null) {
      assertTrue("Specified Solr config directory '" +
          confDir.getAbsolutePath() + "' not found!", confDir.isDirectory());

      // upload the test configs
      cluster.uploadConfigSet(confDir.toPath(), configName);
    }

    Map<String, String> collectionProperties = new HashMap<>();
    collectionProperties.putIfAbsent("solr.tests.maxBufferedDocs", "100000");
    collectionProperties.putIfAbsent("solr.tests.ramBufferSizeMB", "100");
    collectionProperties.putIfAbsent("solr.tests.mergeScheduler", "org.apache.lucene.index.ConcurrentMergeScheduler");
    collectionProperties.putIfAbsent("solr.directoryFactory", "solr.StandardDirectoryFactory");

    CollectionAdminRequest.createCollection(collectionName, configName, numShards, replicationFactor)
      .setProperties(collectionProperties)
      .processAndWait(cluster.getSolrClient(), 30);

    ensureAllReplicasAreActive(collectionName, numShards, replicationFactor, 20);
  }

  protected static void ensureAllReplicasAreActive(String testCollectionName, int shards, int rf,
      int maxWaitSecs) throws Exception {
    long startMs = System.currentTimeMillis();

    ZkStateReader zkr = cloudSolrClient.getZkStateReader();
    zkr.forceUpdateCollection(testCollectionName); // force the state to be fresh

    ClusterState cs = zkr.getClusterState();
    DocCollection docCollection = cs.getCollection(testCollectionName);
    Collection<Slice> slices = docCollection.getActiveSlices();
    assertTrue(slices.size() == shards);
    boolean allReplicasUp = false;
    long waitMs = 0L;
    long maxWaitMs = maxWaitSecs * 1000L;
    Replica leader = null;
    while (waitMs < maxWaitMs && !allReplicasUp) {
      // refresh state every 2 secs
      if (waitMs % 2000 == 0) {
        log.info("Updating ClusterState");
        cloudSolrClient.getZkStateReader().forceUpdateCollection(testCollectionName);
      }

      cs = cloudSolrClient.getZkStateReader().getClusterState();
      assertNotNull(cs);
      allReplicasUp = true; // assume true
      for (Slice shard : docCollection.getActiveSlices()) {
        String shardId = shard.getName();
        assertNotNull("No Slice for " + shardId, shard);
        Collection<Replica> replicas = shard.getReplicas();
        assertTrue(replicas.size() == rf);
        leader = shard.getLeader();
        assertNotNull(leader);
        log.info("Found " + replicas.size() + " replicas and leader on " +
            leader.getNodeName() + " for " + shardId + " in " + testCollectionName);

        // ensure all replicas are "active"
        for (Replica replica : replicas) {
          Replica.State replicaState = replica.getState();
          if (!Replica.State.ACTIVE.equals(replicaState)) {
            log.info("Replica " + replica.getName() + " for shard " + shardId + " is currently "
                + replicaState);
            allReplicasUp = false;
          }
        }
      }

      if (!allReplicasUp) {
        try {
          Thread.sleep(500L);
        } catch (Exception ignoreMe) {
        }
        waitMs += 500L;
      }
    } // end while

    if (!allReplicasUp) {
      fail("Didn't see all replicas for " + testCollectionName +
          " come up within " + maxWaitMs + " ms! ClusterState: " + printClusterStateInfo(
          testCollectionName));
    }
    long diffMs = (System.currentTimeMillis() - startMs);
    log.info("Took " + diffMs + " ms to see all replicas become active for " + testCollectionName);
  }

  protected static String printClusterStateInfo(String collection) throws Exception {
    cloudSolrClient.getZkStateReader().forceUpdateCollection(collection);
    String cs;
    ClusterState clusterState = cloudSolrClient.getZkStateReader().getClusterState();
    if (collection != null) {
      cs = clusterState.getCollection(collection).toString();
    } else {
      Map<String, DocCollection> map = clusterState.getCollectionsMap();
      CharArr out = new CharArr();
      new JSONWriter(out, 2).write(map);
      cs = out.toString();
    }
    return cs;
  }

  protected static String getBaseUrl() {
    return cluster.getZkServer().getZkAddress();
  }

  protected static String getZkHost() {
    return cluster.getZkServer().getZkHost();
  }

  protected static void removeAllDocs() throws IOException, SolrServerException {
    cloudSolrClient.deleteByQuery("*:*");
  }

  // Use to verify document count
  protected void assertCount(String query, int expectedCount)
      throws SolrServerException, IOException {

    SolrQuery solrQuery = new SolrQuery();
    solrQuery.setQuery(query);

    QueryResponse rsp = cloudSolrClient.query(solrQuery);
    SolrDocumentList docs = rsp.getResults();
    int docsSize = (int) docs.getNumFound();

    assertEquals("expected " + expectedCount + "  obtained: " + docsSize, docsSize, expectedCount);

  }

  protected void assertQ(String query, int expectedCount, String... expectedFields)
      throws SolrServerException, IOException {

    SolrQuery solrQuery = new SolrQuery();
    solrQuery.setQuery(query);

    QueryResponse rsp = cloudSolrClient.query(solrQuery);
    SolrDocumentList docs = rsp.getResults();
    assertEquals(expectedCount, docs.size());
    log.info("docs: " + docs.toString());

    if (docs.isEmpty()) {
      fail("No results for query " + query);
    }

    SolrDocument doc = docs.get(0);
    if (expectedFields != null) {
      for (int counter = 0; counter < expectedFields.length; counter += 2) {
        assertTrue(
            doc.getFieldValue(expectedFields[counter]).toString() + " != " + expectedFields[counter
                + 1], doc.getFieldValue(expectedFields[counter]).toString()
                .equals(expectedFields[counter + 1]));
      }
    }
  }
}