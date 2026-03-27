package org.grimmory.epub4j.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for decoding archive filenames and detecting content encodings.
 *
 * <p>ZIP files use a general purpose flag bit (bit 11) to indicate whether filenames are encoded as
 * UTF-8. When this bit is not set, filenames are encoded in CP437 (the original IBM PC character
 * set). Many older tools and manga/comic archives use Shift_JIS or CP437 encoded filenames.
 *
 * <p>Follows the PKZIP APPNOTE specification for filename encoding detection.
 */
public final class FilenameDecoder {

  /** CP437 charset - the default ZIP filename encoding when UTF-8 flag is not set. */
  private static final Charset CP437 = Charset.forName("CP437");

  /** ZIP general purpose flag bit 11 - indicates UTF-8 encoded filenames. */
  private static final int UTF8_FLAG = 0x800;

  private FilenameDecoder() {}

  /**
   * Decodes a ZIP filename based on the general purpose flags. If bit 11 (0x800) is set, the
   * filename is UTF-8 encoded. Otherwise, it's CP437 encoded (per PKZIP APPNOTE specification).
   *
   * @param rawBytes the raw filename bytes from the ZIP entry
   * @param generalPurposeFlags the general purpose bit flag from the ZIP entry header
   * @return the decoded filename string
   */
  public static String decodeZipFilename(byte[] rawBytes, int generalPurposeFlags) {
    Charset charset = (generalPurposeFlags & UTF8_FLAG) != 0 ? StandardCharsets.UTF_8 : CP437;
    return new String(rawBytes, charset);
  }

  /**
   * Sniffs the encoding of an XML content stream by examining BOM and declaration. Handles UTF-8,
   * UTF-16, and other common XML encodings.
   *
   * <p>Inspired by epubcheck's XMLEncodingSniffer approach.
   *
   * @param in a markable input stream (must support mark/reset)
   * @return the detected encoding name, or null if undetermined (assume UTF-8)
   */
  public static String sniffXmlEncoding(InputStream in) throws IOException {
    byte[] buffer = new byte[256];
    in.mark(buffer.length);
    int len = in.read(buffer);
    in.reset();

    if (len < 4) {
      return null;
    }

    // Check BOM (Byte Order Mark)
    // Order matters: check 4-byte sequences before 2-byte ones

    // UCS-4 BOMs (4-byte patterns)
    if (matchesMagic(buffer, (byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF)) {
      return "UCS-4BE";
    }
    if (matchesMagic(buffer, (byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00)) {
      return "UCS-4LE";
    }
    // UCS-4 unusual byte orders
    if (matchesMagic(buffer, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFE)
        || matchesMagic(buffer, (byte) 0xFE, (byte) 0xFF, (byte) 0x00, (byte) 0x00)) {
      return "UCS-4";
    }
    // UCS-4 null-pattern detection (no BOM, but null bytes reveal encoding)
    if (matchesMagic(buffer, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3C)) {
      return "UCS-4BE";
    }
    if (matchesMagic(buffer, (byte) 0x3C, (byte) 0x00, (byte) 0x00, (byte) 0x00)) {
      return "UCS-4LE";
    }

    // UTF-8 BOM
    if (matchesMagic(buffer, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
      return "UTF-8";
    }

    // UTF-16 BOMs (2-byte patterns, checked after 4-byte to avoid mismatching UCS-4)
    if (matchesMagic(buffer, (byte) 0xFE, (byte) 0xFF)) {
      return "UTF-16BE";
    }
    if (matchesMagic(buffer, (byte) 0xFF, (byte) 0xFE)) {
      return "UTF-16LE";
    }
    // UTF-16 null-pattern detection (no BOM)
    if (matchesMagic(buffer, (byte) 0x00, (byte) 0x3C, (byte) 0x00, (byte) 0x3F)) {
      return "UTF-16BE";
    }
    if (matchesMagic(buffer, (byte) 0x3C, (byte) 0x00, (byte) 0x3F, (byte) 0x00)) {
      return "UTF-16LE";
    }

    // EBCDIC pattern (<?xm in EBCDIC: 0x4C 0x6F 0xA7 0x94)
    if (matchesMagic(buffer, (byte) 0x4C, (byte) 0x6F, (byte) 0xA7, (byte) 0x94)) {
      return "EBCDIC";
    }

    // Read ASCII portion and look for encoding= declaration
    int asciiLen = 0;
    while (asciiLen < len) {
      int c = buffer[asciiLen] & 0xFF;
      if (c == 0 || c > 0x7F) break;
      asciiLen++;
    }
    if (asciiLen == 0) return null;

    String header = new String(buffer, 0, asciiLen, StandardCharsets.US_ASCII);
    Matcher matcher = Pattern.compile("encoding\\s*=\\s*(['\"])([^'\"]+)\\1").matcher(header);
    if (!matcher.find()) return null;

    return matcher.group(2).toUpperCase();
  }

  /** Checks if the input stream starts with a UTF-8 BOM (EF BB BF). */
  public static boolean hasUtf8Bom(InputStream in) throws IOException {
    byte[] bom = new byte[3];
    in.mark(3);
    int read = in.read(bom);
    in.reset();
    return read == 3 && matchesMagic(bom, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
  }

  private static boolean matchesMagic(byte[] buffer, byte... magic) {
    if (buffer.length < magic.length) return false;
    for (int i = 0; i < magic.length; i++) {
      if (buffer[i] != magic[i]) return false;
    }
    return true;
  }
}
