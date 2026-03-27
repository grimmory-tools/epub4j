package org.grimmory.epub4j.util;

import java.util.regex.Pattern;

/**
 * XML character cleaning utilities for ensuring XML 1.0 validity.
 *
 * <p>XML has strict character rules defined by the XML 1.0 specification. Many Unicode characters
 * are invalid in XML documents. This class provides utilities to clean text content to ensure XML
 * validity.
 *
 * <p>Valid XML characters are:
 *
 * <ul>
 *   <li>#x9 (tab) | #xA (line feed) | #xD (carriage return)
 *   <li>#x20-#xD7FF (most of Basic Multilingual Plane)
 *   <li>#xE000-#xFFFD (Private Use Area, excluding surrogates)
 *   <li>#x10000-#x10FFFF (Supplementary Planes)
 * </ul>
 *
 * <p>Invalid characters include:
 *
 * <ul>
 *   <li>ASCII control characters (0x00-0x1F except 0x09, 0x0A, 0x0D)
 *   <li>DEL character (0x7F)
 *   <li>Unicode surrogates (0xD800-0xDFFF)
 *   <li>Non-characters (0xFFFE, 0xFFFF, etc.)
 * </ul>
 *
 * @author Grimmory
 */
public class XmlCleaner {

  /** Pattern to match ASCII control characters (except tab, LF, CR) */
  private static final Pattern ASCII_CONTROL_PATTERN =
      Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

  /** Pattern to match Unicode non-characters */
  private static final Pattern NON_CHAR_PATTERN =
      Pattern.compile("[\\uD800-\\uDFFF\\uFFFE\\uFFFF]");

  /**
   * Check if a Unicode code point is allowed in XML 1.0 documents.
   *
   * @param codePoint the Unicode code point to check
   * @return true if the character is valid in XML
   */
  public static boolean isAllowedXmlChar(int codePoint) {
    // Valid ranges per XML 1.0 specification
    return (codePoint == 0x09) // tab
        || (codePoint == 0x0A) // line feed
        || (codePoint == 0x0D) // carriage return
        || (codePoint >= 0x20 && codePoint <= 0xD7FF)
        || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
        || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
  }

  /**
   * Check if a char is allowed in XML 1.0 documents. Note: This doesn't handle surrogate pairs
   * correctly. Use {@link #isAllowedXmlChar(int)} for full Unicode support.
   *
   * @param c the character to check
   * @return true if the character is valid in XML
   */
  public static boolean isAllowedXmlChar(char c) {
    return isAllowedXmlChar((int) c);
  }

  /**
   * Clean a string by removing all characters that are invalid in XML.
   *
   * @param text the text to clean
   * @return cleaned text with only valid XML characters
   */
  public static String cleanXmlChars(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }

    StringBuilder sb = new StringBuilder(text.length());
    int len = text.length();

    for (int i = 0; i < len; ) {
      int codePoint = text.codePointAt(i);
      if (isAllowedXmlChar(codePoint)) {
        sb.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }

    return sb.toString();
  }

  /**
   * Clean a StringBuilder by removing all characters that are invalid in XML. This is more
   * efficient than cleaning a String for large texts.
   *
   * @param sb the StringBuilder to clean (modified in place)
   * @return the same StringBuilder for chaining
   */
  public static StringBuilder cleanXmlChars(StringBuilder sb) {
    if (sb == null || sb.isEmpty()) {
      return sb;
    }

    int readIndex = 0;
    int writeIndex = 0;
    int len = sb.length();

    while (readIndex < len) {
      int codePoint = sb.codePointAt(readIndex);
      if (isAllowedXmlChar(codePoint)) {
        int charCount = Character.charCount(codePoint);
        if (readIndex != writeIndex) {
          // Need to move characters
          for (int i = 0; i < charCount; i++) {
            sb.setCharAt(writeIndex + i, sb.charAt(readIndex + i));
          }
        }
        writeIndex += charCount;
      }
      readIndex += Character.charCount(codePoint);
    }

    sb.setLength(writeIndex);
    return sb;
  }

  /**
   * Remove ASCII control characters from text. This removes all ASCII control characters
   * (0x00-0x1F) except:
   *
   * <ul>
   *   <li>0x09 (tab)
   *   <li>0x0A (line feed)
   *   <li>0x0D (carriage return)
   * </ul>
   *
   * Also removes DEL (0x7F).
   *
   * @param text the text to clean
   * @return cleaned text with ASCII control characters removed
   */
  public static String cleanAsciiControl(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    return ASCII_CONTROL_PATTERN.matcher(text).replaceAll("");
  }

  /**
   * Remove ASCII control characters from a byte array.
   *
   * @param data the byte array to clean
   * @return cleaned byte array
   */
  public static byte[] cleanAsciiControl(byte[] data) {
    if (data == null || data.length == 0) {
      return data;
    }

    // First pass: count valid bytes
    int validCount = 0;
    for (byte b : data) {
      int c = b & 0xFF;
      if (c == 0x09 || c == 0x0A || c == 0x0D || (c >= 0x20 && c < 0x7F)) {
        validCount++;
      }
    }

    // If all bytes are valid, return original
    if (validCount == data.length) {
      return data;
    }

    // Second pass: copy valid bytes
    byte[] result = new byte[validCount];
    int j = 0;
    for (byte b : data) {
      int c = b & 0xFF;
      if (c == 0x09 || c == 0x0A || c == 0x0D || (c >= 0x20 && c < 0x7F)) {
        result[j++] = b;
      }
    }

    return result;
  }

  /**
   * Remove Unicode non-characters from text. Non-characters include surrogates (U+D800-U+DFFF) and
   * permanent non-characters (U+FFFE, U+FFFF, etc.).
   *
   * @param text the text to clean
   * @return cleaned text with non-characters removed
   */
  public static String removeNonCharacters(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }

    // Quick check: does text contain any non-characters?
    if (!NON_CHAR_PATTERN.matcher(text).find()) {
      return text;
    }

    StringBuilder sb = new StringBuilder(text.length());
    int len = text.length();

    for (int i = 0; i < len; ) {
      int codePoint = text.codePointAt(i);
      // Check for non-characters
      boolean isNonChar =
          (codePoint >= 0xD800 && codePoint <= 0xDFFF) // Surrogates
              || (codePoint >= 0xFDD0 && codePoint <= 0xFDEF) // Non-characters
              || ((codePoint & 0xFFFF) >= 0xFFFE); // U+FFFE, U+FFFF, etc.

      if (!isNonChar) {
        sb.appendCodePoint(codePoint);
      }
      i += Character.charCount(codePoint);
    }

    return sb.toString();
  }

  /**
   * Clean text for XML: remove control characters and non-characters. This is the main entry point
   * for cleaning text before XML serialization.
   *
   * @param text the text to clean
   * @return cleaned text safe for XML
   */
  public static String cleanForXml(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }

    // First remove ASCII control characters (fast path)
    text = cleanAsciiControl(text);

    // Then remove non-characters and validate Unicode
    return removeNonCharacters(text);
  }

  /**
   * Validate that a string contains only valid XML characters.
   *
   * @param text the text to validate
   * @return true if all characters are valid XML characters
   */
  public static boolean isValidXmlText(String text) {
    if (text == null || text.isEmpty()) {
      return true;
    }

    int len = text.length();
    for (int i = 0; i < len; ) {
      int codePoint = text.codePointAt(i);
      if (!isAllowedXmlChar(codePoint)) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  /**
   * Find the first invalid XML character in a string.
   *
   * @param text the text to check
   * @return the index of the first invalid character, or -1 if all valid
   */
  public static int findFirstInvalidChar(String text) {
    if (text == null || text.isEmpty()) {
      return -1;
    }

    int len = text.length();
    for (int i = 0; i < len; ) {
      int codePoint = text.codePointAt(i);
      if (!isAllowedXmlChar(codePoint)) {
        return i;
      }
      i += Character.charCount(codePoint);
    }
    return -1;
  }
}
