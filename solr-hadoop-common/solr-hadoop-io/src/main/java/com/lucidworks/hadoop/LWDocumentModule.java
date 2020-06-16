package com.lucidworks.hadoop;

import com.google.inject.AbstractModule;

import com.lucidworks.hadoop.io.LWDocument;
import com.lucidworks.hadoop.process.TikaProcess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

public class LWDocumentModule extends AbstractModule {

  private static final Logger log = LoggerFactory.getLogger(LWDocumentModule.class);

  public static final String TIKA_PARSINGG_CLASS = "com.lucidworks.hadoop.tika.TikaParsing";

  @Override
  protected void configure() {
    lwDocumentconfig();
    tikaConfig();
  }

  private void lwDocumentconfig() {
    final ServiceLoader<LWDocument> serviceLoader = ServiceLoader.load(LWDocument.class);
    for (LWDocument document : serviceLoader) {
      log.info("Loading LWDocument class: " + document.getClass());
      // Get the first service implementation.
      bind(LWDocument.class).to(document.getClass());
      break;
    }
  }

  @SuppressWarnings("unchecked")
  private void tikaConfig() {
    Class<TikaProcess> tikaClass;
    try {
      tikaClass = (Class<TikaProcess>) Class.forName(TIKA_PARSINGG_CLASS);
    } catch (ClassNotFoundException e) {
      log.warn("ClaClassNotFoundException: " + TIKA_PARSINGG_CLASS);
      return;
    }
    bind(TikaProcess.class).to(tikaClass);
  }
}

