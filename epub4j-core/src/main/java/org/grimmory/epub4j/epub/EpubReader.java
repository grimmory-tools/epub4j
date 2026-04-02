package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.StructuredTaskScope;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.grimmory.epub4j.domain.SpineReference;
import org.grimmory.epub4j.domain.TOCReference;
import org.grimmory.epub4j.util.ResourceUtil;
import org.grimmory.epub4j.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Reads an epub file.
 *
 * @author paul
 */
public class EpubReader {

  private static final System.Logger log = System.getLogger(EpubReader.class.getName());

  public enum IngestionCode {
    CONTAINER_MISSING,
    CONTAINER_PARSE_ERROR,
    OPF_MISSING_PACKAGE_RESOURCE,
    OPF_PARSE_ERROR,
    TOC_MISSING,
    TOC_INVALID,
    SPINE_BROKEN_REFERENCE,
    TOC_BROKEN_REFERENCE,
    MIMETYPE_MISSING,
    MIMETYPE_INVALID,
    /** An individual resource failed to load but other resources were ingested successfully. */
    RESOURCE_LOAD_ERROR,
    /** An individual resource exceeded policy size limits and was skipped. */
    RESOURCE_SIZE_EXCEEDED,
    /** An archive entry had an unsafe path and was skipped. */
    RESOURCE_UNSAFE_PATH,
    /** A duplicate archive entry was skipped. */
    RESOURCE_DUPLICATE,
    /** Archive entry count exceeded policy limit; remaining entries were not loaded. */
    ARCHIVE_ENTRY_LIMIT,
    /** Total uncompressed size exceeded policy limit; remaining entries were not loaded. */
    ARCHIVE_SIZE_LIMIT,
    /** Archive central directory was corrupted; entries were recovered from local headers. */
    ARCHIVE_CORRUPTED
  }

  public record IngestionWarning(IngestionCode code, String message, String resourceHref) {}

  public record IngestionReport(List<IngestionWarning> warnings, List<String> corrections) {
    public IngestionReport {
      warnings = List.copyOf(warnings == null ? List.of() : warnings);
      corrections = List.copyOf(corrections == null ? List.of() : corrections);
    }

    public boolean hasWarnings() {
      return !warnings.isEmpty();
    }

    public boolean hasCorrections() {
      return !corrections.isEmpty();
    }
  }

  public record ReadResult(Book book, IngestionReport report) {}

  private final BookProcessor bookProcessor;
  private final EpubProcessingPolicy policy;

  public EpubReader() {
    this(BookProcessor.IDENTITY_BOOKPROCESSOR, EpubProcessingPolicy.defaultPolicy());
  }

  public EpubReader(BookProcessor bookProcessor) {
    this(bookProcessor, EpubProcessingPolicy.defaultPolicy());
  }

  public EpubReader(BookProcessor bookProcessor, EpubProcessingPolicy policy) {
    this.bookProcessor =
        bookProcessor == null ? BookProcessor.IDENTITY_BOOKPROCESSOR : bookProcessor;
    this.policy = policy == null ? EpubProcessingPolicy.defaultPolicy() : policy;
  }

  public Book readEpub(InputStream in) throws IOException {
    return readEpub(in, Constants.CHARACTER_ENCODING);
  }

  /**
   * Reads an EPUB from a file path. This is the preferred method as it avoids creating a temporary
   * file - NightCompress/libarchive reads directly from the path.
   *
   * @param epubPath path to the EPUB file
   * @return the parsed Book
   * @throws IOException if reading fails
   */
  public Book readEpub(Path epubPath) throws IOException {
    return readEpub(epubPath, Constants.CHARACTER_ENCODING);
  }

  /**
   * Reads an EPUB from a file path and fails fast if strict validation reports errors.
   *
   * @param epubPath path to the EPUB file
   * @return the parsed Book
   * @throws IOException if validation fails or reading fails
   */
  public Book readEpubStrict(Path epubPath) throws IOException {
    return readEpubStrict(epubPath, Constants.CHARACTER_ENCODING);
  }

  /**
   * Reads an EPUB from a file path with the given encoding.
   *
   * @param epubPath path to the EPUB file
   * @param encoding the encoding to use for HTML files within the EPUB
   * @return the parsed Book
   * @throws IOException if reading fails
   */
  public Book readEpub(Path epubPath, String encoding) throws IOException {
    return readEpubWithReport(epubPath, encoding).book();
  }

  public ReadResult readEpubWithReport(Path epubPath) throws IOException {
    return readEpubWithReport(epubPath, Constants.CHARACTER_ENCODING);
  }

  public ReadResult readEpubWithReport(Path epubPath, String encoding) throws IOException {
    ResourcesLoader.ResourceLoadResult loadResult =
        ResourcesLoader.loadResourcesWithWarnings(
            epubPath, encoding, Collections.emptyList(), policy);
    return readEpubWithReport(loadResult, new Book(), true, policy);
  }

  /**
   * Reads an EPUB from a file path with strict pre-validation.
   *
   * @param epubPath path to the EPUB file
   * @param encoding the encoding to use for HTML files within the EPUB
   * @return the parsed Book
   * @throws IOException if validation fails or reading fails
   */
  public Book readEpubStrict(Path epubPath, String encoding) throws IOException {
    return readEpubStrictWithReport(epubPath, encoding).book();
  }

  public ReadResult readEpubStrictWithReport(Path epubPath, String encoding) throws IOException {
    validateOrThrow(epubPath);
    EpubProcessingPolicy strictPolicy = strictPolicy();
    ResourcesLoader.ResourceLoadResult loadResult =
        ResourcesLoader.loadResourcesWithWarnings(
            epubPath, encoding, Collections.emptyList(), strictPolicy);
    return readEpubWithReport(loadResult, new Book(), true, strictPolicy);
  }

  /**
   * Read epub from inputstream.
   *
   * @param in the inputstream from which to read the epub
   * @param encoding the encoding to use for the html files within the epub
   * @return the Book as read from the inputstream
   * @throws IOException if reading fails
   */
  public Book readEpub(InputStream in, String encoding) throws IOException {
    return readEpubWithReport(in, encoding).book();
  }

  public ReadResult readEpubWithReport(InputStream in, String encoding) throws IOException {
    ResourcesLoader.ResourceLoadResult loadResult =
        ResourcesLoader.loadResourcesWithWarnings(in, encoding, policy);
    return readEpubWithReport(loadResult, new Book(), true, policy);
  }

  /**
   * Reads an EPUB from stream with strict pre-validation.
   *
   * @param in the inputstream from which to read the epub
   * @param encoding the encoding to use for the html files within the epub
   * @return the Book as read from the inputstream
   * @throws IOException if validation fails or reading fails
   */
  public Book readEpubStrict(InputStream in, String encoding) throws IOException {
    return readEpubStrictWithReport(in, encoding).book();
  }

  public ReadResult readEpubStrictWithReport(InputStream in, String encoding) throws IOException {
    Path tempFile = Files.createTempFile("epub4j-reader-strict-", ".epub");
    try {
      EpubProcessingPolicy strictPolicy = strictPolicy();
      copyToTempWithLimit(in, tempFile, strictPolicy.maxArchiveBytes());
      validateOrThrow(tempFile);
      ResourcesLoader.ResourceLoadResult loadResult =
          ResourcesLoader.loadResourcesWithWarnings(
              tempFile, encoding, Collections.emptyList(), strictPolicy);
      return readEpubWithReport(loadResult, new Book(), true, strictPolicy);
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private EpubProcessingPolicy strictPolicy() {
    return EpubProcessingPolicy.builder(policy).mode(EpubProcessingPolicy.Mode.STRICT).build();
  }

  private static void copyToTempWithLimit(InputStream in, Path tempFile, long maxBytes)
      throws IOException {
    byte[] buffer = new byte[8192];
    long total = 0;
    try (OutputStream out = Files.newOutputStream(tempFile)) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new IOException(
              "EPUB stream exceeds policy size limit during copy: "
                  + total
                  + " > "
                  + maxBytes
                  + " bytes");
        }
        out.write(buffer, 0, read);
      }
    }
  }

  /**
   * Reads this EPUB without loading all resources into memory. Lazy-loaded resources are backed by
   * an EpubResourceProvider that reads from the ZIP file on demand.
   *
   * @param epubPath the path to the EPUB file
   * @param encoding the encoding for XHTML files
   * @return the Book with lazily-loaded resources
   * @throws IOException if reading fails
   */
  public Book readEpubLazy(Path epubPath, String encoding) throws IOException {
    return readEpubLazy(epubPath, encoding, Arrays.asList(MediaTypes.mediaTypes));
  }

  /**
   * Reads this EPUB without loading specified resource types into memory.
   *
   * @param epubPath the path to the EPUB file
   * @param encoding the encoding for XHTML files
   * @param lazyLoadedTypes a list of the MediaType to load lazily
   * @return the Book with lazily-loaded resources
   * @throws IOException if reading fails
   */
  public Book readEpubLazy(Path epubPath, String encoding, List<MediaType> lazyLoadedTypes)
      throws IOException {
    Resources resources =
        ResourcesLoader.loadResources(epubPath, encoding, lazyLoadedTypes, policy);
    return readEpub(resources);
  }

  public Book readEpub(Resources resources) throws IOException {
    return readEpubWithReport(resources).book();
  }

  public ReadResult readEpubWithReport(Resources resources) throws IOException {
    return readEpubWithReport(resources, new Book(), true, policy);
  }

  public Book readEpub(Resources resources, Book result) throws IOException {
    return readEpubWithReport(resources, result, true).book();
  }

  /**
   * Reads an EPUB from resources with optional encoding normalization.
   *
   * @param resources the EPUB resources
   * @param result the book to populate (or null to create new)
   * @param normalizeEncoding whether to normalize all text resources to UTF-8
   * @return the parsed Book
   * @throws IOException if reading fails
   */
  public Book readEpub(Resources resources, Book result, boolean normalizeEncoding)
      throws IOException {
    return readEpubWithReport(resources, result, normalizeEncoding).book();
  }

  public ReadResult readEpubWithReport(Resources resources, Book result, boolean normalizeEncoding)
      throws IOException {
    return readEpubWithReport(resources, result, normalizeEncoding, policy);
  }

  private ReadResult readEpubWithReport(
      Resources resources,
      Book result,
      boolean normalizeEncoding,
      EpubProcessingPolicy effectivePolicy)
      throws IOException {
    return readEpubWithReport(
        new ResourcesLoader.ResourceLoadResult(resources, List.of()),
        result,
        normalizeEncoding,
        effectivePolicy);
  }

  private ReadResult readEpubWithReport(
      ResourcesLoader.ResourceLoadResult loadResult,
      Book result,
      boolean normalizeEncoding,
      EpubProcessingPolicy effectivePolicy)
      throws IOException {
    if (result == null) {
      result = new Book();
    }

    EpubProcessingPolicy resolvedPolicy =
        effectivePolicy == null ? EpubProcessingPolicy.defaultPolicy() : effectivePolicy;
    boolean strictMode = resolvedPolicy.mode() == EpubProcessingPolicy.Mode.STRICT;
    List<IngestionWarning> warnings = new ArrayList<>(loadResult.warnings());
    List<String> corrections = new ArrayList<>();

    Resources resources = loadResult.resources();
    int resourceCount = resources.size();

    // Resources may contain non-UTF-8 text or unsafe XHTML  -  normalize before OPF parsing
    // depends on consistent encoding. Parallelise when possible to reduce wall-clock time.
    boolean doSanitize = resolvedPolicy.sanitizeXhtml();
    if (normalizeEncoding || doSanitize) {
      if (resolvedPolicy.parallelLoading() && resourceCount > 1) {
        processResourcesParallel(resources, normalizeEncoding, doSanitize);
      } else {
        if (normalizeEncoding) {
          normalizeResourcesEncoding(resources);
        }
        if (doSanitize) {
          sanitizeXhtmlResources(resources);
        }
      }
    }

    validateAndHandleMimeType(resources, strictMode, warnings, corrections);

    // Guard parsing against adversarial XML that could cause unbounded processing
    long timeoutMs = resolvedPolicy.parseTimeoutMs();
    if (timeoutMs > 0) {
      parseWithTimeout(resources, result, strictMode, warnings, corrections, timeoutMs);
    } else {
      parseSequential(resources, result, strictMode, warnings, corrections);
    }

    enforcePostReadConsistency(result, strictMode, warnings, corrections);

    result = postProcessBook(result);
    return new ReadResult(result, new IngestionReport(warnings, corrections));
  }

  /** Runs OPF + TOC parsing sequentially without timeout protection. */
  private void parseSequential(
      Resources resources,
      Book result,
      boolean strictMode,
      List<IngestionWarning> warnings,
      List<String> corrections)
      throws IOException {
    String packageResourceHref = getPackageResourceHref(resources, strictMode, warnings);
    Resource packageResource =
        processPackageResource(
            packageResourceHref, result, resources, strictMode, warnings, corrections);
    result.setOpfResource(packageResource);
    Resource ncxResource =
        processNcxResource(packageResource, result, strictMode, warnings, corrections);
    result.setNcxResource(ncxResource);
  }

  /**
   * Runs OPF + TOC parsing inside a structured task scope with timeout. Prevents adversarial XML
   * from causing indefinite hangs.
   */
  private void parseWithTimeout(
      Resources resources,
      Book result,
      boolean strictMode,
      List<IngestionWarning> warnings,
      List<String> corrections,
      long timeoutMs)
      throws IOException {
    try (var scope =
        StructuredTaskScope.open(
            StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
            cfg -> cfg.withTimeout(Duration.ofMillis(timeoutMs)))) {

      scope.fork(
          () -> {
            parseSequential(resources, result, strictMode, warnings, corrections);
            return null;
          });
      scope.join();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("EPUB parsing was interrupted", e);
    } catch (StructuredTaskScope.TimeoutException e) {
      throw new IOException(
          "EPUB parsing timed out after " + timeoutMs + "ms  -  possible adversarial content", e);
    } catch (StructuredTaskScope.FailedException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException ioe) throw ioe;
      throw new IOException("EPUB parsing failed", cause);
    }
  }

  /** Normalize encoding of all text resources to UTF-8. */
  private static void normalizeResourcesEncoding(Resources resources) {
    for (Resource resource : resources.getAll()) {
      try {
        EncodingNormalizer.normalizeToUtf8(resource);
      } catch (IOException e) {
        log.log(
            System.Logger.Level.WARNING,
            "Failed to normalize encoding for " + resource.getHref() + ": " + e.getMessage());
      }
    }
  }

  /**
   * Strip dangerous XHTML elements (scripts, iframes, object, embed, etc.) from all XHTML resources
   * at read time.
   */
  private static void sanitizeXhtmlResources(Resources resources) {
    for (Resource resource : resources.getAll()) {
      if (resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }
      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;
        String content = new String(data, StandardCharsets.UTF_8);
        String sanitized = XhtmlSecurityStrip.strip(content);
        if (!sanitized.equals(content)) {
          resource.setData(sanitized.getBytes(StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Failed to sanitize XHTML: " + resource.getHref());
      }
    }
  }

  /**
   * Process resources in parallel using structured concurrency. Each resource gets encoding
   * normalization and/or XHTML sanitization on its own virtual thread. Uses awaitAll() joiner so
   * individual failures are logged but don't cancel other tasks.
   */
  private static void processResourcesParallel(
      Resources resources, boolean normalizeEncoding, boolean sanitizeXhtml) {
    List<Resource> allResources = new ArrayList<>(resources.getAll());
    if (allResources.isEmpty()) {
      return;
    }

    try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {

      for (Resource resource : allResources) {
        scope.fork(
            () -> {
              processResource(resource, normalizeEncoding, sanitizeXhtml);
              return null;
            });
      }
      scope.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void processResource(
      Resource resource, boolean normalizeEncoding, boolean sanitizeXhtml) {
    if (normalizeEncoding) {
      try {
        EncodingNormalizer.normalizeToUtf8(resource);
      } catch (IOException e) {
        log.log(
            System.Logger.Level.WARNING,
            "Failed to normalize encoding for " + resource.getHref() + ": " + e.getMessage());
      }
    }
    if (sanitizeXhtml && resource.getMediaType() == MediaTypes.XHTML) {
      try {
        byte[] data = resource.getData();
        if (data != null && data.length > 0) {
          String content = new String(data, StandardCharsets.UTF_8);
          String sanitized = XhtmlSecurityStrip.strip(content);
          if (!sanitized.equals(content)) {
            resource.setData(sanitized.getBytes(StandardCharsets.UTF_8));
          }
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Failed to sanitize XHTML: " + resource.getHref());
      }
    }
  }

  private Book postProcessBook(Book book) {
    if (bookProcessor != null) {
      book = bookProcessor.processBook(book);
    }
    return book;
  }

  private Resource processNcxResource(
      Resource packageResource,
      Book book,
      boolean strictMode,
      List<IngestionWarning> warnings,
      List<String> corrections)
      throws IOException {
    if (packageResource == null) {
      return null;
    }

    if (book.getSpine().getTocResource() == null) {
      if (!book.getSpine().isEmpty()) {
        String message = "Spine has content but no table of contents resource was found";
        if (strictMode) {
          throw new IOException(IngestionCode.TOC_MISSING + ": " + message);
        }
        addWarning(warnings, IngestionCode.TOC_MISSING, message, null);
        book.setTableOfContents(TocSynthesizer.synthesize(book));
        corrections.add("Synthesized table of contents from spine due to missing TOC resource");
      }
      return null;
    }

    Resource ncxResource = NCXDocument.read(book, this);
    if (book.getTableOfContents().size() == 0 && !book.getSpine().isEmpty()) {
      String message = "Table of contents resource could not be parsed into a usable TOC";
      if (strictMode) {
        throw new IOException(IngestionCode.TOC_INVALID + ": " + message);
      }
      addWarning(
          warnings, IngestionCode.TOC_INVALID, message, book.getSpine().getTocResource().getHref());
      book.setTableOfContents(TocSynthesizer.synthesize(book));
      corrections.add("Synthesized table of contents from spine due to invalid TOC resource");
    }
    return ncxResource;
  }

  private Resource processPackageResource(
      String packageResourceHref,
      Book book,
      Resources resources,
      boolean strictMode,
      List<IngestionWarning> warnings,
      List<String> corrections)
      throws IOException {
    Resource packageResource = resources.remove(packageResourceHref);
    if (packageResource == null) {
      if (strictMode) {
        throw new IOException(
            IngestionCode.OPF_MISSING_PACKAGE_RESOURCE
                + ": package resource not found at "
                + packageResourceHref);
      }

      Resource fallback = findFallbackPackageResource(resources);
      if (fallback == null) {
        addWarning(
            warnings,
            IngestionCode.OPF_MISSING_PACKAGE_RESOURCE,
            "Package resource not found at "
                + packageResourceHref
                + " and no fallback .opf was available",
            packageResourceHref);
        book.setResources(resources);
        return null;
      }

      resources.remove(fallback.getHref());
      packageResource = fallback;
      addWarning(
          warnings,
          IngestionCode.OPF_MISSING_PACKAGE_RESOURCE,
          "Package resource not found at "
              + packageResourceHref
              + ", using fallback "
              + fallback.getHref(),
          packageResourceHref);
      corrections.add("Used fallback OPF resource: " + fallback.getHref());
    }

    try {
      PackageDocumentReader.read(packageResource, this, book, resources);
    } catch (Exception e) {
      if (strictMode) {
        throw new IOException(
            IngestionCode.OPF_PARSE_ERROR
                + ": failed to parse package resource "
                + packageResource.getHref(),
            e);
      }
      addWarning(
          warnings,
          IngestionCode.OPF_PARSE_ERROR,
          "Failed to parse package resource " + packageResource.getHref() + ": " + e.getMessage(),
          packageResource.getHref());
      corrections.add("Recovered from OPF parse failure by keeping already-loaded resources");
      book.setResources(resources);
    }
    return packageResource;
  }

  private static Resource findFallbackPackageResource(Resources resources) {
    for (Resource resource : resources.getAll()) {
      String href = resource.getHref();
      if (href != null && href.toLowerCase(Locale.ROOT).endsWith(".opf")) {
        return resource;
      }
    }
    return null;
  }

  private static String getPackageResourceHref(
      Resources resources, boolean strictMode, List<IngestionWarning> warnings) throws IOException {
    String defaultResult = "OEBPS/content.opf";
    String result = defaultResult;

    Resource containerResource = resources.remove("META-INF/container.xml");
    if (containerResource == null) {
      addWarning(
          warnings,
          IngestionCode.CONTAINER_MISSING,
          "container.xml is missing, falling back to default OPF location",
          "META-INF/container.xml");
      return result;
    }
    try {
      Document document = ResourceUtil.getAsDocument(containerResource);
      Element rootFileElement =
          (Element)
              ((Element) document.getDocumentElement().getElementsByTagName("rootfiles").item(0))
                  .getElementsByTagName("rootfile")
                  .item(0);
      result = rootFileElement.getAttribute("full-path");
    } catch (Exception e) {
      if (strictMode) {
        throw new IOException(
            IngestionCode.CONTAINER_PARSE_ERROR + ": failed to parse META-INF/container.xml", e);
      }
      addWarning(
          warnings,
          IngestionCode.CONTAINER_PARSE_ERROR,
          "Failed to parse container.xml, falling back to default OPF location: " + e.getMessage(),
          "META-INF/container.xml");
    }
    if (StringUtil.isBlank(result)) {
      if (strictMode) {
        throw new IOException(
            IngestionCode.CONTAINER_PARSE_ERROR
                + ": container.xml did not specify rootfile full-path");
      }
      addWarning(
          warnings,
          IngestionCode.CONTAINER_PARSE_ERROR,
          "container.xml had no rootfile full-path, falling back to default OPF location",
          "META-INF/container.xml");
      result = defaultResult;
    }
    return result;
  }

  private static void addWarning(
      List<IngestionWarning> warnings, IngestionCode code, String message, String resourceHref) {
    warnings.add(new IngestionWarning(code, message, resourceHref));
    log.log(
        System.Logger.Level.WARNING,
        code.name() + ": " + message + (resourceHref == null ? "" : " [" + resourceHref + "]"));
  }

  private static void enforcePostReadConsistency(
      Book book, boolean strictMode, List<IngestionWarning> warnings, List<String> corrections)
      throws IOException {
    int removedSpine = sanitizeSpineReferences(book, strictMode);
    if (removedSpine > 0 && !strictMode) {
      addWarning(
          warnings,
          IngestionCode.SPINE_BROKEN_REFERENCE,
          "Removed "
              + removedSpine
              + " invalid spine reference(s) that pointed to missing resources",
          null);
      corrections.add("Removed invalid spine references: " + removedSpine);
    }

    int removedToc = sanitizeTocReferences(book, strictMode);
    if (removedToc > 0 && !strictMode) {
      addWarning(
          warnings,
          IngestionCode.TOC_BROKEN_REFERENCE,
          "Removed " + removedToc + " invalid TOC reference(s) that pointed to missing resources",
          null);
      corrections.add("Removed invalid TOC references: " + removedToc);
    }
  }

  private static int sanitizeSpineReferences(Book book, boolean strictMode) throws IOException {
    List<SpineReference> original = book.getSpine().getSpineReferences();
    List<SpineReference> valid = new ArrayList<>(original.size());
    int removed = 0;
    for (SpineReference ref : original) {
      if (ref == null
          || ref.getResource() == null
          || ref.getResource().getHref() == null
          || !book.getResources().containsByHref(ref.getResource().getHref())) {
        removed++;
      } else {
        valid.add(ref);
      }
    }

    if (removed > 0) {
      if (strictMode) {
        throw new IOException(
            IngestionCode.SPINE_BROKEN_REFERENCE
                + ": found "
                + removed
                + " invalid spine reference(s)");
      }
      book.getSpine().setSpineReferences(valid);
    }
    return removed;
  }

  private static int sanitizeTocReferences(Book book, boolean strictMode) throws IOException {
    List<TOCReference> roots = book.getTableOfContents().getTocReferences();
    int removed = pruneInvalidTocReferences(roots, book.getResources());
    if (removed > 0 && strictMode) {
      throw new IOException(
          IngestionCode.TOC_BROKEN_REFERENCE + ": found " + removed + " invalid TOC reference(s)");
    }
    return removed;
  }

  private static int pruneInvalidTocReferences(List<TOCReference> refs, Resources resources) {
    if (refs == null || refs.isEmpty()) {
      return 0;
    }
    int removed = 0;
    for (int i = refs.size() - 1; i >= 0; i--) {
      TOCReference ref = refs.get(i);
      removed += pruneInvalidTocReferences(ref.getChildren(), resources);
      Resource resource = ref.getResource();
      if (resource != null
          && (resource.getHref() == null || !resources.containsByHref(resource.getHref()))) {
        refs.remove(i);
        removed++;
      }
    }
    return removed;
  }

  private static void validateAndHandleMimeType(
      Resources resources,
      boolean strictMode,
      List<IngestionWarning> warnings,
      List<String> corrections)
      throws IOException {
    Resource mimeTypeResource = resources.remove("mimetype");
    if (mimeTypeResource == null) {
      if (strictMode) {
        throw new IOException(
            IngestionCode.MIMETYPE_MISSING + ": EPUB archive is missing required mimetype entry");
      }
      addWarning(
          warnings,
          IngestionCode.MIMETYPE_MISSING,
          "EPUB archive is missing required mimetype entry",
          "mimetype");
      return;
    }

    try {
      String mimeType = new String(mimeTypeResource.getData(), StandardCharsets.UTF_8).trim();
      if (!"application/epub+zip".equals(mimeType)) {
        if (strictMode) {
          throw new IOException(
              IngestionCode.MIMETYPE_INVALID
                  + ": invalid mimetype entry, expected application/epub+zip but found "
                  + mimeType);
        }
        addWarning(
            warnings,
            IngestionCode.MIMETYPE_INVALID,
            "Invalid mimetype entry value, expected application/epub+zip but found: " + mimeType,
            "mimetype");
        corrections.add("Accepted non-standard mimetype value in recover mode: " + mimeType);
      }
    } catch (IOException e) {
      if (strictMode) {
        throw e;
      }
      addWarning(
          warnings,
          IngestionCode.MIMETYPE_INVALID,
          "Failed to read mimetype entry: " + e.getMessage(),
          "mimetype");
    }
  }

  private static void validateOrThrow(Path epubPath) throws IOException {
    // Lightweight structural validation (OCF/OPF checks only).
    // For deep W3C conformance validation, use EpubValidator.validateFull(Path) directly.
    EpubValidator.DetailedValidationResult result = EpubValidator.validateDetailed(epubPath);
    if (result.hasErrors()) {
      throw new EpubValidationException(result);
    }
  }
}
