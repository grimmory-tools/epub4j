/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.image;

import com.github.gotson.nightcompress.Archive;
import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.grimmory.comic4j.archive.EntryFilter;
import org.grimmory.comic4j.archive.NaturalSortComparator;
import org.grimmory.comic4j.domain.ImageFormat;
import org.grimmory.comic4j.error.ComicError;

/**
 * Extracts images from comic archives using NightCompress/libarchive (FFM). Handles entry
 * filtering, natural sort ordering, and format detection.
 */
public final class ImageExtractor {

  private static final System.Logger LOG = System.getLogger(ImageExtractor.class.getName());

  private ImageExtractor() {}

  /** Lists all image entries in the archive, filtered and naturally sorted. */
  public static List<ImageEntry> listImageEntries(Path archivePath) {
    validatePath(archivePath);

    List<ArchiveEntry> entries;
    try {
      entries = Archive.getEntries(archivePath);
    } catch (LibArchiveException e) {
      throw ComicError.ERR_C003.exception(archivePath.toString(), e);
    }

    List<ImageEntry> images = new ArrayList<>();
    for (ArchiveEntry entry : entries) {
      String name = entry.getName();
      if (name == null || name.endsWith("/")) continue;
      if (!EntryFilter.isContentEntry(name)) continue;
      if (!ImageFormat.isImageFileName(name)) continue;
      long size = entry.getSize() != null ? entry.getSize() : 0;
      images.add(ImageEntry.of(name, size, 0));
    }

    images.sort((a, b) -> NaturalSortComparator.INSTANCE.compare(a.name(), b.name()));

    // Assign sequential indices matching the new sort order
    List<ImageEntry> indexed = new ArrayList<>(images.size());
    for (int i = 0; i < images.size(); i++) {
      ImageEntry img = images.get(i);
      indexed.add(new ImageEntry(img.name(), img.displayName(), img.size(), i, img.format()));
    }
    return List.copyOf(indexed);
  }

  /** Lists all non-image content entries in the archive. */
  public static List<String> listOtherEntries(Path archivePath) {
    validatePath(archivePath);

    List<ArchiveEntry> entries;
    try {
      entries = Archive.getEntries(archivePath);
    } catch (LibArchiveException e) {
      throw ComicError.ERR_C003.exception(archivePath.toString(), e);
    }

    List<String> others = new ArrayList<>();
    for (ArchiveEntry entry : entries) {
      String name = entry.getName();
      if (name == null || name.endsWith("/")) continue;
      if (!EntryFilter.isContentEntry(name)) continue;
      if (ImageFormat.isImageFileName(name)) continue;
      others.add(name);
    }
    return List.copyOf(others);
  }

  /**
   * Extracts a single image by entry name.
   *
   * @return the image bytes
   */
  public static byte[] extractImage(Path archivePath, String entryName) {
    validatePath(archivePath);

    try (InputStream is = Archive.getInputStream(archivePath, entryName)) {
      if (is == null) {
        throw ComicError.ERR_C021.exception(entryName);
      }
      return is.readAllBytes();
    } catch (LibArchiveException | IOException e) {
      throw ComicError.ERR_C007.exception(entryName, e);
    }
  }

  /** Extracts a single image by page index (0-based). */
  public static byte[] extractImage(Path archivePath, int pageIndex) {
    List<ImageEntry> images = listImageEntries(archivePath);
    if (pageIndex < 0 || pageIndex >= images.size()) {
      throw ComicError.ERR_C020.exception(
          "index " + pageIndex + ", archive has " + images.size() + " pages");
    }
    return extractImage(archivePath, images.get(pageIndex).name());
  }

  /**
   * Returns an InputStream for the given entry name. Caller is responsible for closing the stream.
   */
  public static InputStream streamImage(Path archivePath, String entryName) {
    validatePath(archivePath);

    try {
      InputStream is = Archive.getInputStream(archivePath, entryName);
      if (is == null) {
        throw ComicError.ERR_C021.exception(entryName);
      }
      return is;
    } catch (LibArchiveException | IOException e) {
      throw ComicError.ERR_C007.exception(entryName, e);
    }
  }

  /**
   * Extracts all images to the specified output directory, using flat filenames (no subdirectories)
   * with natural sort order. Returns the list of extracted file paths.
   */
  public static List<Path> extractAllImages(Path archivePath, Path outputDir) {
    validatePath(archivePath);

    List<ImageEntry> images = listImageEntries(archivePath);
    if (images.isEmpty()) {
      throw ComicError.ERR_C006.exception(archivePath.toString());
    }

    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw ComicError.ERR_C007.exception("Failed to create output directory", e);
    }

    Map<String, ImageEntry> imagesByName = new HashMap<>(images.size());
    for (var img : images) {
      imagesByName.put(img.name(), img);
    }

    List<Path> extracted = new ArrayList<>(images.size());

    try (var archive = new Archive(archivePath)) {
      ArchiveEntry entry;
      while ((entry = archive.getNextEntry()) != null) {
        String name = entry.getName();

        var imageEntry = imagesByName.get(name);
        if (imageEntry == null) continue;

        // Per-entry recovery: skip corrupted entries instead of aborting
        try {
          String ext = getExtension(name);
          String outputName = String.format("%04d%s", imageEntry.index(), ext);
          Path outputFile = outputDir.resolve(outputName);

          try (InputStream is = archive.getInputStream();
              OutputStream os = Files.newOutputStream(outputFile)) {
            is.transferTo(os);
          }
          extracted.add(outputFile);
        } catch (IOException e) {
          LOG.log(System.Logger.Level.DEBUG, "Skipping unreadable archive entry: " + name, e);
        }
      }
    } catch (LibArchiveException e) {
      throw ComicError.ERR_C007.exception("Failed to extract images", e);
    }

    extracted.sort(
        Comparator.comparing(p -> p.getFileName().toString(), NaturalSortComparator.INSTANCE));
    return List.copyOf(extracted);
  }

  /**
   * Extracts a single named entry as raw bytes (not limited to images). Used internally for
   * ComicInfo.xml extraction.
   *
   * @return the entry bytes, or null if not found
   */
  public static byte[] extractEntry(Path archivePath, String entryName) {
    validatePath(archivePath);

    try (InputStream is = Archive.getInputStream(archivePath, entryName)) {
      return is != null ? is.readAllBytes() : null;
    } catch (LibArchiveException | IOException e) {
      throw ComicError.ERR_C007.exception(entryName, e);
    }
  }

  private static void validatePath(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      throw ComicError.ERR_C001.exception(path == null ? "null" : path.toString());
    }
  }

  private static String getExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(dot) : "";
  }
}
