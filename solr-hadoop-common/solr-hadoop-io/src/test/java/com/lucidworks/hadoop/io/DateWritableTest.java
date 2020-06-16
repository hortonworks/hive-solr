package com.lucidworks.hadoop.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 *
 **/
public class DateWritableTest {

  @Test
  public void test() throws Exception {
    Date now = new Date();
    DateWritable writable = new DateWritable(now);
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    DataOutput out = new DataOutputStream(outStream);
    writable.write(out);
    ByteArrayInputStream inputStream = new ByteArrayInputStream(outStream.toByteArray());
    DataInput in = new DataInputStream(inputStream);
    DateWritable readWritable = new DateWritable();
    readWritable.readFields(in);
    Assert.assertEquals(writable.get(), readWritable.get());

  }
}
