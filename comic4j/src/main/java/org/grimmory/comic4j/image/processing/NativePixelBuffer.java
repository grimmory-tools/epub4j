/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.image.processing;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Off-heap pixel buffer using Panama FFM for high-performance image manipulation. Stores pixels as
 * packed ARGB ints in native memory, enabling zero-copy operations and cache-friendly sequential
 * access patterns.
 *
 * <p>Must be closed after use to release native memory.
 */
public final class NativePixelBuffer implements AutoCloseable {

  private final Arena arena;
  private final MemorySegment pixels;
  private final int width;
  private final int height;
  private final int pixelCount;
  private boolean closed;

  private NativePixelBuffer(Arena arena, MemorySegment pixels, int width, int height) {
    this.arena = arena;
    this.pixels = pixels;
    this.width = width;
    this.height = height;
    this.pixelCount = Math.multiplyExact(width, height);
  }

  /** Creates a new buffer from a BufferedImage, copying pixel data to off-heap memory. */
  public static NativePixelBuffer fromImage(BufferedImage image) {
    int w = image.getWidth();
    int h = image.getHeight();
    int count = Math.multiplyExact(w, h);

    Arena arena = Arena.ofConfined();
    try {
      MemorySegment pixels = arena.allocate((long) count * Integer.BYTES);

      // Convert to ARGB and copy to native memory
      BufferedImage argb = ensureArgb(image);
      int[] data = ((DataBufferInt) argb.getRaster().getDataBuffer()).getData();
      MemorySegment.copy(data, 0, pixels, ValueLayout.JAVA_INT, 0, count);

      return new NativePixelBuffer(arena, pixels, w, h);
    } catch (Exception | Error e) {
      arena.close();
      throw e;
    }
  }

  /** Creates an empty buffer of the given dimensions. */
  public static NativePixelBuffer allocate(int width, int height) {
    int count = Math.multiplyExact(width, height);
    Arena arena = Arena.ofConfined();
    try {
      MemorySegment pixels = arena.allocate((long) count * Integer.BYTES);
      pixels.fill((byte) 0);
      return new NativePixelBuffer(arena, pixels, width, height);
    } catch (Exception | Error e) {
      arena.close();
      throw e;
    }
  }

  /** Converts this buffer back to a BufferedImage. */
  public BufferedImage toImage() {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    MemorySegment.copy(pixels, ValueLayout.JAVA_INT, 0, data, 0, pixelCount);
    return image;
  }

  /** Creates a sub-region copy of this buffer. */
  public NativePixelBuffer subRegion(int x, int y, int w, int h) {
    if (x < 0 || y < 0 || x + w > width || y + h > height) {
      throw new IllegalArgumentException(
          "Sub-region [%d,%d %dx%d] out of bounds for %dx%d image"
              .formatted(x, y, w, h, width, height));
    }

    NativePixelBuffer sub = allocate(w, h);
    for (int row = 0; row < h; row++) {
      long srcOffset = (((long) y + row) * width + x) * Integer.BYTES;
      long dstOffset = ((long) row * w) * Integer.BYTES;
      MemorySegment.copy(pixels, srcOffset, sub.pixels, dstOffset, (long) w * Integer.BYTES);
    }
    return sub;
  }

  public int getPixel(int x, int y) {
    return pixels.getAtIndex(ValueLayout.JAVA_INT, (long) y * width + x);
  }

  public void setPixel(int x, int y, int argb) {
    pixels.setAtIndex(ValueLayout.JAVA_INT, (long) y * width + x, argb);
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int pixelCount() {
    return pixelCount;
  }

  MemorySegment segment() {
    return pixels;
  }

  private static BufferedImage ensureArgb(BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
      return image;
    }
    BufferedImage argb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    var g = argb.createGraphics();
    try {
      g.drawImage(image, 0, 0, null);
    } finally {
      g.dispose();
    }
    return argb;
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      arena.close();
    }
  }
}
