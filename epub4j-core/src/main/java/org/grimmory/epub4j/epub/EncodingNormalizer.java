/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;

/**
 * Detects and normalizes text encodings in EPUB resources. Handles BOMs, XML declarations, HTML
 * meta tags, and heuristic detection.
 *
 * <p>Real-world EPUBs often have encoding chaos: BOMs, mislabeled charsets, mixed encodings within
 * a single file. This normalizer converts everything to clean UTF-8 without BOM.
 *
 * <p>Handles BOM stripping, charset re-detection, and declaration fixup.
 *
 * @author Grimmory
 */
public class EncodingNormalizer {

  private static final System.Logger log = System.getLogger(EncodingNormalizer.class.getName());

  // Pluggable heuristic detector (e.g. ICU via epub4j-native)
  private static volatile EncodingDetector heuristicDetector;

  /**
   * Register a heuristic encoding detector used when BOM and declarations are absent. Typically
   * called once at startup by the native module.
   */
  public static void setEncodingDetector(EncodingDetector detector) {
    heuristicDetector = detector;
  }

  /** Byte Order Mark signatures for common encodings. */
  public enum BOM {
    UTF_8(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "UTF-8"),
    UTF_16LE(new byte[] {(byte) 0xFF, (byte) 0xFE}, "UTF-16LE"),
    UTF_16BE(new byte[] {(byte) 0xFE, (byte) 0xFF}, "UTF-16BE"),
    UTF_32LE(new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00}, "UTF-32LE"),
    UTF_32BE(new byte[] {(byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF}, "UTF-32BE");

    final byte[] bytes;
    final String charset;

    BOM(byte[] bytes, String charset) {
      this.bytes = bytes;
      this.charset = charset;
    }

    public byte[] getBytes() {
      return bytes;
    }

    public String getCharset() {
      return charset;
    }
  }

  // Patterns for encoding detection
  private static final Pattern XML_DECL_ENCODING =
      Pattern.compile("<\\?xml[^>]+encoding\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  private static final Pattern HTML_META_CHARSET =
      Pattern.compile(
          "<meta[^>]+charset\\s*=\\s*[\"']?([^\"'\\s>]+)[\"']?", Pattern.CASE_INSENSITIVE);

  private static final Pattern HTML_META_CONTENT_TYPE =
      Pattern.compile(
          "<meta[^>]+content\\s*=\\s*[\"'][^\"']*charset\\s*=\\s*([^\"'\\s;]+)[\"']",
          Pattern.CASE_INSENSITIVE);

  // Common encoding aliases mapping to Java charset names
  private static final Map<String, String> ENCODING_ALIASES;

  static {
    ENCODING_ALIASES =
        Map.ofEntries(
            Map.entry("utf8", "UTF-8"),
            Map.entry("utf-8", "UTF-8"),
            Map.entry("utf16", "UTF-16"),
            Map.entry("utf-16", "UTF-16"),
            Map.entry("utf32", "UTF-32"),
            Map.entry("utf-32", "UTF-32"),
            Map.entry("ascii", "US-ASCII"),
            Map.entry("us-ascii", "US-ASCII"),
            Map.entry("iso-8859-1", "ISO-8859-1"),
            Map.entry("iso8859-1", "ISO-8859-1"),
            Map.entry("latin1", "ISO-8859-1"),
            Map.entry("latin-1", "ISO-8859-1"),
            Map.entry("iso-8859-15", "ISO-8859-15"),
            Map.entry("iso8859-15", "ISO-8859-15"),
            Map.entry("latin9", "ISO-8859-15"),
            Map.entry("windows-1250", "windows-1250"),
            Map.entry("cp1250", "windows-1250"),
            Map.entry("windows-1251", "windows-1251"),
            Map.entry("cp1251", "windows-1251"),
            Map.entry("windows-1252", "windows-1252"),
            Map.entry("cp1252", "windows-1252"),
            Map.entry("shift_jis", "Shift_JIS"),
            Map.entry("shift-jis", "Shift_JIS"),
            Map.entry("sjis", "Shift_JIS"),
            Map.entry("gb2312", "GB2312"),
            Map.entry("gbk", "GBK"),
            Map.entry("gb18030", "GB18030"),
            Map.entry("big5", "Big5"),
            Map.entry("euc-jp", "EUC-JP"),
            Map.entry("eucjp", "EUC-JP"),
            Map.entry("euc-kr", "EUC-KR"),
            Map.entry("euckr", "EUC-KR"),
            Map.entry("koi8-r", "KOI8-R"),
            Map.entry("koi8r", "KOI8-R"),
            Map.entry("koi8-u", "KOI8-U"),
            Map.entry("koi8u", "KOI8-U"),
            Map.entry("macroman", "MacRoman"),
            Map.entry("mac-roman", "MacRoman"));
  }

  /**
   * Result of encoding analysis.
   *
   * @param hasBom whether a BOM was detected
   * @param bom the detected BOM type, if any
   * @param declaredEncoding encoding from XML/HTML declaration, if any
   * @param detectedEncoding heuristically detected encoding, if any
   * @param needsNormalization whether the data needs UTF-8 conversion
   */
  public record EncodingAnalysis(
      boolean hasBom,
      Optional<BOM> bom,
      Optional<String> declaredEncoding,
      Optional<String> detectedEncoding,
      boolean needsNormalization) {}

  /**
   * Detect BOM at start of byte array.
   *
   * @param data the data to check
   * @return detected BOM type, or empty
   */
  public static Optional<BOM> detectBom(byte[] data) {
    if (data == null || data.length < 2) {
      return Optional.empty();
    }

    for (BOM bom : BOM.values()) {
      if (data.length >= bom.bytes.length) {
        boolean matches = true;
        for (int i = 0; i < bom.bytes.length; i++) {
          if (data[i] != bom.bytes[i]) {
            matches = false;
            break;
          }
        }
        if (matches) {
          return Optional.of(bom);
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Strip BOM from data if present.
   *
   * @param data the data to process
   * @return data without BOM (may be same array if no BOM)
   */
  public static byte[] stripBom(byte[] data) {
    Optional<BOM> bom = detectBom(data);
    if (bom.isPresent()) {
      byte[] bomBytes = bom.get().getBytes();
      if (data.length > bomBytes.length) {
        return Arrays.copyOfRange(data, bomBytes.length, data.length);
      }
      return new byte[0];
    }
    return data;
  }

  /**
   * Extract encoding from XML prolog: {@code <?xml version="1.0" encoding="UTF-8"?>}
   *
   * @param data the data to scan
   * @return declared encoding, or empty
   */
  public static Optional<String> extractXmlDeclarationEncoding(byte[] data) {
    if (data == null || data.length < 5) {
      return Optional.empty();
    }

    // Check if it starts with XML declaration
    String start;
    try {
      // Try reading first 200 bytes as ASCII/UTF-8
      int len = Math.min(200, data.length);
      start = new String(data, 0, len, StandardCharsets.US_ASCII);
    } catch (Exception e) {
      return Optional.empty();
    }

    if (!start.trim().startsWith("<?xml")) {
      return Optional.empty();
    }

    Matcher matcher = XML_DECL_ENCODING.matcher(start);
    if (matcher.find()) {
      String encoding = matcher.group(1).trim();
      return Optional.of(normalizeEncodingName(encoding));
    }

    return Optional.empty();
  }

  /**
   * Extract encoding from HTML meta tag: {@code <meta charset="UTF-8">} or {@code <meta
   * http-equiv="Content-Type" content="text/html; charset=UTF-8">}
   *
   * @param data the data to scan
   * @return declared encoding, or empty
   */
  public static Optional<String> extractHtmlMetaEncoding(byte[] data) {
    if (data == null || data.length < 10) {
      return Optional.empty();
    }

    String content;
    try {
      // Try with UTF-8 first (most common)
      content = new String(data, 0, Math.min(1000, data.length), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return Optional.empty();
    }

    // Try <meta charset="...">
    Matcher charsetMatcher = HTML_META_CHARSET.matcher(content);
    if (charsetMatcher.find()) {
      String encoding = charsetMatcher.group(1).trim();
      return Optional.of(normalizeEncodingName(encoding));
    }

    // Try <meta http-equiv="Content-Type" content="...charset=...">
    Matcher contentTypeMatcher = HTML_META_CONTENT_TYPE.matcher(content);
    if (contentTypeMatcher.find()) {
      String encoding = contentTypeMatcher.group(1).trim();
      return Optional.of(normalizeEncodingName(encoding));
    }

    return Optional.empty();
  }

  /**
   * Normalize encoding name to Java charset name.
   *
   * @param encoding the encoding name to normalize
   * @return normalized encoding name
   */
  public static String normalizeEncodingName(String encoding) {
    if (encoding == null || encoding.isBlank()) {
      return "UTF-8";
    }

    String lower = encoding.trim().toLowerCase();
    String normalized = ENCODING_ALIASES.get(lower);
    return normalized != null ? normalized : encoding.trim();
  }

  /**
   * Analyze encoding of data.
   *
   * @param data the data to analyze
   * @return encoding analysis result
   */
  public static EncodingAnalysis analyze(byte[] data) {
    if (data == null || data.length == 0) {
      return new EncodingAnalysis(
          false, Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    Optional<BOM> bom = detectBom(data);
    Optional<String> xmlEncoding = extractXmlDeclarationEncoding(data);
    Optional<String> htmlEncoding = extractHtmlMetaEncoding(data);

    String declaredEncoding = xmlEncoding.or(() -> htmlEncoding).orElse(null);
    boolean hasBom = bom.isPresent();

    // Heuristic fallback: try pluggable detector when BOM and declarations are absent
    Optional<String> detectedEncoding = Optional.empty();
    if (!hasBom && declaredEncoding == null) {
      EncodingDetector detector = heuristicDetector;
      if (detector != null) {
        detectedEncoding =
            detector
                .detect(data)
                .filter(r -> r.confidence() >= 30)
                .map(r -> normalizeEncodingName(r.encoding()));
      }
    }

    boolean needsNormalization =
        hasBom
            || (declaredEncoding != null && !"UTF-8".equals(declaredEncoding))
            || (detectedEncoding.isPresent() && !"UTF-8".equals(detectedEncoding.get()));

    return new EncodingAnalysis(
        hasBom, bom, xmlEncoding.or(() -> htmlEncoding), detectedEncoding, needsNormalization);
  }

  /**
   * Normalize resource to UTF-8 without BOM. Returns true if the resource was modified.
   *
   * @param resource the resource to normalize
   * @return true if modified
   */
  public static boolean normalizeToUtf8(Resource resource) throws IOException {
    if (resource == null) {
      return false;
    }

    // Only process text resources
    if (!isTextResource(resource)) {
      return false;
    }

    byte[] data = resource.getData();
    if (data == null || data.length == 0) {
      return false;
    }

    EncodingAnalysis analysis = analyze(data);

    if (!analysis.needsNormalization()) {
      // Already UTF-8 without BOM
      if (!"UTF-8".equals(resource.getInputEncoding())) {
        resource.setInputEncoding("UTF-8");
        return true;
      }
      return false;
    }

    // Determine source encoding
    String sourceEncoding =
        analysis
            .bom()
            .map(BOM::getCharset)
            .or(() -> analysis.declaredEncoding)
            .or(() -> analysis.detectedEncoding)
            .orElse("UTF-8");

    // Strip BOM if present
    byte[] cleanData = stripBom(data);

    // Convert to UTF-8
    byte[] utf8Data;
    try {
      String str = new String(cleanData, Charset.forName(sourceEncoding));
      utf8Data = str.getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.log(
          System.Logger.Level.WARNING,
          "Failed to convert "
              + resource.getHref()
              + " from "
              + sourceEncoding
              + " to UTF-8: "
              + e.getMessage());
      // Try UTF-8 as fallback
      try {
        String str = new String(cleanData, StandardCharsets.UTF_8);
        utf8Data = str.getBytes(StandardCharsets.UTF_8);
      } catch (Exception e2) {
        log.log(
            System.Logger.Level.ERROR,
            "Failed to decode " + resource.getHref() + " as UTF-8: " + e2.getMessage());
        return false; // Can't safely convert
      }
    }

    // Release original data reference early  -  cleanData/data no longer needed
    // after conversion so the GC can reclaim sooner

    // Update resource once after all transformations
    resource.setInputEncoding("UTF-8");

    // Apply declaration updates on the String to avoid repeated byte→String→byte conversions
    String utf8Content = new String(utf8Data, StandardCharsets.UTF_8);
    boolean contentModified = false;

    // Update XML declaration if present
    if (analysis.declaredEncoding().isPresent()
            && !"UTF-8".equals(analysis.declaredEncoding().get())
        || analysis.hasBom()) {
      String updated = updateXmlDeclarationString(utf8Content);
      if (updated != null) {
        utf8Content = updated;
        contentModified = true;
      }
    }

    // Update HTML meta charset if present
    String htmlUpdated = updateHtmlMetaCharsetString(utf8Content);
    if (htmlUpdated != null) {
      utf8Content = htmlUpdated;
      contentModified = true;
    }

    // Single setData call with final result
    resource.setData(contentModified ? utf8Content.getBytes(StandardCharsets.UTF_8) : utf8Data);

    log.log(
        System.Logger.Level.DEBUG,
        "Normalized "
            + resource.getHref()
            + " to UTF-8 (was: "
            + sourceEncoding
            + (analysis.hasBom() ? " + BOM" : "")
            + ")");
    return true;
  }

  /** Update XML declaration to specify UTF-8 encoding. */
  private static byte[] updateXmlDeclaration(byte[] data) {
    String content;
    try {
      content = new String(data, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return data;
    }

    // Check if XML declaration exists
    if (content.trim().startsWith("<?xml")) {
      // Update existing declaration
      String updated =
          XML_DECL_ENCODING
              .matcher(content)
              .replaceAll("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      return updated.getBytes(StandardCharsets.UTF_8);
    } else {
      // Add declaration
      return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + content)
          .getBytes(StandardCharsets.UTF_8);
    }
  }

  /**
   * Update HTML meta charset to UTF-8.
   *
   * @return updated data, or original if no change needed
   */
  private static byte[] updateHtmlMetaCharset(byte[] data) {
    String content;
    try {
      content = new String(data, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return data;
    }

    boolean modified = false;

    // Replace <meta charset="..."> with UTF-8
    if (HTML_META_CHARSET.matcher(content).find()) {
      content = HTML_META_CHARSET.matcher(content).replaceAll("<meta charset=\"UTF-8\">");
      modified = true;
    }

    // Replace <meta http-equiv="Content-Type" content="...charset=..."> with UTF-8
    if (HTML_META_CONTENT_TYPE.matcher(content).find()) {
      content =
          HTML_META_CONTENT_TYPE
              .matcher(content)
              .replaceAll(
                  "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
      modified = true;
    }

    return modified ? content.getBytes(StandardCharsets.UTF_8) : data;
  }

  /**
   * String-based XML declaration update - avoids an extra byte[]→String conversion when the caller
   * already has the content as a String.
   *
   * @return updated content, or null if no change was needed/possible
   */
  private static String updateXmlDeclarationString(String content) {
    if (content.trim().startsWith("<?xml")) {
      return XML_DECL_ENCODING
          .matcher(content)
          .replaceAll("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    }
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + content;
  }

  /**
   * String-based HTML meta charset update - avoids an extra byte[]→String conversion.
   *
   * @return updated content, or null if no change was needed
   */
  private static String updateHtmlMetaCharsetString(String content) {
    boolean modified = false;
    if (HTML_META_CHARSET.matcher(content).find()) {
      content = HTML_META_CHARSET.matcher(content).replaceAll("<meta charset=\"UTF-8\">");
      modified = true;
    }
    if (HTML_META_CONTENT_TYPE.matcher(content).find()) {
      content =
          HTML_META_CONTENT_TYPE
              .matcher(content)
              .replaceAll(
                  "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
      modified = true;
    }
    return modified ? content : null;
  }

  /** Check if resource is a text resource (XHTML, CSS, etc). */
  private static boolean isTextResource(Resource resource) {
    if (resource.getMediaType() == null) {
      // Check by extension
      String href = resource.getHref();
      if (href != null) {
        return href.endsWith(".html")
            || href.endsWith(".htm")
            || href.endsWith(".xhtml")
            || href.endsWith(".css")
            || href.endsWith(".txt")
            || href.endsWith(".xml")
            || href.endsWith(".ncx")
            || href.endsWith(".smil")
            || href.endsWith(".pls");
      }
      return false;
    }

    return resource.getMediaType() == MediaTypes.XHTML
        || resource.getMediaType() == MediaTypes.CSS
        || resource.getMediaType() == MediaTypes.NCX
        || resource.getMediaType() == MediaTypes.SMIL
        || resource.getMediaType() == MediaTypes.PLS
        || resource.getMediaType() == MediaTypes.JAVASCRIPT;
  }

  /**
   * Normalize all text resources in a book.
   *
   * @param book the book to normalize
   * @return number of resources modified
   */
  public static int normalizeBook(Book book) {
    if (book == null) {
      return 0;
    }

    int modified = 0;
    for (Resource resource : book.getResources().getAll()) {
      try {
        if (normalizeToUtf8(resource)) {
          modified++;
        }
      } catch (IOException e) {
        log.log(
            System.Logger.Level.ERROR,
            "Failed to normalize encoding for " + resource.getHref() + ": " + e.getMessage());
      }
    }

    log.log(System.Logger.Level.INFO, "Normalized " + modified + " resources to UTF-8");
    return modified;
  }

  /**
   * Detect encoding issues in a book without modifying.
   *
   * @param book the book to check
   * @return list of resources with encoding issues
   */
  public static List<Resource> detectEncodingIssues(Book book) {
    List<Resource> issues = new ArrayList<>();
    if (book == null) {
      return issues;
    }

    for (Resource resource : book.getResources().getAll()) {
      if (!isTextResource(resource)) {
        continue;
      }

      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) {
          continue;
        }

        EncodingAnalysis analysis = analyze(data);
        if (analysis.needsNormalization()) {
          issues.add(resource);
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Failed to check encoding for " + resource.getHref());
      }
    }

    return issues;
  }
}
