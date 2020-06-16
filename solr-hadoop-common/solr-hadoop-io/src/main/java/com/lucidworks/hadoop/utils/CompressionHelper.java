package com.lucidworks.hadoop.utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.Decompressor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class CompressionHelper {
  private transient static Logger log = LoggerFactory.getLogger(CompressionHelper.class);

  //FIXME: use MIME types
  public static final String GZIP_EXTGENSION = ".gz";
  public static final String BZIP2_EXTGENSION = ".bz2";
  public static final String LZO_EXTGENSION = ".lzo";
  public static final String SNAPPY_EXTGENSION = ".snappy";

  public static boolean isCompressed(Path filePath) {
    String stringPath = filePath.toString();
    if (stringPath.endsWith(GZIP_EXTGENSION)
        || stringPath.endsWith(BZIP2_EXTGENSION)
        || stringPath.endsWith(LZO_EXTGENSION)
        || stringPath.endsWith(SNAPPY_EXTGENSION)) {
      System.out.println("File: " + filePath.toString() + " is compressed");
      return true;
    }
    return false;
  }

  /**
   * This function opens a stream to read a compressed file. Stream is not
   * closed, the user has to close it when read is finished.
   *
   * @param filePath
   * @return
   */
  public static InputStream openCompressedFile(Path filePath, Configuration conf) {
    CompressionCodecFactory factory = new CompressionCodecFactory(conf);
    CompressionCodec codec = factory.getCodec(filePath);

    if (codec == null) {
      log.error("No codec found for file " + filePath.toString());
      return null;
    }

    try {
      FileSystem fs = filePath.getFileSystem(conf);
      Decompressor decompressor = codec.createDecompressor();
      return codec.createInputStream(fs.open(filePath), decompressor);
    } catch (Exception e) {
      log.error("Error opening compressed file: " + e.getMessage());
      e.printStackTrace();
    }
    return null;
  }

}
