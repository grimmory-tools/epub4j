/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.archive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.grimmory.comic4j.error.ComicError;

/**
 * Reader for CBC (Comic Book Collection) format. A CBC file is a ZIP archive containing:
 *
 * <ul>
 *   <li>Multiple comic archives (CBZ, CBR, CB7)
 *   <li>An optional {@code comics.txt} manifest listing the reading order
 * </ul>
 *
 * <p>If {@code comics.txt} is present, its lines define the reading order. Otherwise, contained
 * archives are listed in natural sort order.
 */
public final class CbcReader {

  public static final String MANIFEST_NAME = "comics.txt";

  private CbcReader() {}

  /**
   * Represents an entry in a CBC collection.
   *
   * @param name the archive entry name within the CBC
   * @param title display title (from manifest or derived from filename)
   */
  public record CbcEntry(String name, String title) {}

  /**
   * Reads the manifest of a CBC file, returning the ordered list of contained comics.
   *
   * @param cbcPath path to the .cbc file
   * @return ordered list of comic entries
   */
  public static List<CbcEntry> readManifest(Path cbcPath) {
    validateCbc(cbcPath);

    try (ZipFile zip = new ZipFile(cbcPath.toFile())) {
      // Try reading comics.txt manifest first
      ZipEntry manifestEntry = zip.getEntry(MANIFEST_NAME);
      if (manifestEntry != null) {
        return parseManifest(zip, manifestEntry);
      }

      // Fallback: list all archive entries in natural sort order
      return listArchiveEntries(zip);
    } catch (IOException e) {
      throw ComicError.ERR_C003.exception(cbcPath.toString(), e);
    }
  }

  /**
   * Extracts a contained comic archive from the CBC to a temporary directory.
   *
   * @param cbcPath path to the .cbc file
   * @param entryName name of the contained archive
   * @param outputDir directory to extract to
   * @return path to the extracted archive
   */
  public static Path extractComic(Path cbcPath, String entryName, Path outputDir) {
    validateCbc(cbcPath);

    try (ZipFile zip = new ZipFile(cbcPath.toFile())) {
      ZipEntry entry = zip.getEntry(entryName);
      if (entry == null) {
        throw ComicError.ERR_C021.exception(entryName);
      }

      Files.createDirectories(outputDir);
      Path outputFile = outputDir.resolve(sanitizeFilename(entryName));

      try (InputStream is = zip.getInputStream(entry)) {
        Files.copy(is, outputFile);
      }
      return outputFile;
    } catch (IOException e) {
      throw ComicError.ERR_C007.exception(entryName, e);
    }
  }

  /** Returns true if the path appears to be a CBC file (by extension). */
  public static boolean isCbc(Path path) {
    if (path == null) return false;
    Path fileName = path.getFileName();
    if (fileName == null) return false;
    String name = fileName.toString().toLowerCase();
    return name.endsWith(".cbc");
  }

  private static List<CbcEntry> parseManifest(ZipFile zip, ZipEntry manifestEntry)
      throws IOException {
    List<CbcEntry> entries = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.strip();
        if (line.isEmpty() || line.startsWith("#")) continue;

        // Format: "filename:Title" or just "filename"
        int colonIndex = line.indexOf(':');
        String name, title;
        if (colonIndex > 0) {
          name = line.substring(0, colonIndex).strip();
          title = line.substring(colonIndex + 1).strip();
        } else {
          name = line;
          title = deriveTitle(name);
        }

        // Verify entry exists in archive; fail fast if it does not
        if (zip.getEntry(name) == null) {
          throw ComicError.ERR_C041.exception(name);
        }
        entries.add(new CbcEntry(name, title));
      }
    }
    return List.copyOf(entries);
  }

  private static List<CbcEntry> listArchiveEntries(ZipFile zip) {
    List<String> names = new ArrayList<>();
    var entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      if (entry.isDirectory()) continue;
      if (isArchiveFile(name)) {
        names.add(name);
      }
    }
    names.sort(NaturalSortComparator.INSTANCE);

    return names.stream().map(name -> new CbcEntry(name, deriveTitle(name))).toList();
  }

  private static boolean isArchiveFile(String name) {
    String lower = name.toLowerCase();
    return lower.endsWith(".cbz")
        || lower.endsWith(".cbr")
        || lower.endsWith(".cb7")
        || lower.endsWith(".cbt")
        || lower.endsWith(".zip")
        || lower.endsWith(".rar")
        || lower.endsWith(".7z");
  }

  private static String deriveTitle(String filename) {
    // Strip path and extension
    int lastSlash = filename.lastIndexOf('/');
    String name = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static String sanitizeFilename(String name) {
    // Use only the filename part, remove path separators
    int lastSlash = name.lastIndexOf('/');
    return lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
  }

  private static void validateCbc(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      throw ComicError.ERR_C001.exception(path == null ? "null" : path.toString());
    }
  }
}
