/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects archive format using magic bytes with extension fallback. Supports ZIP, RAR4, RAR5,
 * 7-Zip, and TAR formats.
 */
public final class ArchiveDetector {

  // Magic byte signatures
  private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
  private static final byte[] RAR4_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00};
  private static final byte[] RAR5_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00};
  private static final byte[] SEVEN_ZIP_MAGIC = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
  private static final byte[] TAR_MAGIC = {'u', 's', 't', 'a', 'r'};
  private static final int TAR_MAGIC_OFFSET = 257;

  // Maximum bytes needed for detection
  private static final int HEADER_SIZE = TAR_MAGIC_OFFSET + TAR_MAGIC.length;

  private ArchiveDetector() {}

  /**
   * Detects the archive format of the file at the given path. First tries magic byte detection,
   * then falls back to extension.
   */
  public static ArchiveFormat detect(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      return ArchiveFormat.UNKNOWN;
    }

    ArchiveFormat detected = detectByMagicBytes(path);
    if (detected != ArchiveFormat.UNKNOWN) {
      return detected;
    }

    Path fileName = path.getFileName();
    if (fileName == null) {
      return ArchiveFormat.UNKNOWN;
    }
    return ArchiveFormat.fromExtension(fileName.toString());
  }

  /** Detects archive format by reading magic bytes from the file header. */
  public static ArchiveFormat detectByMagicBytes(Path path) {
    byte[] header = new byte[HEADER_SIZE];
    int bytesRead;

    try (InputStream is = Files.newInputStream(path)) {
      bytesRead = is.readNBytes(header, 0, HEADER_SIZE);
    } catch (IOException e) {
      return ArchiveFormat.UNKNOWN;
    }

    if (bytesRead < 4) {
      return ArchiveFormat.UNKNOWN;
    }

    // RAR5 must be checked before RAR4 (longer signature)
    if (bytesRead >= RAR5_MAGIC.length && startsWith(header, RAR5_MAGIC)) {
      return ArchiveFormat.RAR5;
    }
    if (bytesRead >= RAR4_MAGIC.length && startsWith(header, RAR4_MAGIC)) {
      return ArchiveFormat.RAR4;
    }
    if (startsWith(header, ZIP_MAGIC)) {
      return ArchiveFormat.ZIP;
    }
    if (bytesRead >= SEVEN_ZIP_MAGIC.length && startsWith(header, SEVEN_ZIP_MAGIC)) {
      return ArchiveFormat.SEVEN_ZIP;
    }
    // TAR magic at offset 257
    if (bytesRead >= TAR_MAGIC_OFFSET + TAR_MAGIC.length
        && matchesAt(header, TAR_MAGIC_OFFSET, TAR_MAGIC)) {
      return ArchiveFormat.TAR;
    }

    return ArchiveFormat.UNKNOWN;
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    return matchesAt(data, 0, prefix);
  }

  private static boolean matchesAt(byte[] data, int offset, byte[] pattern) {
    if (offset + pattern.length > data.length) return false;
    for (int i = 0; i < pattern.length; i++) {
      if (data[offset + i] != pattern[i]) {
        return false;
      }
    }
    return true;
  }
}
