/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.grimmory.comic4j.domain.ImageFormat;
import org.grimmory.comic4j.error.ComicError;

/**
 * Image format conversion utilities for comic page processing. Provides JPEG transcoding with
 * decompression bomb protection and JPEG passthrough optimization.
 *
 * <p>Uses ImageIO for codec operations. JPEG inputs are passed through without re-encoding when
 * possible.
 */
public final class ImageCodec {

  private ImageCodec() {}

  /**
   * Transcodes image data to JPEG format. If the input is already JPEG, returns the data as-is
   * (passthrough). Otherwise decodes, converts to RGB color space, and encodes as JPEG.
   *
   * @param data the source image bytes
   * @param quality JPEG compression quality (0.0-1.0)
   * @param maxPixelCount maximum allowed pixel count (decompression bomb protection)
   * @return the JPEG-encoded image bytes
   */
  public static byte[] transcodeToJpeg(byte[] data, float quality, long maxPixelCount) {
    if (data == null || data.length == 0) {
      throw ComicError.ERR_C060.exception("Input data is null or empty");
    }
    validateQuality(quality);

    if (isJpegData(data)) {
      return data;
    }

    return decodeAndEncode(data, quality, maxPixelCount);
  }

  /**
   * Transcodes image data from a stream to JPEG format.
   *
   * @param input the source image stream
   * @param quality JPEG compression quality (0.0-1.0)
   * @param maxPixelCount maximum allowed pixel count
   * @return the JPEG-encoded image bytes
   */
  public static byte[] transcodeToJpeg(InputStream input, float quality, long maxPixelCount) {
    if (input == null) {
      throw ComicError.ERR_C060.exception("Input stream is null");
    }
    try {
      byte[] data = input.readAllBytes();
      return transcodeToJpeg(data, quality, maxPixelCount);
    } catch (IOException e) {
      throw ComicError.ERR_C060.exception("Failed to read input stream", e);
    }
  }

  /** Returns true if the filename indicates a JPEG image. */
  public static boolean isJpeg(String filename) {
    if (filename == null) return false;
    String lower = filename.toLowerCase(Locale.ROOT);
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
  }

  /** Returns true if the format requires transcoding to JPEG (i.e., is not already JPEG). */
  public static boolean needsTranscode(ImageFormat format) {
    return format != null && format != ImageFormat.JPEG;
  }

  /** Checks if the byte data starts with the JPEG SOI marker (0xFFD8). */
  private static boolean isJpegData(byte[] data) {
    return data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
  }

  private static byte[] decodeAndEncode(byte[] data, float quality, long maxPixelCount) {
    BufferedImage image;
    try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      if (iis == null) {
        throw ComicError.ERR_C061.exception("No ImageInputStream available");
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) {
        throw ComicError.ERR_C061.exception("No ImageReader found for input format");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        long pixels = (long) width * height;
        if (pixels > maxPixelCount) {
          throw ComicError.ERR_C054.exception(
              "%dx%d (%,d px) exceeds limit of %,d px"
                  .formatted(width, height, pixels, maxPixelCount));
        }
        image = reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw ComicError.ERR_C060.exception("Failed to decode image", e);
    }

    return encodeJpeg(ensureRgb(image), quality);
  }

  /** Converts the image to TYPE_INT_RGB if it has an alpha channel or incompatible color space. */
  private static BufferedImage ensureRgb(BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_RGB) {
      return image;
    }
    BufferedImage rgb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g2d = rgb.createGraphics();
    try {
      g2d.drawImage(image, 0, 0, null);
    } finally {
      g2d.dispose();
    }
    image.flush();
    return rgb;
  }

  private static byte[] encodeJpeg(BufferedImage image, float quality) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
    if (!writers.hasNext()) {
      throw ComicError.ERR_C060.exception("No JPEG ImageWriter available");
    }
    ImageWriter writer = writers.next();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      writer.setOutput(ios);
      writer.write(null, new IIOImage(image, null, null), param);
    } catch (IOException e) {
      throw ComicError.ERR_C060.exception("Failed to encode JPEG", e);
    } finally {
      writer.dispose();
      image.flush();
    }
    return baos.toByteArray();
  }

  private static void validateQuality(float quality) {
    if (quality < 0.0f || quality > 1.0f) {
      throw new IllegalArgumentException(
          "JPEG quality must be between 0.0 and 1.0, got: " + quality);
    }
  }
}
