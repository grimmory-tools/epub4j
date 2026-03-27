package org.grimmory.epub4j.archive;

import com.github.gotson.nightcompress.Archive;
import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import com.github.gotson.nightcompress.ReadSupportCompression;
import com.github.gotson.nightcompress.ReadSupportFilter;
import com.github.gotson.nightcompress.ReadSupportFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Archive reader wrapping NightCompress/libarchive for multi-format archive support. Supports ZIP,
 * RAR (including RAR5), RAR3, 7z, TAR, and many more formats.
 *
 * <p>Usage for iterating entries:
 *
 * <pre>{@code
 * try (var reader = ArchiveReader.open(path)) {
 *     ArchiveEntry entry;
 *     while ((entry = reader.nextEntry()) != null) {
 *         try (InputStream is = reader.getEntryInputStream()) {
 *             // process entry data
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Usage for extracting a single entry:
 *
 * <pre>{@code
 * byte[] data = ArchiveReader.extractEntry(path, "META-INF/container.xml");
 * }</pre>
 */
public class ArchiveReader implements AutoCloseable {

  private final Archive archive;

  private ArchiveReader(Archive archive) {
    this.archive = archive;
  }

  /** Opens an archive at the given path, auto-detecting format and compression. */
  public static ArchiveReader open(Path path) throws LibArchiveException {
    return new ArchiveReader(new Archive(path));
  }

  /**
   * Opens an archive with selective format support. Use this to restrict which formats are tried,
   * avoiding false positives (e.g., nested archives being misinterpreted).
   */
  public static ArchiveReader open(
      Path path,
      Set<ReadSupportCompression> compressions,
      Set<ReadSupportFilter> filters,
      Set<ReadSupportFormat> formats)
      throws LibArchiveException {
    return new ArchiveReader(new Archive(path, compressions, filters, formats));
  }

  /** Opens an archive for ZIP-only reading (EPUB files). */
  public static ArchiveReader openZip(Path path) throws LibArchiveException {
    return open(
        path,
        Set.of(ReadSupportCompression.ALL),
        Set.of(ReadSupportFilter.ALL),
        Set.of(ReadSupportFormat.ZIP));
  }

  /** Returns true if the native libarchive library is available at runtime. */
  public static boolean isAvailable() {
    return Archive.isAvailable();
  }

  /** Lists all entries in the archive without extracting content. */
  public static List<ArchiveEntry> listEntries(Path path) throws LibArchiveException {
    return Archive.getEntries(path);
  }

  /** Lists entries with selective format support. */
  public static List<ArchiveEntry> listEntries(
      Path path,
      Set<ReadSupportCompression> compressions,
      Set<ReadSupportFilter> filters,
      Set<ReadSupportFormat> formats)
      throws LibArchiveException {
    return Archive.getEntries(path, compressions, filters, formats);
  }

  /**
   * Extracts a single named entry from the archive. Loads the entire entry into memory, so callers
   * should be aware of the memory impact for large entries. For streaming access to large entries,
   * use {@link #open(Path)} with {@link #nextEntry()} and {@link #getEntryInputStream()} instead.
   *
   * @return the entry data as a byte array, or null if the entry was not found
   */
  public static byte[] extractEntry(Path path, String entryName)
      throws LibArchiveException, IOException {
    try (InputStream is = Archive.getInputStream(path, entryName)) {
      return is != null ? is.readAllBytes() : null;
    }
  }

  /** Extracts a single named entry with selective format support. */
  public static byte[] extractEntry(
      Path path,
      Set<ReadSupportCompression> compressions,
      Set<ReadSupportFilter> filters,
      Set<ReadSupportFormat> formats,
      String entryName)
      throws LibArchiveException, IOException {
    try (InputStream is = Archive.getInputStream(path, compressions, filters, formats, entryName)) {
      return is != null ? is.readAllBytes() : null;
    }
  }

  /**
   * Advances to the next entry in the archive.
   *
   * @return the next entry, or null if there are no more entries
   */
  public ArchiveEntry nextEntry() throws LibArchiveException {
    return archive.getNextEntry();
  }

  /**
   * Returns an InputStream for the current entry's data. Must be called after {@link #nextEntry()}.
   */
  public InputStream getEntryInputStream() throws IOException, LibArchiveException {
    return archive.getInputStream();
  }

  @Override
  public void close() {
    archive.close();
  }
}
