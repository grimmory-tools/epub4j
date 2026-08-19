/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.optimize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Compresses bitmap images (JPEG, PNG) in an EPUB. Uses native libjpeg-turbo/libpng when available,
 * falling back to Java ImageIO.
 *
 * <p>Safety rule: if the compressed output is larger than the original, the original is kept. This
 * mirrors Calibre's optimization behavior.
 */
public class ImageCompressor implements BookProcessor {

  private static final System.Logger log = System.getLogger(ImageCompressor.class.getName());

  private final OptimizationConfig config;

  public ImageCompressor(OptimizationConfig config) {
    this.config = config;
  }

  @Override
  public Book processBook(Book book) {
    if (!config.compressImages()) {
      return book;
    }
    for (Resource resource : book.getResources().getAll()) {
      MediaType mt = resource.getMediaType();
      if (mt == null || !config.imageMediaTypes().contains(mt)) {
        continue;
      }
      try {
        compressResource(resource);
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Image compression skipped for " + resource.getHref() + ": " + e.getMessage());
      }
    }
    return book;
  }

  private void compressResource(Resource resource) throws IOException {
    byte[] original = resource.getData();
    if (original == null || original.length == 0) {
      return;
    }

    byte[] compressed;
    if (resource.getMediaType() == MediaTypes.JPG) {
      compressed = compressJpeg(original, config.jpegQuality());
    } else if (resource.getMediaType() == MediaTypes.PNG) {
      compressed = compressPng(original);
    } else {
      return;
    }

    if (compressed != null && compressed.length < original.length) {
      resource.setData(compressed);
      log.log(
          System.Logger.Level.DEBUG,
          "Compressed "
              + resource.getHref()
              + ": "
              + original.length
              + " -> "
              + compressed.length
              + " bytes");
    }
  }

  private byte[] compressJpeg(byte[] data, int quality) throws IOException {
    return compressJpegImageIO(data, quality);
  }

  private byte[] compressPng(byte[] data) throws IOException {
    return compressPngImageIO(data);
  }

  private static byte[] compressJpegImageIO(byte[] data, int quality) throws IOException {
    var image = ImageIO.read(new ByteArrayInputStream(data));
    if (image == null) {
      return null;
    }

    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      return null;
    }
    ImageWriter writer = writers.next();
    try {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality / 100f);
      if (param.canWriteProgressive()) {
        param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
      try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), param);
      }
      return baos.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  private static byte[] compressPngImageIO(byte[] data) throws IOException {
    // PNG re-encoding via ImageIO applies default optimizations (filter selection, compression).
    // For deeper optimization, the native libpng binding strips ancillary chunks and
    // tries harder filter/compression combos.
    var image = ImageIO.read(new ByteArrayInputStream(data));
    if (image == null) {
      return null;
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
    if (!ImageIO.write(image, "png", baos)) {
      return null;
    }
    return baos.toByteArray();
  }
}
