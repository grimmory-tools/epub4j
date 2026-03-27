package org.grimmory.comic4j.archive;

import com.github.gotson.nightcompress.Archive;
import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.grimmory.comic4j.domain.ComicBook;
import org.grimmory.comic4j.domain.ComicInfo;
import org.grimmory.comic4j.error.ComicError;
import org.grimmory.comic4j.image.CoverDetector;
import org.grimmory.comic4j.image.ImageEntry;
import org.grimmory.comic4j.image.ImageExtractor;
import org.grimmory.comic4j.xml.ComicInfoReader;

/**
 * Primary entry point for reading comic book archives. Supports CBZ (ZIP), CBR (RAR4/5), CB7
 * (7-Zip), and CBT (TAR) formats. All archive reading is backed by NightCompress/libarchive via
 * Panama FFM.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Read full book with metadata and page list
 * ComicBook book = ComicArchiveReader.readBook(path);
 *
 * // Extract just the metadata
 * ComicInfo info = ComicArchiveReader.readComicInfo(path);
 *
 * // List image pages
 * List<ImageEntry> pages = ComicArchiveReader.listImages(path);
 *
 * // Extract a specific page
 * byte[] imageData = ComicArchiveReader.extractImage(path, 0);
 *
 * // Extract the cover
 * byte[] cover = ComicArchiveReader.extractCover(path);
 * }</pre>
 */
public final class ComicArchiveReader {

  private static final System.Logger LOG = System.getLogger(ComicArchiveReader.class.getName());

  private ComicArchiveReader() {}

  /** Returns true if the NightCompress/libarchive native library is available. */
  public static boolean isAvailable() {
    return Archive.isAvailable();
  }

  /**
   * Reads a complete comic book: format detection, metadata, and page listing. Degrades gracefully
   * if metadata or page listing partially fails.
   */
  public static ComicBook readBook(Path path) {
    validatePath(path);

    ArchiveFormat format = ArchiveDetector.detect(path);
    ComicInfo comicInfo = null;
    try {
      comicInfo = readComicInfoInternal(path);
    } catch (Exception e) {
      LOG.log(
          System.Logger.Level.DEBUG,
          "ComicInfo extraction failed, continuing with partial book",
          e);
    }
    List<ImageEntry> pages;
    try {
      pages = ImageExtractor.listImageEntries(path);
    } catch (Exception e) {
      pages = List.of();
    }
    List<String> otherEntries;
    try {
      otherEntries = ImageExtractor.listOtherEntries(path);
    } catch (Exception e) {
      otherEntries = List.of();
    }

    return new ComicBook(path, format, comicInfo, pages, otherEntries);
  }

  /** Lists all image entries in the archive, naturally sorted. */
  public static List<ImageEntry> listImages(Path path) {
    validatePath(path);
    return ImageExtractor.listImageEntries(path);
  }

  /**
   * Reads ComicInfo.xml from the archive.
   *
   * @return the parsed ComicInfo, or null if the archive has no ComicInfo.xml
   */
  public static ComicInfo readComicInfo(Path path) {
    validatePath(path);
    return readComicInfoInternal(path);
  }

  /** Extracts a single image by page index (0-based). */
  public static byte[] extractImage(Path path, int pageIndex) {
    validatePath(path);
    return ImageExtractor.extractImage(path, pageIndex);
  }

  /** Extracts a single image by entry name. */
  public static byte[] extractImage(Path path, String entryName) {
    validatePath(path);
    return ImageExtractor.extractImage(path, entryName);
  }

  /**
   * Returns an InputStream for the given image entry. Caller is responsible for closing the stream.
   */
  public static InputStream getImageStream(Path path, String entryName) {
    validatePath(path);
    return ImageExtractor.streamImage(path, entryName);
  }

  /**
   * Extracts the cover image from the archive. Uses ComicInfo page metadata if available, otherwise
   * uses the first image.
   *
   * @return the cover image bytes, or null if the archive has no images
   */
  public static byte[] extractCover(Path path) {
    validatePath(path);

    List<ImageEntry> images = ImageExtractor.listImageEntries(path);
    if (images.isEmpty()) {
      return null;
    }

    ComicInfo comicInfo = readComicInfoInternal(path);
    String coverEntry = CoverDetector.detectCoverEntryName(images, comicInfo);
    if (coverEntry == null) {
      return null;
    }

    return ImageExtractor.extractImage(path, coverEntry);
  }

  /** Detects the archive format of the file. */
  public static ArchiveFormat detectFormat(Path path) {
    return ArchiveDetector.detect(path);
  }

  // --- Internal ---

  private static ComicInfo readComicInfoInternal(Path path) {
    // Try exact name first, then case-insensitive search
    byte[] xmlBytes = ImageExtractor.extractEntry(path, ComicInfoReader.COMIC_INFO_FILENAME);
    if (xmlBytes != null) {
      return ComicInfoReader.read(xmlBytes);
    }

    // Case-insensitive fallback: iterate entries to find ComicInfo.xml
    try {
      List<ArchiveEntry> entries = Archive.getEntries(path);
      for (ArchiveEntry entry : entries) {
        String name = entry.getName();
        if (name == null || name.endsWith("/")) continue;
        // Check just the filename portion (ignore subdirectories)
        String fileName = name;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
          fileName = name.substring(lastSlash + 1);
        }
        if ("comicinfo.xml".equals(fileName.toLowerCase(Locale.ROOT))) {
          xmlBytes = ImageExtractor.extractEntry(path, name);
          if (xmlBytes != null) {
            return ComicInfoReader.read(xmlBytes);
          }
        }
      }
    } catch (LibArchiveException e) {
      throw ComicError.ERR_C003.exception(path.toString(), e);
    }

    return null;
  }

  private static void validatePath(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      throw ComicError.ERR_C001.exception(path == null ? "null" : path.toString());
    }
  }
}
