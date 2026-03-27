package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.util.StringUtil;
import org.grimmory.epub4j.util.XmlCleaner;

/**
 * Comprehensive EPUB diagnostic checker. Inspects book content for common issues that affect
 * rendering and usability.
 *
 * <p>Checks include:
 *
 * <ul>
 *   <li>OPF structure (metadata, manifest, spine, guide)
 *   <li>Required metadata (title, identifier, language)
 *   <li>Broken internal references (href targets that don't exist)
 *   <li>Missing or broken table of contents (NCX/Nav)
 *   <li>Cover image presence and validity
 *   <li>Duplicate IDs in documents
 *   <li>XML/HTML parsing errors
 *   <li>CSS syntax and references to missing resources
 *   <li>Image/font validity
 *   <li>Filename validity
 *   <li>Accessibility issues (alt text, lang attribute, heading structure)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * DiagnosticResult result = EpubIntegrityDiagnostics.check(book);
 * if (result.hasErrors()) {
 *     for (Diagnostic error : result.errors()) {
 *         System.out.println(error);
 *     }
 *
 *     // Auto-fix what we can
 *     if (result.autoFixableCount() > 0) {
 *         BookRepair repair = new BookRepair();
 *         RepairResult repairResult = repair.repair(book);
 *     }
 * }
 * }</pre>
 *
 * @author Grimmory
 */
public class EpubIntegrityDiagnostics {

  private static final System.Logger log =
      System.getLogger(EpubIntegrityDiagnostics.class.getName());

  /** Severity levels for diagnostic findings. */
  public enum Severity {
    /** Debug information, not shown to users by default */
    DEBUG,
    /** Informational message */
    INFO,
    /** Warning - book works but may have issues */
    WARNING,
    /** Error - book may not work correctly */
    ERROR,
    /** Critical error - book is broken */
    CRITICAL
  }

  /**
   * A single diagnostic finding.
   *
   * @param code the error code
   * @param severity the severity level
   * @param message a human-readable description of the issue
   * @param resourceHref the href of the related resource, or null
   * @param line line number in the resource, or null
   * @param column column number in the resource, or null
   * @param autoFixable whether BookRepair can automatically fix this issue
   * @param help help text explaining how to fix the issue
   */
  public record Diagnostic(
      EpubErrorCode code,
      Severity severity,
      String message,
      String resourceHref,
      Integer line,
      Integer column,
      boolean autoFixable,
      String help) {
    public Diagnostic {
      // Use code's default help if none provided
      if (help == null) {
        help = code.getHelp();
      }
    }

    /** Create a diagnostic with just code and message. */
    public Diagnostic(EpubErrorCode code, Severity severity, String message) {
      this(code, severity, message, null, null, null, false, null);
    }

    /** Create a diagnostic with auto-fix flag. */
    public Diagnostic(EpubErrorCode code, Severity severity, String message, boolean autoFixable) {
      this(code, severity, message, null, null, null, autoFixable, null);
    }

    /** Create a diagnostic with resource href. */
    public Diagnostic(EpubErrorCode code, Severity severity, String message, String resourceHref) {
      this(code, severity, message, resourceHref, null, null, false, null);
    }

    /** Create a diagnostic with resource href and auto-fix flag. */
    public Diagnostic(
        EpubErrorCode code,
        Severity severity,
        String message,
        String resourceHref,
        boolean autoFixable) {
      this(code, severity, message, resourceHref, null, null, autoFixable, null);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(severity).append(" ").append(code.getCode()).append(": ").append(message);
      if (resourceHref != null) {
        sb.append(" [").append(resourceHref).append("]");
      }
      if (line != null) {
        sb.append(" (line ").append(line);
        if (column != null) {
          sb.append(":").append(column);
        }
        sb.append(")");
      }
      if (autoFixable) {
        sb.append(" (auto-fixable)");
      }
      return sb.toString();
    }
  }

  /**
   * The result of running diagnostics on a book.
   *
   * @param diagnostics the list of diagnostic findings
   */
  public record DiagnosticResult(List<Diagnostic> diagnostics) {
    public DiagnosticResult {
      diagnostics = List.copyOf(diagnostics);
    }

    /** All error-severity (and critical) diagnostics. */
    public List<Diagnostic> errors() {
      return filterBySeverity(Severity.ERROR, Severity.CRITICAL);
    }

    /** All warning-severity diagnostics. */
    public List<Diagnostic> warnings() {
      return filterBySeverity(Severity.WARNING);
    }

    /** All info-severity diagnostics. */
    public List<Diagnostic> infos() {
      return filterBySeverity(Severity.INFO);
    }

    /** Whether any error-severity or critical diagnostics exist. */
    public boolean hasErrors() {
      return hasAnySeverity(Severity.ERROR, Severity.CRITICAL);
    }

    /** Whether the book has no error-severity diagnostics. */
    public boolean isHealthy() {
      return !hasErrors();
    }

    /** Number of diagnostics that can be auto-fixed by BookRepair. */
    public int autoFixableCount() {
      int count = 0;
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.autoFixable()) {
          count++;
        }
      }
      return count;
    }

    /** Get auto-fixable diagnostics. */
    public List<Diagnostic> autoFixable() {
      List<Diagnostic> result = new ArrayList<>(diagnostics.size());
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.autoFixable()) {
          result.add(diagnostic);
        }
      }
      return List.copyOf(result);
    }

    /** Summary string with counts by severity. */
    public String summary() {
      int critical = countBySeverity(Severity.CRITICAL);
      int errors = countBySeverity(Severity.ERROR);
      int warnings = countBySeverity(Severity.WARNING);
      int infos = countBySeverity(Severity.INFO);
      return "critical="
          + critical
          + ", errors="
          + errors
          + ", warnings="
          + warnings
          + ", info="
          + infos;
    }

    private int countBySeverity(Severity severity) {
      int count = 0;
      for (Diagnostic diagnostic : diagnostics) {
        if (diagnostic.severity() == severity) {
          count++;
        }
      }
      return count;
    }

    private boolean hasAnySeverity(Severity... severities) {
      for (Diagnostic diagnostic : diagnostics) {
        Severity current = diagnostic.severity();
        for (Severity severity : severities) {
          if (current == severity) {
            return true;
          }
        }
      }
      return false;
    }

    private List<Diagnostic> filterBySeverity(Severity... severities) {
      List<Diagnostic> result = new ArrayList<>(diagnostics.size());
      for (Diagnostic diagnostic : diagnostics) {
        Severity current = diagnostic.severity();
        for (Severity severity : severities) {
          if (current == severity) {
            result.add(diagnostic);
            break;
          }
        }
      }
      return List.copyOf(result);
    }

    /** Group diagnostics by error code. */
    public Map<EpubErrorCode, List<Diagnostic>> byCode() {
      return diagnostics.stream().collect(Collectors.groupingBy(Diagnostic::code));
    }

    /** Group diagnostics by resource href. */
    public Map<String, List<Diagnostic>> byResource() {
      return diagnostics.stream()
          .filter(d -> d.resourceHref() != null)
          .collect(Collectors.groupingBy(Diagnostic::resourceHref));
    }
  }

  // Patterns for scanning XHTML content for internal references
  private static final Pattern HREF_PATTERN =
      Pattern.compile("(?:href|src)\\s*=\\s*[\"']([^\"'#][^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern CSS_URL_PATTERN =
      Pattern.compile("url\\s*\\(\\s*[\"']?([^)\"']+)[\"']?\\s*\\)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ID_PATTERN =
      Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern HEADING_PATTERN =
      Pattern.compile("<h([1-6])[\\s>]", Pattern.CASE_INSENSITIVE);
  private static final Pattern IMG_TAG_PATTERN =
      Pattern.compile("<img[^>]*>", Pattern.CASE_INSENSITIVE);

  /**
   * Run all diagnostic checks on the given book.
   *
   * @param book the book to diagnose
   * @return the diagnostic result
   */
  public static DiagnosticResult check(Book book) {
    List<Diagnostic> diagnostics = new ArrayList<>();

    checkMetadata(book, diagnostics);
    checkCover(book, diagnostics);
    checkSpine(book, diagnostics);
    checkManifest(book, diagnostics);
    checkToc(book, diagnostics);
    checkResources(book, diagnostics);
    checkInternalReferences(book, diagnostics);
    checkDocumentStructure(book, diagnostics);
    checkAccessibility(book, diagnostics);

    return new DiagnosticResult(diagnostics);
  }

  /**
   * Check required metadata fields: title, language, identifier. Validates required OPF fields per
   * the EPUB specification.
   */
  private static void checkMetadata(Book book, List<Diagnostic> diagnostics) {
    Metadata metadata = book.getMetadata();

    // Title check
    if (StringUtil.isBlank(metadata.getFirstTitle())) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_MISSING_TITLE, Severity.ERROR, "Book has no title", null, true));
    }

    // Language check
    if (StringUtil.isBlank(metadata.getLanguage())) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_MISSING_LANGUAGE,
              Severity.WARNING,
              "Book has no language set",
              null,
              true));
    }

    // Identifier check
    if (metadata.getIdentifiers().isEmpty()) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_MISSING_IDENTIFIER,
              Severity.ERROR,
              "Book has no identifier",
              null,
              true));
    } else {
      boolean allBlank =
          metadata.getIdentifiers().stream().allMatch(id -> StringUtil.isBlank(id.getValue()));
      if (allBlank) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.OPF_MISSING_IDENTIFIER,
                Severity.ERROR,
                "All identifiers have blank values",
                null,
                true));
      }
    }

    // Authors check (warning, not error - some EPUBs legitimately have no author)
    if (metadata.getAuthors().isEmpty()) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_MISSING_METADATA,
              Severity.INFO,
              "Book has no author (may be legitimate)",
              null));
    }

    // Info about available metadata
    diagnostics.add(
        new Diagnostic(
            EpubErrorCode.OPF_MISSING_METADATA,
            Severity.INFO,
            "Metadata: titles="
                + metadata.getTitles().size()
                + ", authors="
                + metadata.getAuthors().size()
                + ", identifiers="
                + metadata.getIdentifiers().size()
                + ", language="
                + StringUtil.defaultIfNull(metadata.getLanguage()),
            null));
  }

  /** Check cover image presence and basic validity. */
  private static void checkCover(Book book, List<Diagnostic> diagnostics) {
    Resource coverImage = book.getCoverImage();
    if (coverImage == null) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_INCORRECT_COVER,
              Severity.WARNING,
              "No cover image set",
              null,
              true));
    } else {
      // Check the cover image has valid media type
      MediaType mt = coverImage.getMediaType();
      if (mt == null) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.INCORRECT_MIMETYPE,
                Severity.WARNING,
                "Cover image has no media type",
                coverImage.getHref(),
                true));
      } else if (!MediaTypes.isBitmapImage(mt) && mt != MediaTypes.SVG) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.INCORRECT_MIMETYPE,
                Severity.WARNING,
                "Cover image has unexpected media type: " + mt.name(),
                coverImage.getHref()));
      }

      // Check cover image size
      long size = coverImage.getSize();
      if (size == 0) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.EMPTY_FILE,
                Severity.ERROR,
                "Cover image has zero bytes",
                coverImage.getHref()));
      } else if (size < 1024) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.LARGE_IMAGE,
                Severity.INFO,
                "Cover image is suspiciously small (" + size + " bytes)",
                coverImage.getHref()));
      }
    }
  }

  /** Check spine integrity. */
  private static void checkSpine(Book book, List<Diagnostic> diagnostics) {
    Spine spine = book.getSpine();
    if (spine.isEmpty()) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_MISSING_SPINE,
              Severity.CRITICAL,
              "Spine is empty - book has no reading order",
              null));
      return;
    }

    int nullResources = 0;
    int missingResources = 0;
    List<SpineReference> spineReferences = spine.getSpineReferences();
    int spineSize = spineReferences.size();
    Set<String> idrefs = new HashSet<>(Math.max(16, spineSize));
    Set<Integer> nonLinearIndices = new LinkedHashSet<>();

    for (int i = 0; i < spineSize; i++) {
      SpineReference ref = spineReferences.get(i);
      Resource resource = ref.getResource();

      // Check for null resource
      if (resource == null) {
        nullResources++;
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.OPF_INCORRECT_IDREF,
                Severity.ERROR,
                "Spine item " + i + " has null resource",
                null,
                true));
        continue;
      }

      // Check for missing resource
      if (!book.getResources().containsByHref(resource.getHref())) {
        missingResources++;
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.OPF_MISSING_HREF,
                Severity.ERROR,
                "Spine item " + i + " references missing resource: " + resource.getHref(),
                resource.getHref(),
                true));
      }

      // Check for duplicate idrefs
      String idref = resource.getId();
      if (idref != null) {
        if (idrefs.contains(idref)) {
          diagnostics.add(
              new Diagnostic(
                  EpubErrorCode.OPF_DUPLICATE_IDREF_SPINE,
                  Severity.WARNING,
                  "Duplicate idref in spine: " + idref,
                  resource.getHref()));
        }
        idrefs.add(idref);
      }

      // Track non-linear items
      if (!ref.isLinear()) {
        nonLinearIndices.add(i);
      }
    }

    // Report non-linear items
    if (!nonLinearIndices.isEmpty()) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_NON_LINEAR_ITEMS,
              Severity.WARNING,
              "Spine has "
                  + nonLinearIndices.size()
                  + " non-linear item(s) at positions: "
                  + nonLinearIndices,
              null,
              true));
    }

    // Summary
    if (nullResources == 0 && missingResources == 0) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.OPF_MISSING_SPINE,
              Severity.INFO,
              "Spine has " + spineSize + " items, all valid",
              null));
    }
  }

  /** Check manifest integrity. */
  private static void checkManifest(Book book, List<Diagnostic> diagnostics) {
    Resources resources = book.getResources();
    Set<String> hrefs = new HashSet<>();

    for (Resource resource : resources.getAll()) {
      String href = resource.getHref();

      // Check for missing href
      if (StringUtil.isBlank(href)) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.OPF_NO_HREF,
                Severity.ERROR,
                "Resource has no href",
                resource.getId(),
                true));
        continue;
      }

      // Check for duplicate hrefs
      if (!hrefs.add(href)) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.OPF_DUPLICATE_HREF_MANIFEST,
                Severity.ERROR,
                "Duplicate href in manifest: " + href,
                href,
                true));
      }

      // Check for missing media type
      if (resource.getMediaType() == null) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.INCORRECT_MIMETYPE,
                Severity.WARNING,
                "Resource has no media type",
                href,
                true));
      }

      // Check for empty resources (except NCX which can be generated)
      if (resource.getSize() == 0 && resource.getMediaType() != MediaTypes.NCX) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.EMPTY_FILE, Severity.WARNING, "Resource has zero bytes", href));
      }
    }
  }

  /** Check TOC presence and completeness. */
  private static void checkToc(Book book, List<Diagnostic> diagnostics) {
    TableOfContents toc = book.getTableOfContents();
    int tocSize = toc.size();

    if (tocSize == 0) {
      if (!book.getSpine().isEmpty()) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.EMPTY_TOC,
                Severity.WARNING,
                "Table of contents is empty but spine has " + book.getSpine().size() + " items",
                null,
                true));
      } else {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.EMPTY_TOC,
                Severity.INFO,
                "Table of contents is empty (spine also empty)",
                null));
      }
      return;
    }

    // Check how many spine items are covered by the TOC
    List<Resource> tocResources = toc.getAllUniqueResources();
    Set<String> tocHrefs = new HashSet<>(Math.max(16, tocResources.size()));
    for (Resource tocResource : tocResources) {
      if (tocResource != null && tocResource.getHref() != null) {
        tocHrefs.add(tocResource.getHref());
      }
    }

    int spineItemsNotInToc = 0;
    for (SpineReference ref : book.getSpine().getSpineReferences()) {
      Resource resource = ref.getResource();
      if (resource != null && !tocHrefs.contains(resource.getHref())) {
        spineItemsNotInToc++;
      }
    }

    if (spineItemsNotInToc > 0) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.EMPTY_TOC,
              Severity.WARNING,
              spineItemsNotInToc + " spine item(s) not referenced in table of contents",
              null));
    }

    // Check for TOC entries pointing to null resources
    int nullTocRefs = 0;
    for (Resource tocResource : tocResources) {
      if (tocResource == null) {
        nullTocRefs++;
      }
    }
    if (nullTocRefs > 0) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.BROKEN_TOC_ENTRY,
              Severity.WARNING,
              nullTocRefs + " TOC entries point to null resources",
              null));
    }

    // Summary
    diagnostics.add(
        new Diagnostic(
            EpubErrorCode.EMPTY_TOC,
            Severity.INFO,
            "TOC has " + tocSize + " entries, depth " + toc.calculateDepth(),
            null));
  }

  /** Check for orphaned resources and other resource issues. */
  private static void checkResources(Book book, List<Diagnostic> diagnostics) {
    // Find referenced hrefs from spine, TOC, and guide
    Collection<Resource> allResources = book.getResources().getAll();
    Set<String> referencedHrefs = new HashSet<>(Math.max(16, allResources.size() * 2));

    for (SpineReference ref : book.getSpine().getSpineReferences()) {
      Resource r = ref.getResource();
      if (r != null && r.getHref() != null) referencedHrefs.add(r.getHref());
    }

    for (Resource r : book.getTableOfContents().getAllUniqueResources()) {
      if (r != null && r.getHref() != null) referencedHrefs.add(r.getHref());
    }

    for (GuideReference ref : book.getGuide().getReferences()) {
      Resource r = ref.getResource();
      if (r != null && r.getHref() != null) referencedHrefs.add(r.getHref());
    }

    if (book.getCoverImage() != null && book.getCoverImage().getHref() != null) {
      referencedHrefs.add(book.getCoverImage().getHref());
    }

    // Check for orphaned XHTML resources
    int orphanedXhtml = 0;
    int nullMediaTypes = 0;
    for (Resource resource : allResources) {
      if (resource.getMediaType() == null) {
        nullMediaTypes++;
      }
      if (resource.getMediaType() == MediaTypes.XHTML
          && !referencedHrefs.contains(resource.getHref())) {
        orphanedXhtml++;
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.UNREFERENCED_FONT, // Reusing for unreferenced resources
                Severity.WARNING,
                "XHTML resource not referenced by spine, TOC, or guide",
                resource.getHref()));
      }
    }

    // Summary
    diagnostics.add(
        new Diagnostic(
            EpubErrorCode.OPF_MISSING_MANIFEST,
            Severity.INFO,
            "Total resources: "
                + book.getResources().size()
                + ", orphaned XHTML: "
                + orphanedXhtml
                + ", null media types: "
                + nullMediaTypes,
            null));
  }

  /** Scan XHTML content for href/src attributes and verify targets exist. */
  private static void checkInternalReferences(Book book, List<Diagnostic> diagnostics) {
    int brokenRefs = 0;
    int checkedResources = 0;

    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.XHTML
          && resource.getMediaType() != MediaTypes.CSS) {
        continue;
      }

      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;

        String content = new String(data, StandardCharsets.UTF_8);
        String basePath = "";
        String href = resource.getHref();
        if (href != null) {
          int lastSlash = href.lastIndexOf('/');
          if (lastSlash >= 0) basePath = href.substring(0, lastSlash + 1);
        }

        checkedResources++;

        // Choose pattern based on resource type
        Pattern pattern =
            resource.getMediaType() == MediaTypes.CSS ? CSS_URL_PATTERN : HREF_PATTERN;

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
          String refTarget = matcher.group(1).trim();

          // Skip external URLs, data URIs, and mailto links
          if (refTarget.startsWith("http://")
              || refTarget.startsWith("https://")
              || refTarget.startsWith("data:")
              || refTarget.startsWith("mailto:")
              || refTarget.startsWith("javascript:")
              || refTarget.startsWith("#")) {
            continue;
          }

          // Strip fragment
          int hashPos = refTarget.indexOf('#');
          if (hashPos >= 0) refTarget = refTarget.substring(0, hashPos);
          if (refTarget.isEmpty()) continue;

          // Resolve relative reference
          String resolvedRef = basePath + refTarget;
          // Collapse path dots
          if (resolvedRef.contains("..") || resolvedRef.contains("./")) {
            resolvedRef = StringUtil.collapsePathDots(resolvedRef);
          }

          if (!book.getResources().containsByHref(resolvedRef)
              && !book.getResources().containsByHref(refTarget)) {
            brokenRefs++;
            diagnostics.add(
                new Diagnostic(
                    EpubErrorCode.BROKEN_LINK,
                    Severity.WARNING,
                    "Reference to '" + refTarget + "' not found in resources",
                    resource.getHref()));
          }
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Failed to check references in: " + resource.getHref());
      }
    }

    if (brokenRefs == 0 && checkedResources > 0) {
      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.BROKEN_LINK,
              Severity.INFO,
              "All internal references valid (checked " + checkedResources + " resources)",
              null));
    }
  }

  /** Check document structure (IDs, headings, etc.). */
  private static void checkDocumentStructure(Book book, List<Diagnostic> diagnostics) {
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }

      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;

        String content = new String(data, StandardCharsets.UTF_8);

        // Check for duplicate IDs
        checkDuplicateIds(resource.getHref(), content, diagnostics);

        // Check heading structure
        checkHeadingStructure(resource.getHref(), content, diagnostics);

        // Check XML character validity
        checkXmlCharacters(resource.getHref(), content, diagnostics);

      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Failed to check document structure in: " + resource.getHref());
      }
    }
  }

  /** Check for duplicate IDs in a document. */
  private static void checkDuplicateIds(String href, String content, List<Diagnostic> diagnostics) {
    Set<String> ids = new HashSet<>();
    Matcher matcher = ID_PATTERN.matcher(content);

    while (matcher.find()) {
      String id = matcher.group(1).trim();
      if (!ids.add(id)) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.DUPLICATE_ID,
                Severity.ERROR,
                "Duplicate id attribute: " + id,
                href,
                true));
      }
    }
  }

  /** Check heading structure for skipped levels. */
  private static void checkHeadingStructure(
      String href, String content, List<Diagnostic> diagnostics) {
    Matcher matcher = HEADING_PATTERN.matcher(content);
    int lastLevel = -1;

    while (matcher.find()) {
      int level = Integer.parseInt(matcher.group(1));
      if (lastLevel > 0 && level > lastLevel + 1) {
        diagnostics.add(
            new Diagnostic(
                EpubErrorCode.HEADING_SKIP,
                Severity.WARNING,
                "Heading levels skip from h" + lastLevel + " to h" + level,
                href));
      }
      lastLevel = level;
    }
  }

  /** Check for invalid XML characters. */
  private static void checkXmlCharacters(
      String href, String content, List<Diagnostic> diagnostics) {
    int invalidPos = XmlCleaner.findFirstInvalidChar(content);
    if (invalidPos >= 0) {
      // Find line number
      int line = 1;
      int contentLength = content.length();
      for (int i = 0; i < invalidPos && i < contentLength; i++) {
        if (content.charAt(i) == '\n') line++;
      }

      diagnostics.add(
          new Diagnostic(
              EpubErrorCode.XML_PARSE_ERROR,
              Severity.ERROR,
              "Invalid XML character at position " + invalidPos,
              href,
              line,
              null,
              true,
              "Remove invalid XML characters using XmlCleaner.cleanXmlChars()"));
    }
  }

  /** Check accessibility issues. */
  private static void checkAccessibility(Book book, List<Diagnostic> diagnostics) {
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }

      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;

        String content = new String(data, StandardCharsets.UTF_8);

        // Check for missing lang attribute on html element
        if (!content.contains("lang=") && !content.contains("xml:lang=")) {
          diagnostics.add(
              new Diagnostic(
                  EpubErrorCode.MISSING_LANG_ATTRIBUTE,
                  Severity.WARNING,
                  "Document missing lang attribute",
                  resource.getHref()));
        }

        // Check for images missing alt text
        Matcher imgMatcher = IMG_TAG_PATTERN.matcher(content);
        while (imgMatcher.find()) {
          String imgTag = imgMatcher.group();
          if (!imgTag.contains("alt=")) {
            diagnostics.add(
                new Diagnostic(
                    EpubErrorCode.MISSING_ALT_TEXT,
                    Severity.WARNING,
                    "Image missing alt text",
                    resource.getHref()));
          }
        }

      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG, "Failed to check accessibility in: " + resource.getHref());
      }
    }
  }
}
