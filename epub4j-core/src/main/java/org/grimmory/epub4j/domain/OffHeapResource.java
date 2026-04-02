package org.grimmory.epub4j.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import org.grimmory.epub4j.util.MemorySegmentInputStream;

/**
 * A Resource that stores its data off-heap in a {@link MemorySegment}. Reduces GC pressure for
 * large resources (images, fonts, audio) by keeping data outside the Java heap.
 *
 * <p>Must be explicitly closed to release the off-heap memory. After closing, any attempt to read
 * data will throw {@link IOException}.
 */
public final class OffHeapResource extends Resource implements AutoCloseable {

  @Serial private static final long serialVersionUID = 7891234567890123456L;

  private static final Cleaner CLEANER = Cleaner.create();

  private transient volatile Arena arena;
  private transient volatile MemorySegment segment;
  private transient Cleaner.Cleanable cleanable;

  /**
   * Creates an OffHeapResource by copying the given bytes to off-heap memory.
   *
   * @param data the resource data to copy off-heap
   * @param href the resource's href within the epub
   */
  public OffHeapResource(byte[] data, String href) {
    this(null, data, href, MediaTypes.determineMediaType(href));
  }

  /**
   * Creates an OffHeapResource by copying the given bytes to off-heap memory.
   *
   * @param id the resource id (may be null for auto-generation)
   * @param data the resource data to copy off-heap
   * @param href the resource's href within the epub
   * @param mediaType the resource's media type
   */
  public OffHeapResource(String id, byte[] data, String href, MediaType mediaType) {
    super(id, null, href, mediaType);
    this.arena = Arena.ofShared();
    this.segment = arena.allocate(data.length);
    MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_BYTE, 0, data.length);
    this.cleanable = CLEANER.register(this, new ArenaCloser(arena));
  }

  /**
   * Creates an OffHeapResource backed by an existing segment. The caller must ensure the arena
   * outlives this resource. Closing this resource will close the provided arena.
   *
   * @param id the resource id
   * @param segment the off-heap memory segment containing data
   * @param arena the arena that owns the segment
   * @param href the resource's href within the epub
   * @param mediaType the resource's media type
   */
  OffHeapResource(String id, MemorySegment segment, Arena arena, String href, MediaType mediaType) {
    super(id, null, href, mediaType);
    this.arena = arena;
    this.segment = segment;
    this.cleanable = CLEANER.register(this, new ArenaCloser(arena));
  }

  /**
   * Returns the off-heap MemorySegment for zero-copy access.
   *
   * @return the backing MemorySegment, or null if closed
   */
  public MemorySegment getSegment() {
    return segment;
  }

  @Override
  public byte[] getData() throws IOException {
    if (segment == null) {
      throw new IOException("OffHeapResource has been closed: " + getHref());
    }
    byte[] copy = new byte[Math.toIntExact(segment.byteSize())];
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0, copy, 0, copy.length);
    return copy;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    if (segment == null) {
      throw new IOException("OffHeapResource has been closed: " + getHref());
    }
    return new MemorySegmentInputStream(segment);
  }

  @Override
  public long getSize() {
    if (segment == null) {
      return 0;
    }
    return segment.byteSize();
  }

  @Override
  public void setData(byte[] newData) {
    // Clean the old arena before allocating a new one
    if (cleanable != null) {
      cleanable.clean();
    }
    Arena newArena = Arena.ofShared();
    MemorySegment newSegment = newArena.allocate(newData.length);
    MemorySegment.copy(newData, 0, newSegment, ValueLayout.JAVA_BYTE, 0, newData.length);
    this.arena = newArena;
    this.segment = newSegment;
    this.cleanable = CLEANER.register(this, new ArenaCloser(newArena));
  }

  @Override
  public void close() {
    if (cleanable != null) {
      cleanable.clean();
      cleanable = null;
    }
    segment = null;
    arena = null;
  }

  /**
   * Weak reference-safe action that only captures the Arena, not the OffHeapResource itself.
   * Required so the Cleaner reference does not prevent the resource from becoming
   * phantom-reachable.
   */
  private record ArenaCloser(Arena arena) implements Runnable {
    @Override
    public void run() {
      try {
        arena.close();
      } catch (IllegalStateException ignored) {
        // Arena already closed
      }
    }
  }

  @Serial
  private void writeObject(ObjectOutputStream out) throws IOException {
    byte[] onHeap = getData();
    out.defaultWriteObject();
    out.writeInt(onHeap.length);
    out.write(onHeap);
  }

  @Serial
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    int length = in.readInt();
    byte[] onHeap = in.readNBytes(length);
    this.arena = Arena.ofShared();
    this.segment = arena.allocate(length);
    MemorySegment.copy(onHeap, 0, segment, ValueLayout.JAVA_BYTE, 0, length);
    this.cleanable = CLEANER.register(this, new ArenaCloser(arena));
  }
}
