/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Lightweight EPUB structural validator that checks the ZIP-level requirements from the EPUB Open
 * Container Format (OCF) specification.
 *
 * <p>Inspired by epubcheck's OCFZipChecker, this validates:
 *
 * <ul>
 *   <li>PKG_004: File starts with PK magic bytes (valid ZIP)
 *   <li>PKG_006: First entry filename is "mimetype" (8 bytes)
 *   <li>PKG_005: Mimetype entry has no extra field data
 *   <li>PKG_007: Mimetype content equals "application/epub+zip"
 * </ul>
 *
 * <p>This is a quick structural check on raw bytes, not a full EPUB validation. For comprehensive
 * validation, use epubcheck directly.
 */
public final class EpubValidator {

  /** Expected mimetype content for a valid EPUB file. */
  private static final String EPUB_MIMETYPE = "application/epub+zip";

  /** Minimum header size needed for validation (local file header + mimetype content). */
  private static final int HEADER_SIZE = 58;

  // Cached classpath probe  -  avoids repeated Class.forName calls
  private static final boolean EPUBCHECK_AVAILABLE = probeEpubCheck();

  private EpubValidator() {}

  /**
   * Returns true if W3C epubcheck is on the classpath. When available, {@link #validateFull(Path)}
   * uses it for deep validation.
   */
  public static boolean isEpubCheckAvailable() {
    return EPUBCHECK_AVAILABLE;
  }

  /**
   * Validates the EPUB file at the given path.
   *
   * @return a ValidationResult with any warnings or errors found
   */
  public static ValidationResult validate(Path epubPath) throws IOException {
    return validateDetailed(epubPath).toLegacyResult();
  }

  /**
   * Validates the EPUB from an input stream. The stream must be at position 0.
   *
   * @return a ValidationResult with any warnings or errors found
   */
  public static ValidationResult validate(InputStream in) throws IOException {
    return validateDetailed(in).toLegacyResult();
  }

  /** Validates the EPUB file at the given path and returns structured issues. */
  public static DetailedValidationResult validateDetailed(Path epubPath) throws IOException {
    try (InputStream in = Files.newInputStream(epubPath)) {
      return validateDetailed(in);
    }
  }

  /** Validates the EPUB from an input stream and returns structured issues. */
  public static DetailedValidationResult validateDetailed(InputStream in) throws IOException {
    byte[] epubBytes = in.readAllBytes();

    List<ValidationIssue> issues = new ArrayList<>();

    byte[] header = new byte[HEADER_SIZE];
    int readCount = readFully(new ByteArrayInputStream(epubBytes), header);

    if (readCount < 4) {
      addError(issues, "EPUB_000", "File is too small to be a valid ZIP archive");
      return new DetailedValidationResult(issues);
    }

    // PKG_004: Local file header signature must be PK\x03\x04
    if (header[0] != 'P' || header[1] != 'K' || header[2] != 0x03 || header[3] != 0x04) {
      addError(
          issues, "PKG_004", "File does not start with PK signature - not a valid ZIP archive");
      return new DetailedValidationResult(issues);
    }

    if (readCount < HEADER_SIZE) {
      addError(issues, "EPUB_004", "File header too short for EPUB structural validation");
      return new DetailedValidationResult(issues);
    }

    // Parse local file header fields (little-endian)
    int filenameLength = getUint16LE(header, 26);
    int extraFieldLength = getUint16LE(header, 28);

    // PKG_006: First entry filename must be "mimetype" (8 chars)
    if (filenameLength != 8) {
      addError(
          issues,
          "PKG_006",
          "First ZIP entry filename length is " + filenameLength + ", expected 8 (\"mimetype\")");
    } else {
      String firstName = new String(header, 30, 8, StandardCharsets.US_ASCII);
      if (!"mimetype".equals(firstName)) {
        addError(
            issues, "PKG_006", "First ZIP entry is \"" + firstName + "\", expected \"mimetype\"");
      }
    }

    // PKG_005: Mimetype entry must have no extra field
    if (extraFieldLength != 0) {
      addError(
          issues,
          "PKG_005",
          "Mimetype entry has extra field of "
              + extraFieldLength
              + " bytes - the use of the extra field feature is not permitted for the mimetype file");
    }

    // Check compression method at offset 8 (little-endian uint16)
    // Mimetype must be STORED (0), not DEFLATED (8) or other
    int compressionMethod = getUint16LE(header, 8);
    if (compressionMethod != 0) {
      addError(
          issues,
          "PKG_007",
          "Mimetype entry is compressed (method="
              + compressionMethod
              + ") - it must be stored uncompressed");
    }

    // PKG_007: Check mimetype content (starts at offset 30 + filenameLength + extraFieldLength)
    int contentOffset = 30 + filenameLength + extraFieldLength;
    if (contentOffset + EPUB_MIMETYPE.length() <= readCount) {
      String mimetypeContent =
          new String(
              header,
              contentOffset,
              Math.min(EPUB_MIMETYPE.length(), readCount - contentOffset),
              StandardCharsets.US_ASCII);
      if (!EPUB_MIMETYPE.equals(mimetypeContent)) {
        addError(
            issues,
            "PKG_007",
            "Mimetype content is \"" + mimetypeContent + "\", expected \"" + EPUB_MIMETYPE + "\"");
      }
    }

    validateContainerAndPackage(epubBytes, issues);

    return new DetailedValidationResult(issues);
  }

  /**
   * Deep validation using W3C epubcheck when available, falling back to the lightweight structural
   * validator otherwise.
   *
   * <p>Add {@code org.w3c:epubcheck} to your classpath to enable the full W3C conformance check.
   * Without it, only OCF/OPF structural rules are verified.
   *
   * @throws IOException if the file cannot be read
   */
  public static DetailedValidationResult validateFull(Path epubPath) throws IOException {
    if (EPUBCHECK_AVAILABLE) {
      return EpubCheckAdapter.validate(epubPath);
    }
    return validateDetailed(epubPath);
  }

  /**
   * Deep validation from an input stream.
   *
   * @see #validateFull(Path)
   */
  public static DetailedValidationResult validateFull(InputStream in) throws IOException {
    if (EPUBCHECK_AVAILABLE) {
      return EpubCheckAdapter.validate(in);
    }
    return validateDetailed(in);
  }

  /**
   * Validates and throws {@link EpubValidationException} if any errors are found. Uses epubcheck
   * when available.
   *
   * @throws EpubValidationException if the EPUB has validation errors
   */
  public static void validateOrThrow(Path epubPath) throws IOException {
    DetailedValidationResult result = validateFull(epubPath);
    if (result.hasErrors()) {
      throw new EpubValidationException(result);
    }
  }

  /**
   * Validates and throws {@link EpubValidationException} if any errors are found. Uses epubcheck
   * when available.
   *
   * @throws EpubValidationException if the EPUB has validation errors
   */
  public static void validateOrThrow(InputStream in) throws IOException {
    DetailedValidationResult result = validateFull(in);
    if (result.hasErrors()) {
      throw new EpubValidationException(result);
    }
  }

  private static void validateContainerAndPackage(byte[] epubBytes, List<ValidationIssue> issues) {
    Map<String, byte[]> entries = new LinkedHashMap<>();

    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(epubBytes))) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }

        String name = entry.getName();
        if (name.isBlank()) {
          addError(issues, "EPUB_001", "ZIP entry has an empty name");
          continue;
        }
        if (isUnsafePath(name)) {
          addError(issues, "EPUB_002", "ZIP entry has unsafe path traversal segments: " + name);
          continue;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        zin.transferTo(out);
        entries.put(name, out.toByteArray());
      }
    } catch (IOException e) {
      addError(
          issues,
          "EPUB_003",
          "Failed to read ZIP entries for semantic validation: " + e.getMessage());
      return;
    }

    if (!entries.containsKey("META-INF/container.xml")) {
      addError(issues, "EPUB_010", "Missing required container file META-INF/container.xml");
      return;
    }

    String packagePath;
    try {
      packagePath = extractPackagePath(entries.get("META-INF/container.xml"));
    } catch (Exception e) {
      addError(issues, "EPUB_011", "Failed to parse META-INF/container.xml: " + e.getMessage());
      return;
    }

    if (packagePath == null || packagePath.isBlank()) {
      addError(issues, "EPUB_012", "container.xml does not define a package full-path");
      return;
    }

    if (!entries.containsKey(packagePath)) {
      addError(issues, "EPUB_013", "Package document not found at declared path: " + packagePath);
      return;
    }

    try {
      validatePackageDocument(packagePath, entries.get(packagePath), issues);
    } catch (Exception e) {
      addError(
          issues,
          "EPUB_014",
          "Failed to parse package document " + packagePath + ": " + e.getMessage());
    }
  }

  private static void validatePackageDocument(
      String packagePath, byte[] packageBytes, List<ValidationIssue> issues) throws Exception {
    Document doc = parseXml(packageBytes);
    Element packageElement = doc.getDocumentElement();
    if (packageElement == null) {
      addError(issues, "EPUB_020", "Package document is empty: " + packagePath);
      return;
    }

    Element manifestElement = firstChildElementByLocalName(packageElement, "manifest");
    Element spineElement = firstChildElementByLocalName(packageElement, "spine");

    if (manifestElement == null) {
      addError(issues, "EPUB_021", "Package document missing manifest");
      return;
    }
    if (spineElement == null) {
      addError(issues, "EPUB_022", "Package document missing spine");
      return;
    }

    Set<String> manifestIds = new HashSet<>();
    boolean hasNav = false;
    boolean hasNcx = false;

    NodeList manifestNodes = manifestElement.getChildNodes();
    int manifestNodeCount = manifestNodes.getLength();
    for (int i = 0; i < manifestNodeCount; i++) {
      Node node = manifestNodes.item(i);
      if (!(node instanceof Element element) || !"item".equals(element.getLocalName())) {
        continue;
      }

      String id = element.getAttribute("id");
      String href = element.getAttribute("href");
      String mediaType = element.getAttribute("media-type");
      String properties = element.getAttribute("properties");

      if (id.isBlank()) {
        addError(issues, "EPUB_023", "Manifest item is missing id");
      } else if (!manifestIds.add(id)) {
        addError(issues, "EPUB_024", "Duplicate manifest id: " + id);
      }

      if (href.isBlank()) {
        addError(issues, "EPUB_025", "Manifest item " + id + " is missing href");
      }
      if (mediaType.isBlank()) {
        addError(issues, "EPUB_026", "Manifest item " + id + " is missing media-type");
      }

      if ("application/x-dtbncx+xml".equals(mediaType)) {
        hasNcx = true;
      }
      if (hasProperty(properties)) {
        hasNav = true;
      }
    }

    int itemrefCount = 0;
    NodeList spineNodes = spineElement.getChildNodes();
    int spineNodeCount = spineNodes.getLength();
    for (int i = 0; i < spineNodeCount; i++) {
      Node node = spineNodes.item(i);
      if (!(node instanceof Element element) || !"itemref".equals(element.getLocalName())) {
        continue;
      }
      itemrefCount++;
      String idref = element.getAttribute("idref");
      if (idref.isBlank()) {
        addError(issues, "EPUB_027", "Spine itemref is missing idref");
      } else if (!manifestIds.contains(idref)) {
        addError(issues, "EPUB_028", "Spine itemref references unknown manifest id: " + idref);
      }
    }

    if (itemrefCount == 0) {
      addWarning(issues, "EPUB_029", "Spine does not contain any itemref entries");
    }

    String version = packageElement.getAttribute("version");
    if (version.startsWith("3") && !hasNav) {
      addError(
          issues, "EPUB_030", "EPUB 3 package requires a manifest nav item (properties='nav')");
    }
    if (version.startsWith("2") && !hasNcx) {
      addWarning(issues, "EPUB_031", "EPUB 2 package has no NCX manifest item");
    }
  }

  private static void addError(List<ValidationIssue> issues, String code, String message) {
    issues.add(new ValidationIssue(code, ValidationSeverity.ERROR, message));
  }

  private static void addWarning(List<ValidationIssue> issues, String code, String message) {
    issues.add(new ValidationIssue(code, ValidationSeverity.WARNING, message));
  }

  private static String extractPackagePath(byte[] containerXml) throws Exception {
    Document doc = parseXml(containerXml);
    Element root = doc.getDocumentElement();
    if (root == null) {
      return null;
    }

    NodeList rootfiles = root.getElementsByTagNameNS("*", "rootfile");
    if (rootfiles.getLength() == 0) {
      return null;
    }

    Element rootfile = (Element) rootfiles.item(0);
    return rootfile.getAttribute("full-path");
  }

  private static Document parseXml(byte[] xmlBytes) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
    trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
    trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
    trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA);

    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(new ByteArrayInputStream(xmlBytes));
  }

  private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
    try {
      factory.setFeature(feature, value);
    } catch (Exception ignored) {
      // Not all XML parsers support every secure-processing feature.
    }
  }

  private static void trySetAttribute(DocumentBuilderFactory factory, String name) {
    try {
      factory.setAttribute(name, "");
    } catch (IllegalArgumentException ignored) {
      // Attribute not recognized by parser implementation.
    }
  }

  private static Element firstChildElementByLocalName(Element parent, String localName) {
    NodeList children = parent.getChildNodes();
    int childCount = children.getLength();
    for (int i = 0; i < childCount; i++) {
      Node node = children.item(i);
      if (node instanceof Element element && localName.equals(element.getLocalName())) {
        return element;
      }
    }
    return null;
  }

  private static boolean hasProperty(String properties) {
    if (properties == null || properties.isBlank()) {
      return false;
    }
    String value = properties.trim();
    int start = 0;
    int length = value.length();
    while (start < length) {
      while (start < length && Character.isWhitespace(value.charAt(start))) {
        start++;
      }
      if (start >= length) {
        break;
      }
      int end = start + 1;
      while (end < length && !Character.isWhitespace(value.charAt(end))) {
        end++;
      }
      if (end - start == 3
          && value.charAt(start) == 'n'
          && value.charAt(start + 1) == 'a'
          && value.charAt(start + 2) == 'v') {
        return true;
      }
      start = end + 1;
    }
    return false;
  }

  private static boolean isUnsafePath(String path) {
    if (path.startsWith("/") || path.startsWith("\\")) {
      return true;
    }
    String normalized = path.replace('\\', '/');
    return normalized.contains("../") || "..".equals(normalized) || normalized.contains(":/");
  }

  private static int readFully(InputStream in, byte[] buffer) throws IOException {
    int total = 0;
    int bufferLength = buffer.length;
    while (total < bufferLength) {
      int read = in.read(buffer, total, bufferLength - total);
      if (read == -1) break;
      total += read;
    }
    return total;
  }

  private static int getUint16LE(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
  }

  /** Result of EPUB structural validation. */
  public record ValidationResult(List<String> errors, List<String> warnings) {

    public ValidationResult {
      errors = List.copyOf(errors);
      warnings = List.copyOf(warnings);
    }

    /** Returns true if there are no errors (warnings are acceptable). */
    public boolean isValid() {
      return errors.isEmpty();
    }
  }

  public enum ValidationSeverity {
    ERROR,
    WARNING
  }

  public record ValidationIssue(String code, ValidationSeverity severity, String message) {

    @Override
    public String toString() {
      return code + ": " + message;
    }
  }

  public record DetailedValidationResult(List<ValidationIssue> issues) {

    public DetailedValidationResult {
      issues = List.copyOf(issues);
    }

    public List<ValidationIssue> errors() {
      List<ValidationIssue> result = new ArrayList<>(issues.size());
      for (ValidationIssue issue : issues) {
        if (issue.severity() == ValidationSeverity.ERROR) {
          result.add(issue);
        }
      }
      return List.copyOf(result);
    }

    public List<ValidationIssue> warnings() {
      List<ValidationIssue> result = new ArrayList<>(issues.size());
      for (ValidationIssue issue : issues) {
        if (issue.severity() == ValidationSeverity.WARNING) {
          result.add(issue);
        }
      }
      return List.copyOf(result);
    }

    public int errorCount() {
      return countBySeverity(ValidationSeverity.ERROR);
    }

    public int warningCount() {
      return countBySeverity(ValidationSeverity.WARNING);
    }

    private int countBySeverity(ValidationSeverity severity) {
      int count = 0;
      for (ValidationIssue issue : issues) {
        if (issue.severity() == severity) {
          count++;
        }
      }
      return count;
    }

    public boolean hasErrors() {
      return errorCount() > 0;
    }

    public boolean hasWarnings() {
      return warningCount() > 0;
    }

    /** Returns counts per issue code in insertion order. */
    public Map<String, Integer> issueCountsByCode() {
      Map<String, Integer> counts = new LinkedHashMap<>();
      for (ValidationIssue issue : issues) {
        String code = issue.code();
        Integer current = counts.get(code);
        counts.put(code, current == null ? 1 : current + 1);
      }
      return Map.copyOf(counts);
    }

    /** Builds a compact textual summary useful for logs and batch runs. */
    public String summary() {
      int errorCount = countBySeverity(ValidationSeverity.ERROR);
      int warningCount = countBySeverity(ValidationSeverity.WARNING);
      return "errors=" + errorCount + ", warnings=" + warningCount;
    }

    public boolean isValid() {
      return !hasErrors();
    }

    public ValidationResult toLegacyResult() {
      List<String> errorTexts = new ArrayList<>(issues.size());
      List<String> warningTexts = new ArrayList<>(issues.size());
      for (ValidationIssue issue : issues) {
        if (issue.severity() == ValidationSeverity.ERROR) {
          errorTexts.add(issue.toString());
        } else {
          warningTexts.add(issue.toString());
        }
      }
      return new ValidationResult(errorTexts, warningTexts);
    }
  }

  private static boolean probeEpubCheck() {
    try {
      Class.forName(
          "com.adobe.epubcheck.api.EpubCheck", false, EpubValidator.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
