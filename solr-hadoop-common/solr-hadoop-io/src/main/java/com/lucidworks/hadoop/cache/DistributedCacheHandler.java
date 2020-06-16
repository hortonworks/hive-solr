package com.lucidworks.hadoop.cache;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

//This class provides methods to access the distributed cache
public class DistributedCacheHandler {
  private transient static Logger log = LoggerFactory.getLogger(DistributedCacheHandler.class);

  // Adding files to Cache
  @SuppressWarnings("deprecation")
  public static void addFileToCache(JobConf conf, Path path, String propertyName) throws IOException {
    log.debug("Adding {} to cache w/ name: {}", path, propertyName);
    FileSystem fileSystem = path.getFileSystem(conf);
    if (fileSystem instanceof LocalFileSystem) {
      FileSystem defFS = FileSystem.get(conf);
      Path dst = new Path("/tmp/" + path.getName());
      try {
        defFS.mkdirs(new Path("/tmp"));// just in case
      } catch (IOException e) {
      }
      defFS.copyFromLocalFile(path, dst);
      conf.set(propertyName, dst.toUri().toString());
      org.apache.hadoop.filecache.DistributedCache.addCacheFile(dst.toUri(), conf);
    } else {
      conf.set(propertyName, path.toUri().toString());
      org.apache.hadoop.filecache.DistributedCache.addCacheFile(path.toUri(), conf);
    }
  }

  @SuppressWarnings("deprecation")
  private static void addFileToCache(JobConf conf, Path path, FileSystem fs, String nameToStore) {
    conf.set(nameToStore, path.getName());
    log.info("storing in conf:\n key: " + nameToStore + "\nvalue: " + path.getName());
    try {
      org.apache.hadoop.filecache.DistributedCache.addFileToClassPath(path, conf, fs);
    } catch (Exception e) {
      log.error("Error while creating distributed cache", e);
    }
    log.info("File added to cache " + path.toString());
  }

  // Reading files from Cache
  @SuppressWarnings("deprecation")
  public static String getFileFromCache(JobConf conf, String fileName) {
    BufferedReader fis = null;
    StringBuilder response = new StringBuilder();
    try {
      String cacheFile = conf.get(fileName);
      Path[] pathsInCache = org.apache.hadoop.filecache.DistributedCache.getLocalCacheFiles(conf);
      if (pathsInCache != null) {
        for (Path p : pathsInCache) {
          if (cacheFile.contains(p.getName())) {
            String line;
            try {
              fis = new BufferedReader(new FileReader(p.toString()));
            } catch (FileNotFoundException e) {
              // Trying the cache file
              log.warn("Error reading {} from the distributed cache, Trying {}", p.toString(), cacheFile);
              fis = new BufferedReader(new FileReader(cacheFile));
            }
            while ((line = fis.readLine()) != null) {
              line = line.trim();
              response.append(line).append('\n');
            }
          }
        }
      }
      return response.toString();
    } catch (Exception e) {
      log.error("Error reading distributed cache, " + e.getMessage(), e);
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          log.error("Exception", e);
        }
      }
    }
    return null;
  }

}