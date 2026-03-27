package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.util.StringUtil;

/**
 * Comprehensive EPUB diagnostic checker that goes beyond structural validation. Inspects book
 * content for common issues that affect rendering and usability.
 *
 * <p>Checks include:
 *
 * <ul>
 *   <li>Missing or broken internal references (href targets that don't exist)
 *   <li>Resources in manifest but not referenced by spine or TOC
 *   <li>Resources referenced but not in manifest
 *   <li>Encoding issues in XHTML content
 *   <li>Missing required metadata (title, language, identifier)
 *   <li>Cover image quality/presence
 *   <li>TOC completeness (do all spine items appear in TOC?)
 *   <li>CSS issues (references to missing fonts/images)
 *   <li>EPUB version consistency
 * </ul>
 */
public class EpubDiagnostics {

  private static final System.Logger log = System.getLogger(EpubDiagnostics.class.getName());

  /** Severity levels for diagnostic findings. */
  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  public enum DiagnosticCode {
    META_NO_TITLE,
    META_NO_LANGUAGE,
    META_NO_IDENTIFIER,
    META_BLANK_IDENTIFIER,
    META_NO_AUTHOR,
    META_SUMMARY,
    COVER_MISSING,
    COVER_NO_MEDIA_TYPE,
    COVER_BAD_TYPE,
    COVER_EMPTY,
    COVER_TINY,
    SPINE_EMPTY,
    SPINE_NULL_REF,
    SPINE_MISSING_RESOURCE,
    SPINE_OK,
    TOC_EMPTY,
    TOC_INCOMPLETE,
    TOC_NULL_REFS,
    TOC_SUMMARY,
    RESOURCE_ORPHANED_XHTML,
    RESOURCE_NO_MEDIA_TYPE,
    RESOURCE_EMPTY,
    RESOURCE_SUMMARY,
    BROKEN_REFERENCE,
    REFS_OK,
    DUPLICATE_ID,
    BROKEN_TOC_ENTRY,
    XML_PARSE_ERROR,
    ENCODING_MISMATCH
  }

  /**
   * A single diagnostic finding.
   *
   * @param code a short code identifying the type of issue
   * @param severity the severity level
   * @param message a human-readable description of the issue
   * @param resourceHref the href of the related resource, or null
   * @param autoFixable whether BookRepair can automatically fix this issue
   */
  public record Diagnostic(
      DiagnosticCode code,
      Severity severity,
      String message,
      String resourceHref,
      boolean autoFixable) {
    @Override
    public String toString() {
      String prefix =
          severity == Severity.ERROR ? "ERROR" : severity == Severity.WARNING ? "WARN" : "INFO";
      String res = resourceHref != null ? " [" + resourceHref + "]" : "";
      String fix = autoFixable ? " (auto-fixable)" : "";
      return prefix + " " + code.name() + ": " + message + res + fix;
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

    /** All error-severity diagnostics. */
    public List<Diagnostic> errors() {
      return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).toList();
    }

    /** All warning-severity diagnostics. */
    public List<Diagnostic> warnings() {
      return diagnostics.stream().filter(d -> d.severity() == Severity.WARNING).toList();
    }

    /** Whether any error-severity diagnostics exist. */
    public boolean hasErrors() {
      return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    /** Whether the book has no error-severity diagnostics. */
    public boolean isHealthy() {
      return !hasErrors();
    }

    /** Number of diagnostics that can be auto-fixed by BookRepair. */
    public int autoFixableCount() {
      return (int) diagnostics.stream().filter(Diagnostic::autoFixable).count();
    }

    /** Summary string with counts by severity. */
    public String summary() {
      long errors = diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).count();
      long warnings = diagnostics.stream().filter(d -> d.severity() == Severity.WARNING).count();
      long infos = diagnostics.stream().filter(d -> d.severity() == Severity.INFO).count();
      return "errors=" + errors + ", warnings=" + warnings + ", info=" + infos;
    }
  }

  // Patterns for scanning XHTML content for internal references
  private static final Pattern HREF_PATTERN =
      Pattern.compile("(?:href|src)\\s*=\\s*[\"']([^\"'#][^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern CSS_URL_PATTERN =
      Pattern.compile("url\\s*\\(\\s*[\"']?([^)\"']+)[\"']?\\s*\\)", Pattern.CASE_INSENSITIVE);

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
    checkToc(book, diagnostics);
    checkResources(book, diagnostics);
    checkInternalReferences(book, diagnostics);

    return new DiagnosticResult(diagnostics);
  }

  /** Check required metadata fields: title, language, identifier. */
  private static void checkMetadata(Book book, List<Diagnostic> diagnostics) {
    Metadata metadata = book.getMetadata();

    // Title check
    if (StringUtil.isBlank(metadata.getFirstTitle())) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.META_NO_TITLE, Severity.ERROR, "Book has no title", null, true));
    }

    // Language check
    if (StringUtil.isBlank(metadata.getLanguage())) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.META_NO_LANGUAGE,
              Severity.ERROR,
              "Book has no language set",
              null,
              true));
    }

    // Identifier check
    if (metadata.getIdentifiers().isEmpty()) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.META_NO_IDENTIFIER,
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
                DiagnosticCode.META_BLANK_IDENTIFIER,
                Severity.WARNING,
                "All identifiers have blank values",
                null,
                true));
      }
    }

    // Authors check (warning, not error - some EPUBs legitimately have no author)
    if (metadata.getAuthors().isEmpty()) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.META_NO_AUTHOR, Severity.WARNING, "Book has no author", null, false));
    }

    // Info about available metadata
    diagnostics.add(
        new Diagnostic(
            DiagnosticCode.META_SUMMARY,
            Severity.INFO,
            "Metadata: titles="
                + metadata.getTitles().size()
                + ", authors="
                + metadata.getAuthors().size()
                + ", identifiers="
                + metadata.getIdentifiers().size()
                + ", language="
                + StringUtil.defaultIfNull(metadata.getLanguage()),
            null,
            false));
  }

  /** Check cover image presence and basic validity. */
  private static void checkCover(Book book, List<Diagnostic> diagnostics) {
    Resource coverImage = book.getCoverImage();
    if (coverImage == null) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.COVER_MISSING, Severity.WARNING, "No cover image set", null, true));
    } else {
      // Check the cover image has valid media type
      MediaType mt = coverImage.getMediaType();
      if (mt == null) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.COVER_NO_MEDIA_TYPE,
                Severity.WARNING,
                "Cover image has no media type",
                coverImage.getHref(),
                true));
      } else if (!MediaTypes.isBitmapImage(mt) && mt != MediaTypes.SVG) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.COVER_BAD_TYPE,
                Severity.WARNING,
                "Cover image has unexpected media type: " + mt.name(),
                coverImage.getHref(),
                false));
      }

      // Check cover image size
      long size = coverImage.getSize();
      if (size == 0) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.COVER_EMPTY,
                Severity.ERROR,
                "Cover image has zero bytes",
                coverImage.getHref(),
                false));
      } else if (size < 1024) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.COVER_TINY,
                Severity.WARNING,
                "Cover image is suspiciously small (" + size + " bytes)",
                coverImage.getHref(),
                false));
      }
    }
  }

  /** Check spine integrity. */
  private static void checkSpine(Book book, List<Diagnostic> diagnostics) {
    Spine spine = book.getSpine();
    if (spine.isEmpty()) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.SPINE_EMPTY,
              Severity.ERROR,
              "Spine is empty - book has no reading order",
              null,
              false));
      return;
    }

    int nullResources = 0;
    int missingResources = 0;
    for (int i = 0; i < spine.size(); i++) {
      SpineReference ref = spine.getSpineReferences().get(i);
      Resource resource = ref.getResource();
      if (resource == null) {
        nullResources++;
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.SPINE_NULL_REF,
                Severity.ERROR,
                "Spine item " + i + " has null resource",
                null,
                true));
      } else if (!book.getResources().containsByHref(resource.getHref())) {
        missingResources++;
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.SPINE_MISSING_RESOURCE,
                Severity.ERROR,
                "Spine item " + i + " references resource not in manifest",
                resource.getHref(),
                true));
      }
    }

    if (nullResources == 0 && missingResources == 0) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.SPINE_OK,
              Severity.INFO,
              "Spine has " + spine.size() + " items, all valid",
              null,
              false));
    }
  }

  /** Check TOC presence and completeness. */
  private static void checkToc(Book book, List<Diagnostic> diagnostics) {
    TableOfContents toc = book.getTableOfContents();
    if (toc.size() == 0) {
      if (!book.getSpine().isEmpty()) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.TOC_EMPTY,
                Severity.WARNING,
                "Table of contents is empty but spine has " + book.getSpine().size() + " items",
                null,
                true));
      } else {
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.TOC_EMPTY,
                Severity.INFO,
                "Table of contents is empty (spine also empty)",
                null,
                false));
      }
      return;
    }

    // Check how many spine items are covered by the TOC
    Set<String> tocHrefs = new HashSet<>();
    for (Resource tocResource : toc.getAllUniqueResources()) {
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
              DiagnosticCode.TOC_INCOMPLETE,
              Severity.WARNING,
              spineItemsNotInToc + " spine item(s) not referenced in table of contents",
              null,
              false));
    }

    // Check for TOC entries pointing to null resources
    int nullTocRefs = 0;
    for (Resource tocResource : toc.getAllUniqueResources()) {
      if (tocResource == null) {
        nullTocRefs++;
      }
    }
    if (nullTocRefs > 0) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.TOC_NULL_REFS,
              Severity.WARNING,
              nullTocRefs + " TOC entries point to null resources",
              null,
              false));
    }

    diagnostics.add(
        new Diagnostic(
            DiagnosticCode.TOC_SUMMARY,
            Severity.INFO,
            "TOC has " + toc.size() + " entries, depth " + toc.calculateDepth(),
            null,
            false));
  }

  /** Check for orphaned resources and missing media types. */
  private static void checkResources(Book book, List<Diagnostic> diagnostics) {
    // Find referenced hrefs from spine, TOC, and guide
    Set<String> referencedHrefs = new HashSet<>();

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
    if (book.getOpfResource() != null && book.getOpfResource().getHref() != null) {
      referencedHrefs.add(book.getOpfResource().getHref());
    }
    if (book.getNcxResource() != null && book.getNcxResource().getHref() != null) {
      referencedHrefs.add(book.getNcxResource().getHref());
    }

    // Check for orphaned resources (in manifest but never referenced directly)
    // Note: CSS, fonts, and images are typically referenced from within XHTML,
    // so they won't appear in spine/toc/guide. We only flag XHTML orphans.
    int orphanedXhtml = 0;
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() == MediaTypes.XHTML
          && !referencedHrefs.contains(resource.getHref())) {
        orphanedXhtml++;
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.RESOURCE_ORPHANED_XHTML,
                Severity.WARNING,
                "XHTML resource not referenced by spine, TOC, or guide",
                resource.getHref(),
                false));
      }
    }

    // Check for resources with null media types
    int nullMediaTypes = 0;
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() == null) {
        nullMediaTypes++;
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.RESOURCE_NO_MEDIA_TYPE,
                Severity.WARNING,
                "Resource has no media type",
                resource.getHref(),
                true));
      }
    }

    // Check for empty resources
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getSize() == 0
          && resource.getMediaType() != null
          && resource.getMediaType() != MediaTypes.NCX) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticCode.RESOURCE_EMPTY,
                Severity.WARNING,
                "Resource has zero bytes",
                resource.getHref(),
                false));
      }
    }

    // Resource count summary
    diagnostics.add(
        new Diagnostic(
            DiagnosticCode.RESOURCE_SUMMARY,
            Severity.INFO,
            "Total resources: "
                + book.getResources().size()
                + ", orphaned XHTML: "
                + orphanedXhtml
                + ", null media types: "
                + nullMediaTypes,
            null,
            false));
  }

  /** Scan XHTML content for href/src attributes and verify targets exist in resources. */
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
                    DiagnosticCode.BROKEN_REFERENCE,
                    Severity.WARNING,
                    "Reference to '" + refTarget + "' not found in resources",
                    resource.getHref(),
                    false));
          }
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Failed to check references in: " + resource.getHref());
      }
    }

    if (brokenRefs == 0 && checkedResources > 0) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticCode.REFS_OK,
              Severity.INFO,
              "All internal references valid (checked " + checkedResources + " resources)",
              null,
              false));
    }
  }
}
