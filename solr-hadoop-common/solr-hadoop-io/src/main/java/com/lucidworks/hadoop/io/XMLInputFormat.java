package com.lucidworks.hadoop.io;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Reads records that are delimited by a specifc begin/end tag.
 */
public class XMLInputFormat extends FileInputFormat {

  private transient static Logger log = LoggerFactory.getLogger(XMLInputFormat.class);

  public static final String START_TAG_KEY = "lww.xml.start";
  public static final String END_TAG_KEY = "lww.xml.end";

  @Override
  public RecordReader<Text, Text> getRecordReader(InputSplit inputSplit, JobConf jobConf, Reporter reporter) throws IOException {
    return new XMLRecordReader((FileSplit) inputSplit, jobConf);
  }

  public static class XMLRecordReader implements RecordReader<Text, Text> {
    private final byte[] startTag;
    private final byte[] endTag;
    private final long start;
    private final long end;
    private final FSDataInputStream fsin;
    private final DataOutputBuffer buffer = new DataOutputBuffer();
    private final String path;

    public XMLRecordReader(FileSplit split, JobConf jobConf) throws IOException {
      log.info("Setting up XMLRecordReader for path: [" + split.getPath() + "]");
      log.info("startTag=" + jobConf.get(START_TAG_KEY) + ", endTag=" + jobConf.get(END_TAG_KEY));

      startTag = jobConf.get(START_TAG_KEY).getBytes("utf-8");
      endTag = jobConf.get(END_TAG_KEY).getBytes("utf-8");

      // open the file and seek to the start of the split
      start = split.getStart();
      end = start + split.getLength();

      Path file = split.getPath();
      FileSystem fs = file.getFileSystem(jobConf);

      path = split.getPath().getName();

      fsin = fs.open(split.getPath());
      fsin.seek(start);
    }

    @Override
    public boolean next(Text key, Text value) throws IOException {
      if (fsin.getPos() < end) {
        if (readUntilMatch(startTag, false)) {
          try {
            buffer.write(startTag);
            if (readUntilMatch(endTag, true)) {
              key.set(path + "-" + fsin.getPos());
              value.set(buffer.getData(), 0, buffer.getLength());
              return true;
            }
          } finally {
            buffer.reset();
          }
        }
      }
      return false;
    }

    @Override
    public Text createKey() {
      return new Text();
    }

    @Override
    public Text createValue() {
      return new Text();
    }

    public long getPos() throws IOException {
      return fsin.getPos();
    }

    public void close() throws IOException {
      fsin.close();
    }

    public float getProgress() throws IOException {
      return (fsin.getPos() - start) / (float) (end - start);
    }

    private boolean readUntilMatch(byte[] match, boolean withinBlock) throws IOException {
      int i = 0;
      while (true) {

        int b = fsin.read();

        // end of file:
        if (b == -1) {
          return false;
        }

        // save to buffer:
        if (withinBlock) {
          buffer.write(b);
        }

        // check if we're matching:
        if (b == match[i]) {
          i++;
          if (i >= match.length) {
            return true;
          }
        } else {
          i = 0;
        }
        // see if we've passed the stop point:
        if (!withinBlock && i == 0 && fsin.getPos() >= end) {
          return false;
        }
      }
    }
  }
}