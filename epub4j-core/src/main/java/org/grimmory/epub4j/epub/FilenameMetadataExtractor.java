/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts book metadata from filename patterns commonly used in ebook collections. Supports
 * patterns like:
 *
 * <ul>
 *   <li>"Author Name - Book Title (2024).epub"
 *   <li>"Series Name v01 - Book Title.epub"
 *   <li>"Book Title [Author Name].epub"
 *   <li>"Author - Series Name 01 - Book Title.epub"
 * </ul>
 */
public class FilenameMetadataExtractor {

  // Pattern: "Author - Title (Year).epub"
  private static final Pattern AUTHOR_TITLE_YEAR =
      Pattern.compile("^(.+?)\\s*-\\s*(.+?)\\s*(?:\\((\\d{4})\\))?\\s*\\.\\w+$");

  // Pattern: "Series vNN - Title.epub" or "Series #NN - Title.epub"
  private static final Pattern SERIES_VOLUME_TITLE =
      Pattern.compile(
          "^(.+?)\\s+(?:v|#|Vol\\.?\\s*)(\\d+(?:\\.\\d+)?)\\s*(?:-\\s*(.+?))?\\s*\\.\\w+$",
          Pattern.CASE_INSENSITIVE);

  // Pattern: "Title [Author].epub"
  private static final Pattern TITLE_BRACKET_AUTHOR =
      Pattern.compile("^(.+?)\\s*\\[(.+?)\\]\\s*\\.\\w+$");

  // Pattern: "Author - Series NN - Title.epub"
  private static final Pattern AUTHOR_SERIES_NUM_TITLE =
      Pattern.compile("^(.+?)\\s*-\\s*(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*-\\s*(.+?)\\s*\\.\\w+$");

  /**
   * Metadata extracted from a filename.
   *
   * @param title the book title, if detected
   * @param author the author name, if detected
   * @param series the series name, if detected
   * @param seriesNumber the volume/series number, if detected
   * @param year the publication year, if detected
   */
  public record FilenameMetadata(
      Optional<String> title,
      Optional<String> author,
      Optional<String> series,
      Optional<Float> seriesNumber,
      Optional<String> year) {
    /**
     * Whether any metadata field was successfully extracted.
     *
     * @return true if at least one of title, author, or series is present
     */
    public boolean hasAny() {
      return title.isPresent() || author.isPresent() || series.isPresent();
    }
  }

  /**
   * Extract metadata from a filename path.
   *
   * @param filePath the path to the EPUB file
   * @return extracted metadata
   */
  public static FilenameMetadata extract(Path filePath) {
    if (filePath == null) {
      return empty();
    }
    Path fileName = filePath.getFileName();
    if (fileName == null) {
      return empty();
    }
    String filename = fileName.toString();
    return extract(filename);
  }

  /**
   * Extract metadata from a filename string. Tries patterns in order of specificity, returning the
   * first match.
   *
   * @param filename the filename (with extension)
   * @return extracted metadata, or empty metadata if no pattern matched
   */
  public static FilenameMetadata extract(String filename) {
    if (filename == null || filename.isBlank()) {
      return empty();
    }

    // Try patterns in order of specificity

    // Pattern 4: Author - Series NN - Title
    Matcher m = AUTHOR_SERIES_NUM_TITLE.matcher(filename);
    if (m.matches()) {
      return new FilenameMetadata(
          Optional.of(m.group(4).trim()),
          Optional.of(m.group(1).trim()),
          Optional.of(m.group(2).trim()),
          parseFloat(m.group(3)),
          Optional.empty());
    }

    // Pattern 2: Series vNN - Title
    m = SERIES_VOLUME_TITLE.matcher(filename);
    if (m.matches()) {
      String title = m.group(3) != null ? m.group(3).trim() : m.group(1).trim();
      return new FilenameMetadata(
          Optional.of(title),
          Optional.empty(),
          Optional.of(m.group(1).trim()),
          parseFloat(m.group(2)),
          Optional.empty());
    }

    // Pattern 3: Title [Author]
    m = TITLE_BRACKET_AUTHOR.matcher(filename);
    if (m.matches()) {
      return new FilenameMetadata(
          Optional.of(m.group(1).trim()),
          Optional.of(m.group(2).trim()),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }

    // Pattern 1: Author - Title (Year) -- most common
    m = AUTHOR_TITLE_YEAR.matcher(filename);
    if (m.matches()) {
      return new FilenameMetadata(
          Optional.of(m.group(2).trim()),
          Optional.of(m.group(1).trim()),
          Optional.empty(),
          Optional.empty(),
          m.group(3) != null ? Optional.of(m.group(3)) : Optional.empty());
    }

    return empty();
  }

  private static FilenameMetadata empty() {
    return new FilenameMetadata(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static Optional<Float> parseFloat(String s) {
    try {
      return Optional.of(Float.parseFloat(s));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
