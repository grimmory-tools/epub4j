/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.image;

import org.grimmory.comic4j.domain.ImageFormat;

public record ImageEntry(
    String name, String displayName, long size, int index, ImageFormat format) {

  public static ImageEntry of(String name, long size, int index) {
    String displayName = extractDisplayName(name);
    ImageFormat format = ImageFormat.fromFileName(name);
    return new ImageEntry(name, displayName, size, index, format);
  }

  private static String extractDisplayName(String name) {
    if (name == null) return "";
    int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    String fileName = lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }
}
