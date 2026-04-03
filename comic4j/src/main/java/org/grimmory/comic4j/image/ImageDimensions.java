/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.image;

/**
 * Image dimensions extracted from file headers without full decode.
 *
 * @param width the image width in pixels
 * @param height the image height in pixels
 * @param wide true if the image is wider than it is tall (landscape orientation)
 */
public record ImageDimensions(int width, int height, boolean wide) {

  public ImageDimensions {
    if (width < 0 || height < 0) {
      throw new IllegalArgumentException(
          "Dimensions must be non-negative: %dx%d".formatted(width, height));
    }
  }

  public static ImageDimensions of(int width, int height) {
    return new ImageDimensions(width, height, width > height);
  }

  /** Returns the total pixel count (width * height). */
  public long pixelCount() {
    return (long) width * height;
  }
}
