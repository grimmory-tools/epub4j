/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Probes image file headers to extract dimensions without full decoding. Uses direct byte parsing
 * via FFM {@link MemorySegment} for JPEG, PNG, GIF, BMP, and WEBP headers. Falls back to ImageIO
 * header-only reading for unrecognized formats.
 *
 * <p>This is significantly faster than full ImageIO decoding since only the first few hundred bytes
 * are read.
 */
public final class ImageProbe {

  // JPEG markers
  private static final int JPEG_SOI = 0xFFD8;
  private static final int JPEG_SOF0 = 0xFFC0;
  private static final int JPEG_SOF2 = 0xFFC2;

  // PNG signature
  private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;

  // WEBP container
  private static final int RIFF_MAGIC = 0x52494646; // "RIFF"
  private static final int WEBP_MAGIC = 0x57454250; // "WEBP"

  private ImageProbe() {}

  /**
   * Reads image dimensions from the given bytes by parsing the file header.
   *
   * @param data the image file bytes
   * @return the dimensions, or null if the format is unrecognized or the header is corrupt
   */
  public static ImageDimensions readDimensions(byte[] data) {
    if (data == null || data.length < 8) {
      return null;
    }
    ImageDimensions dims = probeHeader(data, data.length);
    if (dims != null) {
      return dims;
    }
    return fallbackImageIO(data);
  }

  /**
   * Reads image dimensions from the given stream by parsing the file header. The stream is consumed
   * up to header probing; if that fails, falls back to ImageIO which may read more.
   *
   * @param stream the image input stream (not closed by this method; should support mark/reset or
   *     be a fresh stream)
   * @return the dimensions, or null if the format is unrecognized or the header is corrupt
   */
  public static ImageDimensions readDimensions(InputStream stream) {
    if (stream == null) {
      return null;
    }
    try {
      byte[] data = stream.readAllBytes();
      return readDimensions(data);
    } catch (IOException e) {
      return null;
    }
  }

  /** Probes header bytes using FFM MemorySegment for fast, bounds-safe access. */
  private static ImageDimensions probeHeader(byte[] data, int length) {
    if (length < 8) return null;

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment seg = arena.allocate(length);
      MemorySegment.copy(data, 0, seg, ValueLayout.JAVA_BYTE, 0, length);

      // PNG: 8-byte signature + IHDR chunk
      if (length >= 24) {
        long sig = seg.get(ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 0);
        if (sig == PNG_SIGNATURE) {
          return probePng(seg, length);
        }
      }

      // JPEG: SOI marker 0xFFD8
      int first2 =
          Byte.toUnsignedInt(seg.get(ValueLayout.JAVA_BYTE, 0)) << 8
              | Byte.toUnsignedInt(seg.get(ValueLayout.JAVA_BYTE, 1));
      if (first2 == JPEG_SOI) {
        return probeJpeg(data, length);
      }

      // GIF: "GIF87a" or "GIF89a"
      if (length >= 10
          && seg.get(ValueLayout.JAVA_BYTE, 0) == 'G'
          && seg.get(ValueLayout.JAVA_BYTE, 1) == 'I'
          && seg.get(ValueLayout.JAVA_BYTE, 2) == 'F') {
        return probeGif(seg);
      }

      // BMP: "BM" header
      if (length >= 26
          && seg.get(ValueLayout.JAVA_BYTE, 0) == 'B'
          && seg.get(ValueLayout.JAVA_BYTE, 1) == 'M') {
        return probeBmp(seg, length);
      }

      // WEBP: "RIFF" + size + "WEBP"
      if (length >= 30) {
        int riff = seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 0);
        int webp = seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8);
        if (riff == RIFF_MAGIC && webp == WEBP_MAGIC) {
          return probeWebp(data, length);
        }
      }
    }

    return null;
  }

  /** PNG: dimensions are in IHDR chunk at fixed offset (bytes 16-23). */
  private static ImageDimensions probePng(MemorySegment seg, int length) {
    if (length < 24) return null;
    // IHDR chunk: offset 8 = length(4), 12 = "IHDR"(4), 16 = width(4), 20 = height(4)
    int width = seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 16);
    int height = seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 20);
    if (width <= 0 || height <= 0) return null;
    return ImageDimensions.of(width, height);
  }

  /** JPEG: scan for SOF0 (0xFFC0) or SOF2 (0xFFC2) marker to find dimensions. */
  private static ImageDimensions probeJpeg(byte[] data, int length) {
    // Skip SOI (2 bytes), then walk markers
    int pos = 2;
    while (pos + 4 < length) {
      if ((data[pos] & 0xFF) != 0xFF) {
        pos++;
        continue;
      }
      int marker = (data[pos] & 0xFF) << 8 | (data[pos + 1] & 0xFF);
      if (marker == 0xFFFF) {
        // Padding byte
        pos++;
        continue;
      }

      if (marker == JPEG_SOF0
          || marker == JPEG_SOF2
          || marker == 0xFFC1
          || marker == 0xFFC3
          || marker == 0xFFC5
          || marker == 0xFFC6
          || marker == 0xFFC7
          || marker == 0xFFC9
          || marker == 0xFFCA
          || marker == 0xFFCB
          || marker == 0xFFCD
          || marker == 0xFFCE
          || marker == 0xFFCF) {
        // SOFn marker: length(2), precision(1), height(2), width(2)
        if (pos + 9 > length) return null;
        int height = ((data[pos + 5] & 0xFF) << 8) | (data[pos + 6] & 0xFF);
        int width = ((data[pos + 7] & 0xFF) << 8) | (data[pos + 8] & 0xFF);
        if (width <= 0 || height <= 0) return null;
        return ImageDimensions.of(width, height);
      }

      // Skip this segment: read segment length
      if (pos + 3 >= length) return null;
      int segLen = ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
      if (segLen < 2) return null;
      pos += 2 + segLen;
    }
    return null;
  }

  /** GIF: width and height are at bytes 6-9 (little-endian 16-bit). */
  private static ImageDimensions probeGif(MemorySegment seg) {
    int width =
        Short.toUnsignedInt(
            seg.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 6));
    int height =
        Short.toUnsignedInt(
            seg.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8));
    if (width <= 0 || height <= 0) return null;
    return ImageDimensions.of(width, height);
  }

  /** BMP: BITMAPINFOHEADER at offset 14; width at 18 (int32 LE), height at 22 (int32 LE). */
  private static ImageDimensions probeBmp(MemorySegment seg, int length) {
    if (length < 26) return null;
    int width = seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 18);
    int height = seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 22);
    // BMP height can be negative (top-down bitmap)
    if (height < 0) height = -height;
    if (width <= 0 || height <= 0) return null;
    return ImageDimensions.of(width, height);
  }

  /**
   * WEBP: Parse the RIFF container to find VP8, VP8L, or VP8X chunks. VP8 (lossy): dimensions in
   * frame header VP8L (lossless): dimensions in the bitstream header VP8X (extended): canvas size
   * in the chunk header
   */
  private static ImageDimensions probeWebp(byte[] data, int length) {
    if (length < 21) return null;

    // Chunk starts at offset 12
    int pos = 12;
    while (pos + 8 <= length) {
      String fourcc = new String(data, pos, 4, StandardCharsets.US_ASCII);
      int chunkSize =
          (data[pos + 4] & 0xFF)
              | ((data[pos + 5] & 0xFF) << 8)
              | ((data[pos + 6] & 0xFF) << 16)
              | ((data[pos + 7] & 0xFF) << 24);

      switch (fourcc) {
        case "VP8 " -> {
          // Lossy: frame header at chunk data offset + 6
          int dataStart = pos + 8;
          if (dataStart + 10 > length) return null;
          // Skip 3-byte frame tag + 3-byte sync code "9d 01 2a"
          int dimOff = dataStart + 6;
          if (dimOff + 4 > length) return null;
          int width = ((data[dimOff] & 0xFF) | ((data[dimOff + 1] & 0xFF) << 8)) & 0x3FFF;
          int height = ((data[dimOff + 2] & 0xFF) | ((data[dimOff + 3] & 0xFF) << 8)) & 0x3FFF;
          if (width > 0 && height > 0) return ImageDimensions.of(width, height);
        }
        case "VP8L" -> {
          // Lossless: signature byte 0x2F, then 4 bytes with packed width/height
          int dataStart = pos + 8;
          if (dataStart + 5 > length) return null;
          if ((data[dataStart] & 0xFF) != 0x2F) return null;
          int bits =
              (data[dataStart + 1] & 0xFF)
                  | ((data[dataStart + 2] & 0xFF) << 8)
                  | ((data[dataStart + 3] & 0xFF) << 16)
                  | ((data[dataStart + 4] & 0xFF) << 24);
          int width = (bits & 0x3FFF) + 1;
          int height = ((bits >> 14) & 0x3FFF) + 1;
          if (width > 0 && height > 0) return ImageDimensions.of(width, height);
        }
        case "VP8X" -> {
          // Extended: canvas width/height at chunk data + 4 and + 7 (24-bit each)
          int dataStart = pos + 8;
          if (dataStart + 10 > length) return null;
          int width =
              1
                  + ((data[dataStart + 4] & 0xFF)
                      | ((data[dataStart + 5] & 0xFF) << 8)
                      | ((data[dataStart + 6] & 0xFF) << 16));
          int height =
              1
                  + ((data[dataStart + 7] & 0xFF)
                      | ((data[dataStart + 8] & 0xFF) << 8)
                      | ((data[dataStart + 9] & 0xFF) << 16));
          if (width > 0 && height > 0) return ImageDimensions.of(width, height);
        }
        default -> {
          // Skip unknown chunks
        }
      }

      // Advance to next chunk (chunks are 2-byte aligned)
      pos += 8 + chunkSize + (chunkSize % 2);
    }
    return null;
  }

  /** Fallback: use ImageIO header reading (reads just enough for dimensions). */
  private static ImageDimensions fallbackImageIO(byte[] data) {
    try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      if (iis == null) return null;
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) return null;
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width > 0 && height > 0) {
          return ImageDimensions.of(width, height);
        }
      } finally {
        reader.dispose();
      }
    } catch (IOException ignored) {
      // Fall through to return null
    }
    return null;
  }
}
