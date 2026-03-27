package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.util.StringUtil;
import org.grimmory.epub4j.util.XmlCleaner;
import org.xml.sax.InputSource;

/**
 * Repairs common issues found in real-world EPUB files. Based on patterns observed across thousands
 * of EPUBs in the wild, including malformed metadata, broken references, missing TOC, and encoding
 * issues.
 *
 * <p>Covers the full range of issues encountered in production EPUB ingestion pipelines.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Run diagnostics
 * DiagnosticResult diagnostics = EpubDiagnostics.check(book);
 *
 * // Auto-fix what we can
 * BookRepair repair = new BookRepair();
 * RepairResult result = repair.repair(book);
 *
 * // Or fix specific errors
 * for (Diagnostic error : diagnostics.autoFixable()) {
 *     repair.fixError(book, error);
 * }
 * }</pre>
 */
public class BookRepair {

  private static final System.Logger log = System.getLogger(BookRepair.class.getName());
  private static volatile boolean nativeCleanerChecked = false;
  private static volatile boolean nativeCleanerAvailable = false;

  private static final Pattern TITLE_PATTERN =
      Pattern.compile(
          "<title[^>]*>\\s*([^<]+?)\\s*</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern UPPERCASE_LANG_DIR_ATTR =
      Pattern.compile("\\s(LANG|DIR)\\s*=", Pattern.CASE_INSENSITIVE);
  private static final Pattern EMPTY_INLINE_OR_LINK_TAG =
      Pattern.compile(
          "<(?:(?:a\\s+[^>]*href=\\\"[^\\\"]*\\\"[^>]*)|(?:a\\s+[^>]*href='[^']*'[^>]*)|(?:a\\s+[^>]*href=[^\\s>]+[^>]*))\\s*>\\s*</a>|<(?:(?:b|i|u))\\b[^>]*>\\s*</(?:(?:b|i|u))>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern OFFICE_P_TAG =
      Pattern.compile(
          "<\\s*o:p\\b[^>]*>(.*?)<\\s*/\\s*o:p\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern OFFICE_EMPTY_TAG =
      Pattern.compile("<\\s*o:p\\b[^>]*/\\s*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern XHTML_START_TAG =
      Pattern.compile("<([A-Za-z][A-Za-z0-9-]*)([^<>]*?)(/?)>", Pattern.DOTALL);
  private static final Pattern XHTML_END_TAG =
      Pattern.compile("</\\s*([A-Za-z][A-Za-z0-9-]*)\\s*>");
  private static final Pattern NON_NAMESPACED_ATTR_NAME =
      Pattern.compile("(\\s)([A-Za-z_][A-Za-z0-9_.-]*)(\\s*=)");
  private static final Pattern DRM_META_TAG =
      Pattern.compile(
          "(?is)\\s*<meta\\b[^>]*\\bname\\s*=\\s*(\"|')adept\\.[^\"']*resource\\1[^>]*>");
  private static final Pattern EMPTY_SPAN_TAG = Pattern.compile("(?is)<span>\\s*</span>");
  private static final Pattern STRAY_IMG_WITHOUT_SRC =
      Pattern.compile("(?is)<img\\b(?![^>]*\\bsrc\\s*=)[^>]*>");
  private static final Pattern STRAY_IMG_EMPTY_SRC =
      Pattern.compile("(?is)<img\\b[^>]*\\bsrc\\s*=\\s*([\"'])\\s*\\1[^>]*>");
  private static final Pattern BCP47_LIKE_LANGUAGE =
      Pattern.compile("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$");
  private static final Pattern BARE_AMPERSAND =
      Pattern.compile("&(?!#\\d+;|#x[0-9A-Fa-f]+;|[A-Za-z][A-Za-z0-9]+;)");
  private static final Set<String> KNOWN_ARTIFACT_FILENAMES =
      Set.of(
          "itunesmetadata.plist",
          "itunesartwork",
          "meta-inf/calibre_bookmarks.txt", // common authoring tool artifact
          ".ds_store",
          "thumbs.db",
          "rights.xml");

  public enum RepairCode {
    SPINE_NULL_REF,
    SPINE_ALIAS_RECONCILED,
    SPINE_MISSING_RESOURCE,
    SPINE_NON_XHTML_REMOVED,
    SPINE_DUPLICATE_REMOVED,
    SPINE_ITEM_ADDED,
    XHTML_PREPARSE_HARDENED,
    XHTML_STILL_MALFORMED,
    LINK_REWRITTEN,
    DUPLICATE_ID,
    MISSING_TOC,
    BROKEN_TOC_PRUNED,
    MISSING_COVER,
    NO_COVER,
    NORMALIZE_HREF,
    MISSING_MEDIA_TYPE,
    UNKNOWN_MEDIA_TYPE,
    MISSING_TITLE,
    MISSING_LANGUAGE,
    INVALID_LANGUAGE,
    MISSING_IDENTIFIER,
    BLANK_IDENTIFIER,
    XML_CLEANED,
    MARKUP_CLEANED,
    JS_ORPHAN_REMOVED,
    ARTIFACT_REMOVED
  }

  public record RepairAction(RepairCode code, String description, Severity severity) {
    public enum Severity {
      INFO,
      WARNING,
      FIX
    }
  }

  /**
   * Result of a repair operation.
   *
   * @param book the repaired book
   * @param actions list of actions taken
   */
  public record RepairResult(Book book, List<RepairAction> actions) {
    public boolean hasChanges() {
      return actions.stream().anyMatch(a -> a.severity() == RepairAction.Severity.FIX);
    }

    public int fixCount() {
      return (int) actions.stream().filter(a -> a.severity() == RepairAction.Severity.FIX).count();
    }
  }

  /**
   * Run all repair strategies on the given book. Returns a RepairResult with the (possibly
   * modified) book and a log of actions taken.
   *
   * @param book the book to repair
   * @return the repair result containing the book and list of actions
   */
  public RepairResult repair(Book book) {
    List<RepairAction> actions = new ArrayList<>(32);
    fixMissingMediaTypes(book, actions);
    normalizeHrefs(book, actions);
    fixDuplicateIds(book, actions);
    fixManifestReferences(book, actions);
    normalizeSpineReadingOrder(book, actions);
    // Strip Office/DRM/security artifacts BEFORE native HTML cleaning,
    // so the Gumbo parser doesn't mangle namespace-prefixed tags like <o:p>
    fixKindleAndGeneralMarkup(book, actions);
    hardenXhtmlPreParse(book, actions);
    LinkGraphRepair.repairInternalLinkGraph(book, actions);
    pruneBrokenTocEntries(book, actions);
    fixMissingToc(book, actions);
    fixMissingCover(book, actions);
    fixEmptyMetadata(book, actions);
    fixXmlCharacters(book, actions);
    removeUnreferencedJavascriptResources(book, actions);
    removeKnownArtifactResources(book, actions);
    return new RepairResult(book, List.copyOf(actions));
  }

  /**
   * Fix a specific diagnostic error.
   *
   * @param book the book to fix
   * @param diagnostic the diagnostic error to fix
   * @return true if the error was fixed
   */
  public boolean fixError(Book book, EpubDiagnostics.Diagnostic diagnostic) {
    if (!diagnostic.autoFixable()) {
      return false;
    }

    EpubDiagnostics.DiagnosticCode code = diagnostic.code();
    String href = diagnostic.resourceHref();

    return switch (code) {
      case META_NO_TITLE -> fixMissingTitle(book);
      case META_NO_LANGUAGE -> fixMissingLanguage(book);
      case META_NO_IDENTIFIER, META_BLANK_IDENTIFIER -> fixMissingIdentifier(book);
      case SPINE_NULL_REF, SPINE_MISSING_RESOURCE ->
          applyFixAndDetectChange(actions -> fixManifestReferences(book, actions));
      case SPINE_EMPTY ->
          applyFixAndDetectChange(actions -> normalizeSpineReadingOrder(book, actions));
      case DUPLICATE_ID -> applyFixAndDetectChange(actions -> fixDuplicateIds(book, actions));
      case TOC_EMPTY -> applyFixAndDetectChange(actions -> fixMissingToc(book, actions));
      case BROKEN_TOC_ENTRY ->
          applyFixAndDetectChange(actions -> pruneBrokenTocEntries(book, actions));
      case COVER_MISSING -> applyFixAndDetectChange(actions -> fixMissingCover(book, actions));
      case XML_PARSE_ERROR -> fixXmlCharacters(book, new ArrayList<>());
      case ENCODING_MISMATCH -> fixEncodingMismatch(book, href);
      default -> false;
    };
  }

  private static boolean applyFixAndDetectChange(
      java.util.function.Consumer<List<RepairAction>> fixer) {
    List<RepairAction> actions = new ArrayList<>();
    fixer.accept(actions);
    return !actions.isEmpty();
  }

  /**
   * Fix all auto-fixable errors from a diagnostic result.
   *
   * @param book the book to fix
   * @param diagnostics the diagnostic result
   * @return number of errors fixed
   */
  public int fixAllAutoFixable(Book book, EpubDiagnostics.DiagnosticResult diagnostics) {
    int fixed = 0;
    for (EpubDiagnostics.Diagnostic diagnostic : diagnostics.diagnostics()) {
      if (diagnostic.autoFixable() && fixError(book, diagnostic)) {
        fixed++;
      }
    }
    return fixed;
  }

  /** Remove spine references whose resources are null or not present in the book's resources. */
  private static void fixManifestReferences(Book book, List<RepairAction> actions) {
    Resources resources = book.getResources();
    List<SpineReference> spineRefs = book.getSpine().getSpineReferences();
    Iterator<SpineReference> it = spineRefs.iterator();
    while (it.hasNext()) {
      SpineReference ref = it.next();
      Resource resource = ref.getResource();
      if (resource == null) {
        it.remove();
        actions.add(
            new RepairAction(
                RepairCode.SPINE_NULL_REF,
                "Removed spine reference with null resource",
                RepairAction.Severity.FIX));
      } else if (!resources.containsByHref(resource.getHref())) {
        Resource reconciled = LinkGraphRepair.resolveResourceAlias(book, resource.getHref(), "");
        if (reconciled != null) {
          ref.setResource(reconciled);
          actions.add(
              new RepairAction(
                  RepairCode.SPINE_ALIAS_RECONCILED,
                  "Reconciled spine alias '"
                      + resource.getHref()
                      + "' to manifest href '"
                      + reconciled.getHref()
                      + "'",
                  RepairAction.Severity.FIX));
        } else {
          it.remove();
          actions.add(
              new RepairAction(
                  RepairCode.SPINE_MISSING_RESOURCE,
                  "Removed spine reference to missing resource: " + resource.getHref(),
                  RepairAction.Severity.FIX));
        }
      }
    }
  }

  /**
   * Harden XHTML before downstream XML parsing by applying conservative cleanup and validating
   * well-formedness.
   */
  private static void hardenXhtmlPreParse(Book book, List<RepairAction> actions) {
    Collection<Resource> allResources = book.getResources().getAll();
    for (Resource resource : allResources) {
      if (resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }

      try {
        XhtmlCleaner.CleanResult cleanerResult = XhtmlCleaner.clean(resource);

        byte[] data = resource.getData();
        if (data == null || data.length == 0) {
          continue;
        }

        String original = new String(data, StandardCharsets.UTF_8);

        // Only invoke the native HTML→XHTML cleaner when the content is not already
        // well-formed XML. Gumbo re-serializes even valid XHTML into a different
        // formatting (e.g. added whitespace), which would cause false repairs.
        String hardened;
        if (isWellFormedXml(original)) {
          hardened = original;
        } else {
          hardened = tryNativeHtmlCleaner(original);
          if (hardened == null) {
            hardened = original;
          }
        }
        hardened = XmlCleaner.cleanForXml(hardened);
        hardened = stripLeadingXmlNoise(hardened);
        hardened = BARE_AMPERSAND.matcher(hardened).replaceAll("&amp;");

        boolean changed = cleanerResult.modified() || !hardened.equals(original);

        if (changed) {
          resource.setData(hardened.getBytes(StandardCharsets.UTF_8));
          actions.add(
              new RepairAction(
                  RepairCode.XHTML_PREPARSE_HARDENED,
                  "Applied XHTML pre-parse hardening to: " + resource.getHref(),
                  RepairAction.Severity.FIX));
        }

        if (!isWellFormedXml(hardened)) {
          actions.add(
              new RepairAction(
                  RepairCode.XHTML_STILL_MALFORMED,
                  "XHTML remains not well-formed after hardening: " + resource.getHref(),
                  RepairAction.Severity.WARNING));
        }
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Failed to harden XHTML pre-parse content in: " + resource.getHref());
      }
    }
  }

  private static String stripLeadingXmlNoise(String content) {
    if (StringUtil.isBlank(content)) {
      return content;
    }
    String stripped = content;
    while (!stripped.isEmpty()) {
      char c = stripped.charAt(0);
      if (c == '\uFEFF' || Character.isWhitespace(c)) {
        stripped = stripped.substring(1);
        continue;
      }
      break;
    }
    int xmlDecl = stripped.indexOf("<?xml");
    if (xmlDecl > 0) {
      stripped = stripped.substring(xmlDecl);
    }
    return stripped;
  }

  private static boolean isWellFormedXml(String content) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setValidating(false);
      factory.setExpandEntityReferences(false);
      // All features set defensively  -  different parsers support different subsets
      trySetFactoryFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
      trySetFactoryFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
      trySetFactoryFeature(
          factory, "http://xml.org/sax/features/external-parameter-entities", false);
      trySetFactoryFeature(
          factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      trySetFactoryAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
      trySetFactoryAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static void trySetFactoryFeature(
      DocumentBuilderFactory factory, String feature, boolean value) {
    try {
      factory.setFeature(feature, value);
    } catch (Exception ignored) {
    }
  }

  private static void trySetFactoryAttribute(
      DocumentBuilderFactory factory, String name, String value) {
    try {
      factory.setAttribute(name, value);
    } catch (Exception ignored) {
    }
  }

  /**
   * Optionally invoke epub4j-native Gumbo-based cleaner via reflection when available. Keeps
   * epub4j-core decoupled from epub4j-native at compile time.
   */
  private static String tryNativeHtmlCleaner(String content) {
    if (!nativeCleanerChecked) {
      nativeCleanerAvailable = isNativeCleanerPresent();
      nativeCleanerChecked = true;
    }
    if (!nativeCleanerAvailable) {
      return content;
    }

    try {
      Class<?> cleanerClass = Class.forName("org.grimmory.epub4j.native_parsing.NativeHtmlCleaner");
      Object cleaner = cleanerClass.getDeclaredConstructor().newInstance();
      try {
        return (String)
            cleanerClass
                .getMethod("clean", String.class, String.class)
                .invoke(cleaner, content, "UTF-8");
      } finally {
        try {
          cleanerClass.getMethod("close").invoke(cleaner);
        } catch (Exception ignored) {
        }
      }
    } catch (Exception e) {
      // If native layer fails at runtime, degrade gracefully to Java cleaner only.
      return content;
    }
  }

  private static boolean isNativeCleanerPresent() {
    try {
      Class.forName("org.grimmory.epub4j.native_parsing.NativeHtmlCleaner");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Normalize spine to reflect a clean reading order: - keep only XHTML resources that are present
   * in manifest/resources - remove duplicate spine entries (keep first) - append missing XHTML
   * manifest items in deterministic href order
   */
  private static void normalizeSpineReadingOrder(Book book, List<RepairAction> actions) {
    Spine spine = book.getSpine();
    if (spine == null) {
      return;
    }

    List<SpineReference> original = spine.getSpineReferences();
    if (original == null) {
      original = new ArrayList<>();
    }
    int originalSize = original.size();

    List<SpineReference> normalized = new ArrayList<>(originalSize);
    Set<String> seenSpineHrefs = new HashSet<>(Math.max(16, originalSize * 2));
    boolean changed = false;

    for (SpineReference ref : original) {
      if (ref == null || ref.getResource() == null) {
        changed = true;
        continue;
      }

      Resource resource = ref.getResource();
      String href = resource.getHref();
      if (StringUtil.isBlank(href) || !book.getResources().containsByHref(href)) {
        changed = true;
        continue;
      }

      String canonicalHref = StringUtil.substringBefore(href, '#');

      if (resource.getMediaType() != MediaTypes.XHTML) {
        changed = true;
        actions.add(
            new RepairAction(
                RepairCode.SPINE_NON_XHTML_REMOVED,
                "Removed non-XHTML resource from spine: " + canonicalHref,
                RepairAction.Severity.FIX));
        continue;
      }

      if (!seenSpineHrefs.add(canonicalHref)) {
        changed = true;
        actions.add(
            new RepairAction(
                RepairCode.SPINE_DUPLICATE_REMOVED,
                "Removed duplicate spine entry: " + canonicalHref,
                RepairAction.Severity.FIX));
        continue;
      }

      normalized.add(ref);
    }

    Collection<Resource> allResources = book.getResources().getAll();
    List<Resource> allXhtml = new ArrayList<>(allResources.size());
    for (Resource resource : allResources) {
      if (resource != null
          && resource.getMediaType() == MediaTypes.XHTML
          && StringUtil.isNotBlank(resource.getHref())) {
        allXhtml.add(resource);
      }
    }
    allXhtml.sort(Comparator.comparing(Resource::getHref, String.CASE_INSENSITIVE_ORDER));

    for (Resource xhtml : allXhtml) {
      String href = StringUtil.substringBefore(xhtml.getHref(), '#');
      if (seenSpineHrefs.add(href)) {
        normalized.add(new SpineReference(xhtml));
        changed = true;
        actions.add(
            new RepairAction(
                RepairCode.SPINE_ITEM_ADDED,
                "Added missing XHTML resource to spine: " + href,
                RepairAction.Severity.FIX));
      }
    }

    if (changed) {
      spine.setSpineReferences(normalized);
    }
  }

  /** Scan all resources for duplicate IDs and rename duplicates with _2, _3 suffixes. */
  private static void fixDuplicateIds(Book book, List<RepairAction> actions) {
    Collection<Resource> allResources = book.getResources().getAll();
    Map<String, Integer> idCounts = new HashMap<>(Math.max(16, allResources.size() * 2));
    Resources resources = book.getResources();
    for (Resource resource : allResources) {
      String id = resource.getId();
      if (StringUtil.isBlank(id)) continue;

      int count = idCounts.getOrDefault(id, 0) + 1;
      idCounts.put(id, count);

      if (count > 1) {
        String newId = id + "_" + count;
        // Ensure this new ID is also unique
        while (resources.containsId(newId)) {
          count++;
          newId = id + "_" + count;
        }
        resource.setId(newId);
        actions.add(
            new RepairAction(
                RepairCode.DUPLICATE_ID,
                "Renamed duplicate ID '"
                    + id
                    + "' to '"
                    + newId
                    + "' for resource: "
                    + resource.getHref(),
                RepairAction.Severity.FIX));
      }
    }
  }

  /** If the table of contents is empty and the spine has content, synthesize TOC from spine. */
  private static void fixMissingToc(Book book, List<RepairAction> actions) {
    TableOfContents toc = book.getTableOfContents();
    if (toc.size() == 0 && !book.getSpine().isEmpty()) {
      TableOfContents synthesized = TocSynthesizer.synthesize(book);
      book.setTableOfContents(synthesized);
      actions.add(
          new RepairAction(
              RepairCode.MISSING_TOC,
              "Synthesized table of contents with " + synthesized.size() + " entries from spine",
              RepairAction.Severity.FIX));
    }
  }

  /**
   * Remove TOC entries that reference non-existent or non-XHTML resources. If a broken entry has
   * children, the children are promoted to preserve hierarchy.
   */
  private void pruneBrokenTocEntries(Book book, List<RepairAction> actions) {
    TableOfContents toc = book.getTableOfContents();
    if (toc == null || toc.getTocReferences() == null || toc.getTocReferences().isEmpty()) {
      return;
    }

    Collection<Resource> allResources = book.getResources().getAll();
    Set<String> validXhtmlHrefs = new HashSet<>(Math.max(16, allResources.size()));
    for (Resource resource : allResources) {
      if (resource != null
          && resource.getMediaType() == MediaTypes.XHTML
          && StringUtil.isNotBlank(resource.getHref())) {
        validXhtmlHrefs.add(StringUtil.substringBefore(resource.getHref(), '#'));
      }
    }

    List<TOCReference> pruned =
        pruneTocReferences(toc.getTocReferences(), validXhtmlHrefs, actions);
    toc.setTocReferences(pruned);
  }

  private List<TOCReference> pruneTocReferences(
      List<TOCReference> input, Set<String> validXhtmlHrefs, List<RepairAction> actions) {
    List<TOCReference> result = new ArrayList<>(input.size());
    for (TOCReference tocReference : input) {
      List<TOCReference> children =
          tocReference.getChildren() == null
              ? Collections.emptyList()
              : pruneTocReferences(tocReference.getChildren(), validXhtmlHrefs, actions);
      tocReference.setChildren(children);

      Resource resource = tocReference.getResource();
      boolean keepNode;

      if (resource == null) {
        keepNode = !children.isEmpty();
      } else {
        String href = StringUtil.substringBefore(resource.getHref(), '#');
        keepNode = StringUtil.isNotBlank(href) && validXhtmlHrefs.contains(href);
        if (!keepNode) {
          actions.add(
              new RepairAction(
                  RepairCode.BROKEN_TOC_PRUNED,
                  "Removed broken TOC entry referencing missing/non-XHTML resource: "
                      + resource.getHref(),
                  RepairAction.Severity.FIX));
        }
      }

      if (keepNode) {
        result.add(tocReference);
      } else if (!children.isEmpty()) {
        result.addAll(children);
      }
    }
    return result;
  }

  /** If no cover image is set, try to detect one using CoverDetector. */
  private static void fixMissingCover(Book book, List<RepairAction> actions) {
    if (book.getCoverImage() == null) {
      Resource detected = CoverDetector.detectCoverImage(book);
      if (detected != null) {
        book.setCoverImage(detected);
        actions.add(
            new RepairAction(
                RepairCode.MISSING_COVER,
                "Detected cover image: " + detected.getHref(),
                RepairAction.Severity.FIX));
      } else {
        actions.add(
            new RepairAction(
                RepairCode.NO_COVER,
                "No cover image could be detected",
                RepairAction.Severity.WARNING));
      }
    }
  }

  /** Normalize resource hrefs: URL-decode, collapse path dots, normalize separators. */
  private static void normalizeHrefs(Book book, List<RepairAction> actions) {
    // Collect resources to re-add since href is the map key
    List<Resource> allResources = new ArrayList<>(book.getResources().getAll());
    List<Resource> toReAdd = new ArrayList<>(allResources.size());
    List<String> toRemove = new ArrayList<>(allResources.size());

    for (Resource resource : allResources) {
      String href = resource.getHref();
      if (href == null) continue;

      String normalized = href;

      try {
        String decoded = URLDecoder.decode(href, StandardCharsets.UTF_8);
        if (!decoded.equals(normalized)) {
          normalized = decoded;
        }
      } catch (IllegalArgumentException e) {
        log.log(System.Logger.Level.DEBUG, "Skipping malformed URL decode for href: " + href);
      }

      normalized = normalized.replace('\\', '/');

      if (normalized.contains("..") || normalized.contains("./")) {
        normalized = StringUtil.collapsePathDots(normalized);
      }

      // EPUB hrefs must be relative
      while (normalized.startsWith("/")) {
        normalized = normalized.substring(1);
      }

      if (!normalized.equals(href)) {
        toRemove.add(href);
        resource.setHref(normalized);
        toReAdd.add(resource);
        actions.add(
            new RepairAction(
                RepairCode.NORMALIZE_HREF,
                "Normalized href '" + href + "' to '" + normalized + "'",
                RepairAction.Severity.FIX));
      }
    }

    // Must remove old keys before re-adding so the resource map stays consistent
    for (String href : toRemove) {
      book.getResources().remove(href);
    }
    for (Resource resource : toReAdd) {
      book.getResources().add(resource);
    }
  }

  /** For resources with null MediaType, try to determine from href extension. */
  private static void fixMissingMediaTypes(Book book, List<RepairAction> actions) {
    Collection<Resource> allResources = book.getResources().getAll();
    for (Resource resource : allResources) {
      if (resource.getMediaType() == null && StringUtil.isNotBlank(resource.getHref())) {
        MediaType determined = MediaTypes.determineMediaType(resource.getHref());
        if (determined != null) {
          resource.setMediaType(determined);
          actions.add(
              new RepairAction(
                  RepairCode.MISSING_MEDIA_TYPE,
                  "Set media type '" + determined.name() + "' for resource: " + resource.getHref(),
                  RepairAction.Severity.FIX));
        } else {
          actions.add(
              new RepairAction(
                  RepairCode.UNKNOWN_MEDIA_TYPE,
                  "Could not determine media type for resource: " + resource.getHref(),
                  RepairAction.Severity.WARNING));
        }
      }
    }
  }

  /**
   * Fix missing essential metadata: title, language, and identifier. Tries to extract title from
   * first XHTML resource's title tag. Defaults language to "en" and generates a UUID identifier if
   * missing.
   */
  private void fixEmptyMetadata(Book book, List<RepairAction> actions) {
    Metadata metadata = book.getMetadata();

    if (StringUtil.isBlank(metadata.getFirstTitle())) {
      String extractedTitle = extractTitleFromContent(book);
      if (extractedTitle != null) {
        metadata.addTitle(extractedTitle);
        actions.add(
            new RepairAction(
                RepairCode.MISSING_TITLE,
                "Extracted title from content: '" + extractedTitle + "'",
                RepairAction.Severity.FIX));
      } else {
        metadata.addTitle("Untitled");
        actions.add(
            new RepairAction(
                RepairCode.MISSING_TITLE,
                "No title found, defaulted to 'Untitled'",
                RepairAction.Severity.FIX));
      }
    }

    if (StringUtil.isBlank(metadata.getLanguage())) {
      metadata.setLanguage("en");
      actions.add(
          new RepairAction(
              RepairCode.MISSING_LANGUAGE,
              "No language set, defaulted to 'en'",
              RepairAction.Severity.FIX));
    } else {
      String language = metadata.getLanguage().trim();
      if (!BCP47_LIKE_LANGUAGE.matcher(language).matches()) {
        metadata.setLanguage("en");
        actions.add(
            new RepairAction(
                RepairCode.INVALID_LANGUAGE,
                "Invalid language tag '" + language + "', normalized to 'en'",
                RepairAction.Severity.FIX));
      }
    }

    if (metadata.getIdentifiers().isEmpty()) {
      Identifier generated = new Identifier();
      metadata.addIdentifier(generated);
      actions.add(
          new RepairAction(
              RepairCode.MISSING_IDENTIFIER,
              "Generated UUID identifier: " + generated.getValue(),
              RepairAction.Severity.FIX));
    } else {
      boolean allBlank = true;
      List<Identifier> identifiers = metadata.getIdentifiers();
      int identifierCount = identifiers.size();
      for (int i = 0; i < identifierCount; i++) {
        if (StringUtil.isNotBlank(identifiers.get(i).getValue())) {
          allBlank = false;
          break;
        }
      }
      if (allBlank) {
        Identifier generated = new Identifier();
        metadata.addIdentifier(generated);
        actions.add(
            new RepairAction(
                RepairCode.BLANK_IDENTIFIER,
                "All identifiers had blank values, generated UUID: " + generated.getValue(),
                RepairAction.Severity.FIX));
      }
    }
  }

  /** Try to extract a title from the first XHTML resource in the spine. */
  private String extractTitleFromContent(Book book) {
    Spine spine = book.getSpine();
    for (int i = 0; i < spine.size(); i++) {
      Resource resource = spine.getResource(i);
      if (resource == null || resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }
      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;

        String content = new String(data, StandardCharsets.UTF_8);
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
          String title =
              matcher
                  .group(1)
                  .trim()
                  .replace("&amp;", "&")
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&#39;", "'")
                  .replace("&apos;", "'");
          if (!title.isBlank()) {
            return title;
          }
        }
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Failed to read resource for title extraction: " + resource.getHref());
      }
    }
    return null;
  }

  // Individual Fix Methods (for fixError)

  /**
   * Fix missing title by extracting from content.
   *
   * @return true if fixed
   */
  private boolean fixMissingTitle(Book book) {
    Metadata metadata = book.getMetadata();
    if (StringUtil.isNotBlank(metadata.getFirstTitle())) {
      return false; // Already has title
    }

    String title = extractTitleFromContent(book);
    if (title != null) {
      metadata.addTitle(title);
      log.log(System.Logger.Level.INFO, "Fixed missing title: " + title);
      return true;
    }

    // Last resort: use "Untitled"
    metadata.addTitle("Untitled");
    log.log(System.Logger.Level.INFO, "Fixed missing title with default: Untitled");
    return true;
  }

  /**
   * Fix missing language.
   *
   * @return true if fixed
   */
  private static boolean fixMissingLanguage(Book book) {
    Metadata metadata = book.getMetadata();
    if (StringUtil.isNotBlank(metadata.getLanguage())) {
      return false; // Already has language
    }

    metadata.setLanguage("en");
    log.log(System.Logger.Level.INFO, "Fixed missing language: en");
    return true;
  }

  /**
   * Fix missing identifier.
   *
   * @return true if fixed
   */
  private static boolean fixMissingIdentifier(Book book) {
    Metadata metadata = book.getMetadata();
    if (!metadata.getIdentifiers().isEmpty()) {
      boolean hasValid = false;
      List<Identifier> identifiers = metadata.getIdentifiers();
      int identifierCount = identifiers.size();
      for (int i = 0; i < identifierCount; i++) {
        if (StringUtil.isNotBlank(identifiers.get(i).getValue())) {
          hasValid = true;
          break;
        }
      }
      if (hasValid) {
        return false; // Already has valid identifier
      }
    }

    Identifier generated = new Identifier();
    metadata.addIdentifier(generated);
    log.log(System.Logger.Level.INFO, "Fixed missing identifier: " + generated.getValue());
    return true;
  }

  /**
   * Fix XML character issues by cleaning invalid characters.
   *
   * @return true if fixed
   */
  private static boolean fixXmlCharacters(Book book, List<RepairAction> actions) {
    boolean changed = false;

    Collection<Resource> allResources = book.getResources().getAll();
    for (Resource resource : allResources) {
      if (resource.getMediaType() != MediaTypes.XHTML
          && resource.getMediaType() != MediaTypes.CSS
          && resource.getMediaType() != MediaTypes.NCX) {
        continue;
      }

      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;

        String content = new String(data, StandardCharsets.UTF_8);
        String cleaned = XmlCleaner.cleanXmlChars(content);

        if (!cleaned.equals(content)) {
          resource.setData(cleaned.getBytes(StandardCharsets.UTF_8));
          changed = true;
          if (actions != null) {
            actions.add(
                new RepairAction(
                    RepairCode.XML_CLEANED,
                    "Cleaned invalid XML characters from: " + resource.getHref(),
                    RepairAction.Severity.FIX));
          }
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Failed to clean XML in: " + resource.getHref());
      }
    }

    return changed;
  }

  /**
   * Fix encoding mismatch by re-encoding to UTF-8.
   *
   * @return true if fixed
   */
  private static boolean fixEncodingMismatch(Book book, String href) {
    if (href == null) {
      return false;
    }

    Resource resource = book.getResources().getByHref(href);
    if (resource == null) {
      return false;
    }

    try {
      // Use EncodingNormalizer to fix
      return EncodingNormalizer.normalizeToUtf8(resource);
    } catch (IOException e) {
      log.log(System.Logger.Level.DEBUG, "Failed to fix encoding for: " + href);
      return false;
    }
  }

  /**
   * Apply practical XHTML markup repairs: - normalize LANG/DIR/XML:LANG attribute casing - remove
   * empty link/inline tags that can create rendering artifacts - strip common Microsoft Office o:p
   * markup - stricter XHTML-only legacy normalization and script/DRM cleanup
   */
  private void fixKindleAndGeneralMarkup(Book book, List<RepairAction> actions) {
    Collection<Resource> allResources = book.getResources().getAll();
    for (Resource resource : allResources) {
      if (resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }

      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) {
          continue;
        }

        String content = new String(data, StandardCharsets.UTF_8);
        String original = content;

        // KindleGen often carries legacy uppercase HTML attributes in source XHTML.
        content =
            UPPERCASE_LANG_DIR_ATTR
                .matcher(content)
                .replaceAll(match -> " " + match.group(1).toLowerCase(Locale.ROOT) + "=");

        // Strict pass for XHTML resources: lowercase non-namespaced HTML tags/attrs.
        // Namespaced tags/attributes (e.g., svg:*, xlink:href) are intentionally untouched.
        content = normalizeLegacyXhtmlCase(content);

        // Remove Microsoft Office paragraph markers while preserving inner text.
        content = OFFICE_P_TAG.matcher(content).replaceAll("$1");
        content = OFFICE_EMPTY_TAG.matcher(content).replaceAll("");

        // Remove metadata artifacts and strip dangerous elements.
        content = DRM_META_TAG.matcher(content).replaceAll("");
        content = XhtmlSecurityStrip.strip(content);

        // Remove empty anchors/inline tags with no semantic content.
        content = EMPTY_INLINE_OR_LINK_TAG.matcher(content).replaceAll("");
        content = EMPTY_SPAN_TAG.matcher(content).replaceAll("");
        content = STRAY_IMG_WITHOUT_SRC.matcher(content).replaceAll("");
        content = STRAY_IMG_EMPTY_SRC.matcher(content).replaceAll("");

        if (!content.equals(original)) {
          resource.setData(content.getBytes(StandardCharsets.UTF_8));
          actions.add(
              new RepairAction(
                  RepairCode.MARKUP_CLEANED,
                  "Normalized and cleaned markup artifacts in: " + resource.getHref(),
                  RepairAction.Severity.FIX));
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Failed to clean markup in: " + resource.getHref());
      }
    }
  }

  /** Remove JavaScript resources that are no longer referenced from XHTML/CSS content. */
  private void removeUnreferencedJavascriptResources(Book book, List<RepairAction> actions) {
    Collection<Resource> allResources = book.getResources().getAll();
    Set<String> referenced = new HashSet<>(Math.max(16, allResources.size() * 2));

    for (Resource resource : allResources) {
      if (resource == null || StringUtil.isBlank(resource.getHref())) {
        continue;
      }
      MediaType mediaType = resource.getMediaType();
      if (mediaType != MediaTypes.XHTML && mediaType != MediaTypes.CSS) {
        continue;
      }

      try {
        String content = new String(resource.getData(), StandardCharsets.UTF_8);
        String basePath = getBasePath(resource.getHref());

        collectReferencedJavascriptTargets(
            content, basePath, LinkGraphRepair.HREF_SRC_TARGET_PATTERN, referenced);
        collectReferencedJavascriptTargets(
            content, basePath, LinkGraphRepair.CSS_URL_PATTERN, referenced);
        collectReferencedJavascriptTargets(
            content, basePath, LinkGraphRepair.CSS_IMPORT_PATTERN, referenced);
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Failed to scan JavaScript references in: " + resource.getHref());
      }
    }

    List<String> toRemove = new ArrayList<>(allResources.size());
    for (Resource resource : allResources) {
      if (resource == null
          || resource.getMediaType() != MediaTypes.JAVASCRIPT
          || StringUtil.isBlank(resource.getHref())) {
        continue;
      }

      String href = StringUtil.substringBefore(resource.getHref(), '#');
      if (!referenced.contains(href)) {
        toRemove.add(href);
      }
    }

    for (String href : toRemove) {
      book.getResources().remove(href);
      actions.add(
          new RepairAction(
              RepairCode.JS_ORPHAN_REMOVED,
              "Removed unreferenced JavaScript resource: " + href,
              RepairAction.Severity.FIX));
    }
  }

  private void collectReferencedJavascriptTargets(
      String content, String basePath, Pattern pattern, Set<String> referenced) {
    Matcher matcher = pattern.matcher(content);
    while (matcher.find()) {
      String rawTarget = matcher.group(1);
      if (StringUtil.isBlank(rawTarget)) {
        continue;
      }

      String target =
          StringUtil.substringBefore(StringUtil.substringBefore(rawTarget.trim(), '#'), '?');
      if (!target.toLowerCase(Locale.ROOT).endsWith(".js")) {
        continue;
      }
      if (target.contains("://")
          || target.startsWith("mailto:")
          || target.startsWith("javascript:")) {
        continue;
      }

      referenced.add(normalizeRelativeHref(basePath, target));
    }
  }

  private static String getBasePath(String href) {
    int slash = href.lastIndexOf('/');
    if (slash < 0) {
      return "";
    }
    return href.substring(0, slash + 1);
  }

  private static String normalizeRelativeHref(String basePath, String target) {
    String candidate = target;
    if (!target.startsWith("/")) {
      candidate = basePath + target;
    }
    candidate = candidate.replace('\\', '/');
    candidate = StringUtil.collapsePathDots(candidate);
    while (candidate.startsWith("/")) {
      candidate = candidate.substring(1);
    }
    return candidate;
  }

  /** Remove known non-content artifacts frequently carried by toolchains/devices. */
  private static void removeKnownArtifactResources(Book book, List<RepairAction> actions) {
    Collection<Resource> allResources = book.getResources().getAll();
    List<String> toRemove = new ArrayList<>(allResources.size());
    for (Resource resource : allResources) {
      if (resource == null || StringUtil.isBlank(resource.getHref())) {
        continue;
      }
      String href = resource.getHref();
      String lowerHref = href.toLowerCase(Locale.ROOT);
      if (KNOWN_ARTIFACT_FILENAMES.contains(lowerHref)) {
        toRemove.add(href);
        continue;
      }
      for (String artifact : KNOWN_ARTIFACT_FILENAMES) {
        if (lowerHref.endsWith("/" + artifact)) {
          toRemove.add(href);
          break;
        }
      }
    }

    for (String href : toRemove) {
      book.getResources().remove(href);
      actions.add(
          new RepairAction(
              RepairCode.ARTIFACT_REMOVED,
              "Removed known non-content artifact: " + href,
              RepairAction.Severity.FIX));
    }
  }

  private String normalizeLegacyXhtmlCase(String content) {
    Matcher startTagMatcher = XHTML_START_TAG.matcher(content);
    StringBuilder normalizedStartTags = new StringBuilder(content.length());
    while (startTagMatcher.find()) {
      String tagName = startTagMatcher.group(1);
      String attrs = startTagMatcher.group(2);
      String closing = startTagMatcher.group(3);

      String normalizedAttrs = lowercaseNonNamespacedAttributeNames(attrs);
      String replacement = "<" + tagName.toLowerCase(Locale.ROOT) + normalizedAttrs + closing + ">";
      startTagMatcher.appendReplacement(normalizedStartTags, Matcher.quoteReplacement(replacement));
    }
    startTagMatcher.appendTail(normalizedStartTags);

    Matcher endTagMatcher = XHTML_END_TAG.matcher(normalizedStartTags.toString());
    StringBuilder normalizedEndTags = new StringBuilder(normalizedStartTags.length());
    while (endTagMatcher.find()) {
      String tagName = endTagMatcher.group(1);
      String replacement = "</" + tagName.toLowerCase(Locale.ROOT) + ">";
      endTagMatcher.appendReplacement(normalizedEndTags, Matcher.quoteReplacement(replacement));
    }
    endTagMatcher.appendTail(normalizedEndTags);
    return normalizedEndTags.toString();
  }

  private String lowercaseNonNamespacedAttributeNames(String attrs) {
    Matcher attrMatcher = NON_NAMESPACED_ATTR_NAME.matcher(attrs);
    StringBuilder normalized = new StringBuilder(attrs.length());
    while (attrMatcher.find()) {
      String attrName = attrMatcher.group(2);
      String replacement =
          attrMatcher.group(1) + attrName.toLowerCase(Locale.ROOT) + attrMatcher.group(3);
      attrMatcher.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
    }
    attrMatcher.appendTail(normalized);
    return normalized.toString();
  }
}
