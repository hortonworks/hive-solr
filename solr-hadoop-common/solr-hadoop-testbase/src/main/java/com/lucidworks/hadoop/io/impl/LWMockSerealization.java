package com.lucidworks.hadoop.io.impl;

import com.lucidworks.hadoop.io.LWDocumentWritable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.Serializer;
import org.apache.hadoop.util.ReflectionUtils;

public class LWMockSerealization extends Configured
    implements org.apache.hadoop.io.serializer.Serialization<Writable> {

  static class WritableDeserializer extends Configured implements Deserializer<Writable> {

    private Class<?> writableClass;
    private DataInputStream dataIn;

    public WritableDeserializer(Configuration conf, Class<?> c) {
      setConf(conf);
      this.writableClass = c;
    }

    @Override
    public void open(InputStream in) {
      if (in instanceof DataInputStream) {
        dataIn = (DataInputStream) in;
      } else {
        dataIn = new DataInputStream(in);
      }
    }

    @Override
    public Writable deserialize(Writable w) throws IOException {
      Writable writable;
      if (w == null) {
        writable = (Writable) ReflectionUtils.newInstance(writableClass, getConf());
        // Create a LWMockDocument, avoid the NPE in LWDocumentWritable in tests.
        if ("class com.lucidworks.hadoop.io.LWDocumentWritable".equals(writableClass.toString())) {
          ((LWDocumentWritable) writable).setLWDocument(new LWMockDocument());
        }
      } else {

        writable = w;
      }
      writable.readFields(dataIn);
      return writable;
    }

    @Override
    public void close() throws IOException {
      dataIn.close();
    }

  }

  static class WritableSerializer extends Configured implements Serializer<Writable> {

    private DataOutputStream dataOut;

    @Override
    public void open(OutputStream out) {
      if (out instanceof DataOutputStream) {
        dataOut = (DataOutputStream) out;
      } else {
        dataOut = new DataOutputStream(out);
      }
    }

    @Override
    public void serialize(Writable w) throws IOException {
      w.write(dataOut);
    }

    @Override
    public void close() throws IOException {
      dataOut.close();
    }

  }

  @Override
  public boolean accept(Class<?> c) {
    return Writable.class.isAssignableFrom(c);
  }

  @Override
  public Serializer<Writable> getSerializer(Class<Writable> c) {
    return new WritableSerializer();
  }

  @Override
  public Deserializer<Writable> getDeserializer(Class<Writable> c) {
    return new WritableDeserializer(getConf(), c);
  }
}
