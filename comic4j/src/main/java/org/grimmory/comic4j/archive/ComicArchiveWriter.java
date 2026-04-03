/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.archive;

import com.github.gotson.nightcompress.Archive;
import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.grimmory.comic4j.domain.ComicInfo;
import org.grimmory.comic4j.error.ComicError;
import org.grimmory.comic4j.xml.ComicInfoReader;
import org.grimmory.comic4j.xml.ComicInfoWriter;

/**
 * Writes ComicInfo.xml metadata back to comic archives.
 *
 * <p>ZIP/CBZ archives support in-place update (atomic replacement via temp file). RAR/7z/TAR
 * archives are converted to ZIP/CBZ format since they don't support modification.
 */
public final class ComicArchiveWriter {

  private ComicArchiveWriter() {}

  /** Returns true if the given format supports in-place metadata writing. */
  public static boolean supportsInPlaceWrite(ArchiveFormat format) {
    return format == ArchiveFormat.ZIP;
  }

  /**
   * Writes ComicInfo.xml into an existing ZIP/CBZ archive. Creates a backup (.bak) before
   * modification, uses atomic replacement.
   *
   * @param zipPath the path to the ZIP/CBZ archive
   * @param info the ComicInfo to write
   * @throws org.grimmory.comic4j.error.ComicException if the format doesn't support writing or IO
   *     fails
   */
  public static void writeToZip(Path zipPath, ComicInfo info) {
    writeToZip(zipPath, info, null);
  }

  /**
   * Writes ComicInfo.xml into an existing ZIP/CBZ archive. Optionally creates a backup in the
   * specified directory before modification.
   *
   * @param zipPath the path to the ZIP/CBZ archive
   * @param info the ComicInfo to write
   * @param backupDir the directory to store a backup copy, or null to skip backup
   * @throws org.grimmory.comic4j.error.ComicException if the format doesn't support writing or IO
   *     fails
   */
  public static void writeToZip(Path zipPath, ComicInfo info, Path backupDir) {
    validateWritablePath(zipPath);

    ArchiveFormat format = ArchiveDetector.detect(zipPath);
    if (format != ArchiveFormat.ZIP) {
      throw ComicError.ERR_C009.exception(format.name());
    }

    // Create backup if requested
    if (backupDir != null) {
      try {
        Files.createDirectories(backupDir);
        Path backup = backupDir.resolve(zipPath.getFileName().toString() + ".bak");
        Files.copy(zipPath, backup, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw ComicError.ERR_C008.exception("Failed to create backup: " + zipPath, e);
      }
    }

    byte[] comicInfoXml = ComicInfoWriter.write(info);
    Path tempFile = null;

    try {
      // Write to temp file in same directory (for atomic rename)
      tempFile = Files.createTempFile(zipPath.getParent(), ".comic4j-", ".tmp");

      try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
        zos.setLevel(Deflater.DEFAULT_COMPRESSION);

        boolean comicInfoWritten = false;

        // Copy existing entries, replacing ComicInfo.xml
        try (Archive archive = new Archive(zipPath)) {
          ArchiveEntry entry;
          while ((entry = archive.getNextEntry()) != null) {
            String name = entry.getName();

            if (isComicInfoEntry(name)) {
              // Replace with new ComicInfo.xml
              writeZipEntry(zos, ComicInfoReader.COMIC_INFO_FILENAME, comicInfoXml);
              comicInfoWritten = true;
              // Skip reading the old entry data - just advance
              continue;
            }

            // Copy entry as-is
            ZipEntry zipEntry = new ZipEntry(name);
            if (entry.getModifiedTime() != null) {
              zipEntry.setLastModifiedTime(
                  java.nio.file.attribute.FileTime.from(entry.getModifiedTime()));
            }
            zos.putNextEntry(zipEntry);
            try (InputStream is = archive.getInputStream()) {
              is.transferTo(zos);
            }
            zos.closeEntry();
          }
        }

        // If ComicInfo.xml didn't exist, add it
        if (!comicInfoWritten) {
          writeZipEntry(zos, ComicInfoReader.COMIC_INFO_FILENAME, comicInfoXml);
        }
      }

      // Atomic replacement
      Files.move(
          tempFile, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      tempFile = null; // Prevent cleanup

    } catch (LibArchiveException | IOException e) {
      throw ComicError.ERR_C008.exception(zipPath.toString(), e);
    } finally {
      // Clean up temp file on failure
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
      }
    }
  }

  /**
   * Converts any comic archive to ZIP/CBZ format. The output file is placed in the specified
   * directory with .cbz extension.
   *
   * @param source the source archive (any supported format)
   * @param outputDir the directory for the output file
   * @return the path to the newly created CBZ file
   */
  public static Path convertToZip(Path source, Path outputDir) {
    return convertToZipWithInfo(source, outputDir, null);
  }

  /**
   * Converts any comic archive to ZIP/CBZ format with the given ComicInfo. If info is null, the
   * existing ComicInfo.xml (if any) is preserved.
   *
   * @param source the source archive
   * @param outputDir the directory for the output file
   * @param info the ComicInfo to embed (null to preserve existing)
   * @return the path to the newly created CBZ file
   */
  public static Path convertToZipWithInfo(Path source, Path outputDir, ComicInfo info) {
    validateReadablePath(source);

    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw ComicError.ERR_C008.exception("Failed to create output directory", e);
    }

    // Build output filename: same name with .cbz extension
    String baseName = source.getFileName().toString();
    int dot = baseName.lastIndexOf('.');
    if (dot > 0) baseName = baseName.substring(0, dot);
    Path outputFile = outputDir.resolve(baseName + ".cbz");

    byte[] comicInfoXml = info != null ? ComicInfoWriter.write(info) : null;
    Path tempFile = null;

    try {
      tempFile = Files.createTempFile(outputDir, ".comic4j-", ".tmp");

      try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
        zos.setLevel(Deflater.DEFAULT_COMPRESSION);

        boolean comicInfoWritten = false;

        try (Archive archive = new Archive(source)) {
          ArchiveEntry entry;
          while ((entry = archive.getNextEntry()) != null) {
            String name = entry.getName();
            if (name == null || name.endsWith("/")) continue;

            if (isComicInfoEntry(name)) {
              if (comicInfoXml != null) {
                // Replace with provided ComicInfo
                writeZipEntry(zos, ComicInfoReader.COMIC_INFO_FILENAME, comicInfoXml);
              } else {
                // Preserve existing ComicInfo
                ZipEntry zipEntry = new ZipEntry(ComicInfoReader.COMIC_INFO_FILENAME);
                zos.putNextEntry(zipEntry);
                try (InputStream is = archive.getInputStream()) {
                  is.transferTo(zos);
                }
                zos.closeEntry();
              }
              comicInfoWritten = true;
              continue;
            }

            // Copy entry
            ZipEntry zipEntry = new ZipEntry(name);
            zos.putNextEntry(zipEntry);
            try (InputStream is = archive.getInputStream()) {
              is.transferTo(zos);
            }
            zos.closeEntry();
          }
        }

        // Add ComicInfo if it wasn't in the source and we have one to write
        if (!comicInfoWritten && comicInfoXml != null) {
          writeZipEntry(zos, ComicInfoReader.COMIC_INFO_FILENAME, comicInfoXml);
        }
      }

      Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
      tempFile = null;

      return outputFile;

    } catch (LibArchiveException | IOException e) {
      throw ComicError.ERR_C008.exception(source.toString(), e);
    } finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
      }
    }
  }

  // --- Helpers ---

  private static boolean isComicInfoEntry(String name) {
    if (name == null) return false;
    String fileName = name;
    int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (lastSlash >= 0) {
      fileName = name.substring(lastSlash + 1);
    }
    return "comicinfo.xml".equals(fileName.toLowerCase(Locale.ROOT));
  }

  private static void writeZipEntry(ZipOutputStream zos, String name, byte[] data)
      throws IOException {
    ZipEntry entry = new ZipEntry(name);
    zos.putNextEntry(entry);
    zos.write(data);
    zos.closeEntry();
  }

  private static void validateWritablePath(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      throw ComicError.ERR_C001.exception(path == null ? "null" : path.toString());
    }
    if (!Files.isWritable(path)) {
      throw ComicError.ERR_C008.exception("File is not writable: " + path);
    }
  }

  private static void validateReadablePath(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      throw ComicError.ERR_C001.exception(path == null ? "null" : path.toString());
    }
  }
}
