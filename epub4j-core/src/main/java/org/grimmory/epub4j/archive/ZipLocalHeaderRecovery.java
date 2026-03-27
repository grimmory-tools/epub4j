package org.grimmory.epub4j.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Recovers entries from ZIP archives with corrupted central directories by scanning for local file
 * header signatures ({@code PK\x03\x04}).
 *
 * <p>Many real-world EPUBs (especially from B&amp;N, older Kobo exports, and re-zipped files) have
 * damaged or missing central directory records. libarchive relies on the central directory and
 * fails on such files. This recovery path scans forward through the raw bytes, parsing each local
 * file header to extract filenames and data.
 *
 * <p>Limitations:
 *
 * <ul>
 *   <li>ZIP64 extensions are not supported (entries &gt; 4 GB)
 *   <li>Encrypted entries are skipped
 *   <li>Only STORE (0) and DEFLATE (8) compression methods are handled
 * </ul>
 */
public final class ZipLocalHeaderRecovery {

  private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
  private static final int LOCAL_HEADER_FIXED_SIZE = 30;
  private static final int METHOD_STORED = 0;
  private static final int METHOD_DEFLATED = 8;
  // Bit 0 = encrypted, Bit 3 = data descriptor follows
  private static final int FLAG_ENCRYPTED = 0x0001;
  private static final int FLAG_DATA_DESCRIPTOR = 0x0008;
  // Per-entry decompressed size limit  -  prevents zip bomb decompression
  private static final long DEFAULT_MAX_INFLATE_BYTES = 64L * 1024 * 1024;

  /**
   * A recovered archive entry with its raw data.
   *
   * @param name entry filename from the local header
   * @param data decompressed entry bytes
   * @param compressedSize original compressed size
   * @param uncompressedSize original uncompressed size
   */
  public record RecoveredEntry(
      String name, byte[] data, long compressedSize, long uncompressedSize) {}

  private ZipLocalHeaderRecovery() {}

  /**
   * Scans the archive file for local file headers and extracts all recoverable entries.
   *
   * @param archivePath path to the (possibly corrupted) ZIP/EPUB file
   * @return list of recovered entries; may be empty if no valid headers found
   * @throws IOException if the file cannot be read
   */
  public static List<RecoveredEntry> recover(Path archivePath) throws IOException {
    return recover(archivePath, Integer.MAX_VALUE);
  }

  /**
   * Scans the archive file for local file headers, up to {@code maxEntries}.
   *
   * @param archivePath path to the (possibly corrupted) ZIP/EPUB file
   * @param maxEntries stop after recovering this many entries
   * @return list of recovered entries
   * @throws IOException if the file cannot be read
   */
  public static List<RecoveredEntry> recover(Path archivePath, int maxEntries) throws IOException {
    return recover(archivePath, maxEntries, DEFAULT_MAX_INFLATE_BYTES);
  }

  /**
   * Scans the archive file for local file headers, up to {@code maxEntries}, with an explicit limit
   * on decompressed entry size.
   *
   * @param archivePath path to the (possibly corrupted) ZIP/EPUB file
   * @param maxEntries stop after recovering this many entries
   * @param maxInflateBytes per-entry decompressed size limit (zip bomb guard)
   * @return list of recovered entries
   * @throws IOException if the file cannot be read
   */
  public static List<RecoveredEntry> recover(Path archivePath, int maxEntries, long maxInflateBytes)
      throws IOException {
    List<RecoveredEntry> entries = new ArrayList<>();

    try (RandomAccessFile raf = new RandomAccessFile(archivePath.toFile(), "r");
        FileChannel channel = raf.getChannel()) {

      long fileSize = channel.size();
      if (fileSize < LOCAL_HEADER_FIXED_SIZE) {
        return entries;
      }

      if (fileSize > 512L * 1024 * 1024) {
        throw new IOException("File too large for local header recovery: " + fileSize + " bytes");
      }

      // Memory-map instead of heap-allocating  -  OS manages the pages,
      // avoids a contiguous heap allocation that can cause OOM
      MappedByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
      buf.order(ByteOrder.LITTLE_ENDIAN);

      long pos = 0;
      while (pos + LOCAL_HEADER_FIXED_SIZE <= fileSize && entries.size() < maxEntries) {
        pos = findNextSignature(buf, (int) pos);
        if (pos < 0) break;

        RecoveredEntry entry = tryParseEntry(buf, (int) pos, (int) fileSize, maxInflateBytes);
        if (entry != null) {
          entries.add(entry);
          // Advance past this entry's data
          int headerEnd =
              (int) pos
                  + LOCAL_HEADER_FIXED_SIZE
                  + getUnsignedShort(buf, (int) pos + 26) // filename length
                  + getUnsignedShort(buf, (int) pos + 28); // extra field length
          pos = headerEnd + entry.compressedSize();
        } else {
          // Skip this signature  -  might be a false positive in file data
          pos += 4;
        }
      }
    }

    return entries;
  }

  /** Scan forward from {@code start} for the next local file header signature. */
  private static long findNextSignature(ByteBuffer buf, int start) {
    int limit = buf.limit() - 3;
    for (int i = start; i < limit; i++) {
      if (buf.get(i) == 0x50
          && buf.get(i + 1) == 0x4B
          && buf.get(i + 2) == 0x03
          && buf.get(i + 3) == 0x04) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Attempts to parse a single entry at the given offset. Returns null if the header is invalid or
   * the entry cannot be extracted.
   */
  private static RecoveredEntry tryParseEntry(
      ByteBuffer buf, int offset, int fileSize, long maxInflateBytes) {
    if (offset + LOCAL_HEADER_FIXED_SIZE > fileSize) return null;

    int sig = buf.getInt(offset);
    if (sig != LOCAL_FILE_HEADER_SIGNATURE) return null;

    int flags = getUnsignedShort(buf, offset + 6);
    int method = getUnsignedShort(buf, offset + 8);
    long compressedSize = getUnsignedInt(buf, offset + 18);
    long uncompressedSize = getUnsignedInt(buf, offset + 22);
    int nameLen = getUnsignedShort(buf, offset + 26);
    int extraLen = getUnsignedShort(buf, offset + 28);

    // Skip encrypted entries  -  we can't decrypt them
    if ((flags & FLAG_ENCRYPTED) != 0) return null;

    // Only STORE and DEFLATE are common in EPUBs
    if (method != METHOD_STORED && method != METHOD_DEFLATED) return null;

    int dataOffset = offset + LOCAL_HEADER_FIXED_SIZE + nameLen + extraLen;

    // Data descriptor entries store sizes after the data  -  scan for next header
    if ((flags & FLAG_DATA_DESCRIPTOR) != 0) {
      compressedSize = scanForDataEnd(buf, dataOffset, fileSize);
      if (compressedSize < 0) return null;
    }

    if (dataOffset + compressedSize > fileSize) return null;
    if (nameLen <= 0 || nameLen > 1024) return null;

    // Extract filename
    byte[] nameBytes = new byte[nameLen];
    buf.position(offset + LOCAL_HEADER_FIXED_SIZE);
    buf.get(nameBytes);
    String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);

    // Reject path-traversal attempts
    if (!FilenameValidator.isSafeEntryName(name)) return null;

    // Skip directories
    if (name.endsWith("/")) return null;

    // Extract and decompress data
    byte[] compressedData = new byte[(int) compressedSize];
    buf.position(dataOffset);
    buf.get(compressedData);

    byte[] data;
    if (method == METHOD_STORED) {
      data = compressedData;
      uncompressedSize = compressedSize;
    } else {
      data = inflate(compressedData, (int) uncompressedSize, maxInflateBytes);
      if (data == null) return null;
      uncompressedSize = data.length;
    }

    return new RecoveredEntry(name, data, compressedSize, uncompressedSize);
  }

  /**
   * When the data descriptor flag is set, sizes aren't in the local header. Scan forward to find
   * the next PK signature or end of file to estimate the compressed data length.
   */
  private static long scanForDataEnd(ByteBuffer buf, int dataStart, int fileSize) {
    // Look for next PK\x03\x04 or PK\x01\x02 (central directory) signature
    int limit = fileSize - 3;
    for (int i = dataStart; i < limit; i++) {
      if (buf.get(i) == 0x50 && buf.get(i + 1) == 0x4B) {
        int third = buf.get(i + 2) & 0xFF;
        int fourth = buf.get(i + 3) & 0xFF;
        if ((third == 0x03 && fourth == 0x04) || (third == 0x01 && fourth == 0x02)) {
          // Account for optional data descriptor (12 bytes) before the next header
          long dataLen = i - dataStart;
          if (dataLen >= 16) {
            // Check if an optional data descriptor with signature sits just before
            int descSigPos = i - 16;
            if (descSigPos >= dataStart && buf.getInt(descSigPos) == 0x08074b50) {
              dataLen = descSigPos - dataStart;
            } else if (i - 12 >= dataStart) {
              // Data descriptor without signature (12 bytes: CRC + sizes)
              dataLen = (long) i - 12 - dataStart;
            }
          }
          return Math.max(0, dataLen);
        }
      }
    }
    // No next header found  -  data runs to end of file
    return (long) fileSize - dataStart;
  }

  private static byte[] inflate(byte[] compressed, int expectedSize, long maxOutputBytes) {
    try (Inflater inflater = new Inflater(true)) {
      inflater.setInput(compressed);
      ByteArrayOutputStream out =
          new ByteArrayOutputStream(
              expectedSize > 0 ? Math.min(expectedSize, 16 * 1024 * 1024) : 8192);
      byte[] tmp = new byte[8192];
      long totalWritten = 0;
      while (!inflater.finished()) {
        int n = inflater.inflate(tmp);
        if (n == 0 && inflater.needsInput()) break;
        totalWritten += n;
        // Guard against zip bombs  -  abort before allocating unbounded memory
        if (totalWritten > maxOutputBytes) {
          return null;
        }
        out.write(tmp, 0, n);
      }
      return out.toByteArray();
    } catch (DataFormatException e) {
      return null;
    }
  }

  private static int getUnsignedShort(ByteBuffer buf, int offset) {
    return buf.getShort(offset) & 0xFFFF;
  }

  private static long getUnsignedInt(ByteBuffer buf, int offset) {
    return buf.getInt(offset) & 0xFFFFFFFFL;
  }
}
