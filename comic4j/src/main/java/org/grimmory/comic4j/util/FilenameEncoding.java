package org.grimmory.comic4j.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * Detects and decodes archive entry filenames that may be encoded in various charsets. Tries
 * charsets in order of likelihood: UTF-8, Shift_JIS, ISO-8859-1, CP437, MS932.
 */
public final class FilenameEncoding {

  private static final Charset[] CHARSETS_TO_TRY = {
    StandardCharsets.UTF_8,
    Charset.forName("Shift_JIS"),
    StandardCharsets.ISO_8859_1,
    Charset.forName("CP437"),
    Charset.forName("MS932")
  };

  private FilenameEncoding() {}

  /**
   * Attempts to decode the given bytes as a filename string using the preferred charset order.
   * Returns the decoded string using the first charset that succeeds without errors. Falls back to
   * UTF-8 with replacement characters if all charsets fail.
   */
  public static String decode(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    for (Charset charset : CHARSETS_TO_TRY) {
      CharsetDecoder decoder =
          charset
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
      try {
        CharBuffer result = decoder.decode(ByteBuffer.wrap(bytes));
        String decoded = result.toString();
        // For UTF-8, also check that the result doesn't contain obvious mojibake
        if (charset == StandardCharsets.UTF_8 || isReadable(decoded)) {
          return decoded;
        }
      } catch (CharacterCodingException ignored) {
        // Try next charset
      }
    }
    // Ultimate fallback: UTF-8 with replacement
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Checks if a decoded string appears to contain readable characters (no replacement characters or
   * excessive control characters).
   */
  private static boolean isReadable(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\uFFFD') {
        return false;
      }
    }
    return true;
  }

  /**
   * Attempts to detect the charset of the given bytes. Returns the first charset that can decode
   * the bytes without errors, or UTF-8 as the fallback.
   */
  public static Charset detect(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return StandardCharsets.UTF_8;
    }
    for (Charset charset : CHARSETS_TO_TRY) {
      CharsetDecoder decoder =
          charset
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
      try {
        decoder.decode(ByteBuffer.wrap(bytes));
        return charset;
      } catch (CharacterCodingException ignored) {
        // Try next
      }
    }
    return StandardCharsets.UTF_8;
  }
}
