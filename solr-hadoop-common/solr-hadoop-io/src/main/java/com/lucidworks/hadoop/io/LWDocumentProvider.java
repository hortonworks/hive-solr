package com.lucidworks.hadoop.io;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.lucidworks.hadoop.LWDocumentModule;
import com.lucidworks.hadoop.process.TikaProcess;

import java.util.Map;
import org.apache.hadoop.mapred.JobConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LWDocumentProvider {

  public static final String TIKA_PROCESS = "lw.tika.process";

  private static final Logger log = LoggerFactory.getLogger(LWDocumentProvider.class);

  private static Injector injector = Guice.createInjector(new LWDocumentModule());

  public static LWDocument createDocument() {
    return injector.getInstance(LWDocument.class);
  }

  public static LWDocument createDocument(String id, Map<String, String> metadata) {
    LWDocument document = createDocument();
    document.setId(id);
    document.setMetadata(metadata);
    return document;
  }

  public static LWDocument[] processIfNeeded(LWDocument doc, JobConf jobConf) {
    if (!jobConf.getBoolean(TIKA_PROCESS, false)) {
      return new LWDocument[] { doc };
    }
    TikaProcess tika = injector.getInstance(TikaProcess.class);
    return tika.tikaParsing(doc);
  }
}
