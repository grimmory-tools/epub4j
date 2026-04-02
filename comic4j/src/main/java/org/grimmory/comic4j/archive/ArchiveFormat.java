/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.archive;

import java.util.Locale;
import java.util.Set;

public enum ArchiveFormat {
  ZIP(Set.of("cbz", "zip")),
  RAR4(Set.of("cbr", "rar")),
  RAR5(Set.of("cbr", "rar")),
  SEVEN_ZIP(Set.of("cb7", "7z")),
  TAR(Set.of("cbt", "tar", "tar.gz", "tgz")),
  UNKNOWN(Set.of());

  private final Set<String> extensions;

  ArchiveFormat(Set<String> extensions) {
    this.extensions = Set.copyOf(extensions);
  }

  public Set<String> extensions() {
    return Set.copyOf(extensions);
  }

  public boolean isWritable() {
    return this == ZIP;
  }

  public static ArchiveFormat fromExtension(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return UNKNOWN;
    }
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
      return TAR;
    }
    int dot = lower.lastIndexOf('.');
    if (dot < 0 || dot == lower.length() - 1) {
      return UNKNOWN;
    }
    return switch (lower.substring(dot + 1)) {
      case "cbz", "zip" -> ZIP;
      case "cbr", "rar" -> RAR4;
      case "cb7", "7z" -> SEVEN_ZIP;
      case "cbt", "tar" -> TAR;
      default -> UNKNOWN;
    };
  }
}
