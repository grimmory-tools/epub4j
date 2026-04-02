/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.image.processing;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Configurable comic page processing pipeline. Applies a sequence of image operations based on
 * {@link ComicProcessingOptions}.
 *
 * <p>Processing order (when enabled):
 *
 * <ol>
 *   <li>Landscape splitting (produces 1 or 2 pages)
 *   <li>Border removal
 *   <li>Normalize (auto-levels)
 *   <li>Sharpen
 *   <li>Despeckle
 *   <li>Grayscale conversion
 *   <li>Color quantization
 *   <li>Resize
 * </ol>
 *
 * <p>All intermediate pixel operations use FFM off-heap memory via {@link NativePixelBuffer}.
 */
public final class PageProcessor {

  private final ComicProcessingOptions options;

  public PageProcessor(ComicProcessingOptions options) {
    this.options = options;
  }

  /**
   * Processes a single page image through the configured pipeline. May return multiple images if
   * landscape splitting is enabled.
   *
   * @param image the source page image
   * @return list of processed page images (1 or 2)
   */
  public List<BufferedImage> process(BufferedImage image) {
    try (var input = NativePixelBuffer.fromImage(image)) {
      if (options.splitLandscape() && LandscapeSplitter.isLandscape(input)) {
        List<NativePixelBuffer> pages = LandscapeSplitter.split(input, options.readingDirection());

        List<BufferedImage> results = new ArrayList<>(pages.size());
        for (var page : pages) {
          try (page) {
            results.add(processBuffer(page));
          }
        }
        return results;
      } else {
        return List.of(processBuffer(input));
      }
    }
  }

  /** Processes a single page without landscape splitting. Always returns exactly one image. */
  public BufferedImage processSingle(BufferedImage image) {
    try (var input = NativePixelBuffer.fromImage(image)) {
      return processBuffer(input);
    }
  }

  /**
   * Applies all configured operations to the buffer and returns a BufferedImage. Handles
   * intermediate buffer lifecycle internally.
   */
  private BufferedImage processBuffer(NativePixelBuffer input) {
    NativePixelBuffer current = input;
    boolean currentOwned = false;

    try {
      // Border removal (produces new buffer)
      if (options.removeBorders()) {
        var trimmed =
            ImageOperations.removeBorders(
                current, options.borderTolerance(), options.borderMinContentPercent());
        if (trimmed != current) {
          if (currentOwned) {
            current.close();
          }
          current = trimmed;
          currentOwned = true;
        }
      }

      // In-place operations
      if (options.normalize()) ImageOperations.normalize(current);
      if (options.sharpen())
        ImageOperations.gaussianSharpen(current, options.sharpenSigma(), options.sharpenGain());
      if (options.despeckle()) ImageOperations.despeckle(current);
      if (options.grayscale()) ImageOperations.grayscale(current);
      if (options.quantize()) ImageOperations.quantize(current, options.quantizeLevels());

      // Resize (produces new buffer)
      if (options.resize()) {
        var resized =
            options.keepAspectRatio()
                ? ImageOperations.resizeFit(current, options.maxWidth(), options.maxHeight())
                : ImageOperations.resize(current, options.maxWidth(), options.maxHeight());
        if (resized != current) {
          if (currentOwned) {
            current.close();
          }
          current = resized;
          currentOwned = true;
        }
      }

      return current.toImage();
    } finally {
      if (currentOwned) {
        current.close();
      }
    }
  }

  /**
   * Processes multiple pages in parallel using virtual threads. Each page's pipeline runs
   * independently - ideal for large archives where single-threaded processing is the bottleneck.
   *
   * <p>Processes in sliding windows to bound peak memory - at most {@code maxConcurrency * 2} pages
   * have data in flight simultaneously. Each input image reference is released from the window
   * after its future completes so the GC can reclaim the source bitmap.
   *
   * <p>Results are returned in the same order as the input list. Each input image may produce 1 or
   * 2 output images (landscape splitting).
   *
   * @param images source page images in reading order
   * @param maxConcurrency upper bound on simultaneous pages to limit memory pressure
   * @return processed images, one list per input page
   */
  public List<List<BufferedImage>> processBatch(List<BufferedImage> images, int maxConcurrency) {
    if (maxConcurrency < 1) {
      throw new IllegalArgumentException("maxConcurrency must be >= 1, got: " + maxConcurrency);
    }
    if (images.isEmpty()) {
      return List.of();
    }
    if (images.size() == 1) {
      return List.of(process(images.getFirst()));
    }

    var semaphore = new Semaphore(maxConcurrency);
    List<List<BufferedImage>> allResults = new ArrayList<>(images.size());

    // Process in windows to bound peak memory  -  prevents all input + output
    // images from being live simultaneously
    int windowSize = maxConcurrency * 2;

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int start = 0; start < images.size(); start += windowSize) {
        int end = Math.min(start + windowSize, images.size());
        List<BufferedImage> window = images.subList(start, end);
        var futures = new ArrayList<Future<List<BufferedImage>>>(window.size());

        for (BufferedImage image : window) {
          futures.add(
              executor.submit(
                  () -> {
                    semaphore.acquireUninterruptibly();
                    try {
                      return process(image);
                    } finally {
                      semaphore.release();
                    }
                  }));
        }

        // Collect results for this window before starting next
        for (var future : futures) {
          try {
            allResults.add(future.get());
          } catch (ExecutionException e) {
            throw new RuntimeException("Page processing failed", e.getCause());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Page processing interrupted", e);
          }
        }
      }
    }
    return allResults;
  }
}
