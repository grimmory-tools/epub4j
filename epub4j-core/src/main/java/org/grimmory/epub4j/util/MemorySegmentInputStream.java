package org.grimmory.epub4j.util;

import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * An InputStream backed by a {@link MemorySegment} for zero-copy reads from off-heap memory.
 *
 * <p>Supports mark/reset. All read operations go directly to the off-heap segment without
 * intermediate heap copies beyond the caller-supplied buffer.
 */
public final class MemorySegmentInputStream extends InputStream {

  private final MemorySegment segment;
  private long position;
  private long mark;

  public MemorySegmentInputStream(MemorySegment segment) {
    this.segment = segment;
    this.position = 0;
    this.mark = 0;
  }

  @Override
  public int read() {
    if (position >= segment.byteSize()) {
      return -1;
    }
    return Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, position++));
  }

  @Override
  public int read(byte[] b, int off, int len) {
    if (position >= segment.byteSize()) {
      return -1;
    }
    long remaining = segment.byteSize() - position;
    int toRead = (int) Math.min(len, remaining);
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, position, b, off, toRead);
    position += toRead;
    return toRead;
  }

  @Override
  public long skip(long n) {
    long remaining = segment.byteSize() - position;
    long toSkip = Math.min(n, remaining);
    position += toSkip;
    return toSkip;
  }

  @Override
  public int available() {
    long remaining = segment.byteSize() - position;
    return (int) Math.min(remaining, Integer.MAX_VALUE);
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public void mark(int readAheadLimit) {
    mark = position;
  }

  @Override
  public void reset() {
    position = mark;
  }
}
