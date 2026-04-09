/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.archive;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filters out non-content entries commonly found in comic archives: macOS resource forks, system
 * files, hidden files, and path traversal attempts.
 */
public final class EntryFilter {

  private static final Set<String> SYSTEM_FILES = Set.of(".ds_store", "thumbs.db", "desktop.ini");
  private static final Pattern PATH_SEPARATOR_PATTERN = Pattern.compile("[/\\\\]+");

  private EntryFilter() {}

  /** Returns true if the entry name represents valid content (not a system/hidden file). */
  public static boolean isContentEntry(String entryName) {
    if (entryName == null || entryName.isBlank()) {
      return false;
    }

    // Reject path traversal: any path segment equal to ".."
    for (String segment : PATH_SEPARATOR_PATTERN.split(entryName)) {
      if ("..".equals(segment)) {
        return false;
      }
    }

    // Reject __MACOSX resource fork directory
    if (entryName.startsWith("__MACOSX/") || "__MACOSX".equals(entryName)) {
      return false;
    }

    // Get the filename portion (after last slash)
    String fileName = entryName;
    int lastSlash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
    if (lastSlash >= 0) {
      fileName = entryName.substring(lastSlash + 1);
    }

    // Reject empty filenames (directory entries)
    if (fileName.isEmpty()) {
      return false;
    }

    // Reject macOS resource fork files (._prefix)
    if (fileName.startsWith("._")) {
      return false;
    }

    // Reject hidden files (dotfiles)
    if (fileName.startsWith(".")) {
      return false;
    }

    // Reject known system files
    return !SYSTEM_FILES.contains(fileName.toLowerCase(Locale.ROOT));
  }
}
