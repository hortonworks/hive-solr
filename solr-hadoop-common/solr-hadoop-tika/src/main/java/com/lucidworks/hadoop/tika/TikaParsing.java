package com.lucidworks.hadoop.tika;

import com.google.common.base.Strings;
import com.lucidworks.hadoop.io.LWDocument;
import com.lucidworks.hadoop.io.LWDocumentProvider;
import com.lucidworks.hadoop.process.TikaProcess;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// basic tika parsing: this class needs more work, dynamic fields/ check errors ...
public class TikaParsing implements TikaProcess {
  private transient static Logger log = LoggerFactory.getLogger(TikaParsing.class);

  //TODO: use this flags here?
  public static final String TIKA_INCLUDE_IMAGES = "default.include.images";
  public static final String TIKA_FLATENN_COMPOUND = "default.faltten.compound";
  public static final String TIKA_ADD_FAILED_DOCS = "default.add.failed.docs";
  public static final String TIKA_ADD_ORIGINAL_CONTENT = "default.add.original.content";
  public static final String FIELD_MAPPING_RENAME_UNKNOWN = "default.rename.unknown";

  private static final String RAW_CONTENT = "_raw_content_";

  public static boolean includeImages = false;
  public static boolean flattenCompound = false;
  public static boolean addFailedDocs = false;
  public static boolean addOriginalContent = false;
  public static boolean renameUnknown = false;

  public static int MAX_TERM_LENGTH_UTF = 32766;

  private static LWDocument[] parseLWSolrDocument(
    LWDocument primaryDocument,
    byte[] data) {

    RecursiveMetadataParser parser = new RecursiveMetadataParser(new AutoDetectParser());
    ParseContext context = new ParseContext();
    context.set(Parser.class, parser);

    // create the content handler
    ContentHandler text = new BodyContentHandler();
    LinkContentHandler links = new LinkContentHandler();
    ContentHandler handler = new TeeContentHandler(links, text);

    // parser input stream
    InputStream input = new ByteArrayInputStream(data);

    // metadata for parser
    Metadata metadata = new Metadata();

    try {
      parser.parse(input, handler, metadata, context);
    } catch (IOException e) {
      e.printStackTrace();
      log.warn("IOException " + primaryDocument.getId());
    } catch (SAXException e) {
      e.printStackTrace();
      log.warn("SAXException " + primaryDocument.getId());
    } catch (TikaException e) {
      e.printStackTrace();
      log.warn("TikaException " + primaryDocument.getId());
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    List<LWDocument> temporalResponse = new ArrayList<>();
    String primaryDocumentId = primaryDocument.getId();
    for (Map.Entry<Metadata, ContentHandler> nestedMetadata : parser.getNestedDocuments()) {
      LWDocument nestedDocument = generateDocument(LWDocumentProvider.createDocument(), nestedMetadata.getKey(),
        nestedMetadata.getValue());

      if (!Strings.isNullOrEmpty(primaryDocumentId)) {
        // add id to each nested document. Id will be the primaryDocument uri + # + nested resource name or title
        String nestedResourceName = nestedMetadata.getKey().get("resourceName");
        String nestedTitle = nestedMetadata.getKey().get("title");
        if (!Strings.isNullOrEmpty(nestedResourceName)) {
          nestedDocument.setId(primaryDocumentId + "#" + nestedResourceName);
        } else if (!Strings.isNullOrEmpty(nestedTitle)) {
          nestedDocument.setId(primaryDocumentId + "#" + nestedTitle);
        } else {
          log.warn("No resource name or title were found for document: [{}]", primaryDocumentId);
          nestedDocument.setId(primaryDocumentId + "#" + UUID.randomUUID());
        }
      } else {
        log.warn("Can not set id for nested document, [{}]", primaryDocumentId);
        nestedDocument.setId("directory_ingest_mapper_id_" + UUID.randomUUID());
      }
      temporalResponse.add(nestedDocument);
    }
    return temporalResponse.toArray(new LWDocument[temporalResponse.size()]);
  }

  public static LWDocument generateDocument(LWDocument document, Metadata metadata, ContentHandler content) {
    if (metadata != null) {
      for (String name : metadata.names()) {
        String value = metadata.get(name);
        document.addField(name, value);
      }
    }
    if (content != null) {
      String body = content.toString();
      if (body.length() > MAX_TERM_LENGTH_UTF) {
        body = body.substring(0, MAX_TERM_LENGTH_UTF);
      }
      document.addField("body", body);
    }
    return document;
  }

  @Override
  public LWDocument[] tikaParsing(LWDocument document) {
    Object raw = document.getFirstFieldValue(RAW_CONTENT);
    if (raw instanceof byte[]) {
      return parseLWSolrDocument(document, (byte[]) raw);
    }
    document.removeField(RAW_CONTENT);
    return new LWDocument[]{document};
  }

  private static class RecursiveMetadataParser extends ParserDecorator {

    private List<Map.Entry<Metadata, ContentHandler>> nestedFiles;

    public RecursiveMetadataParser(Parser parser) {
      super(parser);
      nestedFiles = new ArrayList<>();
    }

    public List<Map.Entry<Metadata, ContentHandler>> getNestedDocuments() {
      return nestedFiles;
    }

    @Override
    public void parse(InputStream stream, ContentHandler ignore, Metadata metadata, ParseContext context)
      throws IOException, SAXException, TikaException {
      ContentHandler content = new BodyContentHandler();
      super.parse(stream, content, metadata, context);

      log.debug("Begin Document");
      log.debug("Metadata: ");
      for (String metadata_name : metadata.names()) {
        log.debug("\t" + metadata_name + " -> " + Arrays.asList(metadata.getValues(metadata_name)));
      }
      log.debug("End Document");
      nestedFiles.add(new AbstractMap.SimpleEntry<Metadata, ContentHandler>(metadata, content));
    }
  }
}