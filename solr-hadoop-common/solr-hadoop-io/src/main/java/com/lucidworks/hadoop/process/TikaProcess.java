package com.lucidworks.hadoop.process;

import com.lucidworks.hadoop.io.LWDocument;

public interface TikaProcess {
  LWDocument[] tikaParsing(LWDocument document);
}
