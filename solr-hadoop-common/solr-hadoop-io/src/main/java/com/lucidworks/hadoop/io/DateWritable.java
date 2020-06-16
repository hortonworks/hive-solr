package com.lucidworks.hadoop.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Date;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.WritableComparable;

/**
 * A thinly veiled LongWritable that looks like a Date.
 */
public class DateWritable implements WritableComparable<DateWritable> {

  private Date date;
  private LongWritable epoch = new LongWritable();

  public DateWritable() {
  }

  public DateWritable(Date date) {
    this.date = date;
    this.epoch.set(date.getTime());
  }

  public Date get() {
    return date;
  }

  public void set(Date date) {
    this.date = date;
    this.epoch.set(date.getTime());
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    epoch.readFields(in);
    date = new Date(epoch.get());
  }

  @Override
  public void write(DataOutput out) throws IOException {
    epoch.write(out);
  }

  @Override
  public int compareTo(DateWritable other) {
    return this.epoch.compareTo(other.epoch);
  }

  @Override
  public String toString() {
    return date.toString();
  }
}
