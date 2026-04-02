package org.grimmory.epub4j.epub;

import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.StructuredTaskScope;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.grimmory.epub4j.archive.ArchiveReader;
import org.grimmory.epub4j.archive.FilenameValidator;
import org.grimmory.epub4j.archive.ZipLocalHeaderRecovery;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.util.CollectionUtil;
import org.grimmory.epub4j.util.ResourceUtil;

/**
 * Loads Resources from archive files using NightCompress/libarchive.
 *
 * @author paul
 */
public class ResourcesLoader {

  private static final System.Logger log = System.getLogger(ResourcesLoader.class.getName());
  private static final int COPY_BUFFER_SIZE = 8192;

  /** Result of loading resources, including any warnings for entries that were skipped. */
  public record ResourceLoadResult(
      Resources resources, List<EpubReader.IngestionWarning> warnings) {
    public ResourceLoadResult {
      warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
  }

  /**
   * Loads all entries from the archive at the given path.
   *
   * @param archivePath path to the EPUB/ZIP file
   * @param defaultHtmlEncoding encoding to use for XHTML files
   * @return the loaded resources
   * @throws IOException if reading fails
   */
  public static Resources loadResources(Path archivePath, String defaultHtmlEncoding)
      throws IOException {
    return loadResources(
        archivePath,
        defaultHtmlEncoding,
        Collections.emptyList(),
        EpubProcessingPolicy.defaultPolicy());
  }

  public static Resources loadResources(
      Path archivePath, String defaultHtmlEncoding, EpubProcessingPolicy policy)
      throws IOException {
    return loadResources(archivePath, defaultHtmlEncoding, Collections.emptyList(), policy);
  }

  /**
   * Loads entries from the archive, with lazy loading support for specified media types.
   *
   * <p>Resources whose MediaType is in lazyLoadedTypes will not have their contents loaded
   * immediately. Instead they are backed by an {@link EpubResourceProvider} that loads data on
   * demand from the ZIP file.
   *
   * @param archivePath path to the EPUB/ZIP file
   * @param defaultHtmlEncoding encoding to use for XHTML files
   * @param lazyLoadedTypes media types to load lazily
   * @return the loaded resources
   * @throws IOException if reading fails
   */
  public static Resources loadResources(
      Path archivePath, String defaultHtmlEncoding, List<MediaType> lazyLoadedTypes)
      throws IOException {
    return loadResources(
        archivePath, defaultHtmlEncoding, lazyLoadedTypes, EpubProcessingPolicy.defaultPolicy());
  }

  public static Resources loadResources(
      Path archivePath,
      String defaultHtmlEncoding,
      List<MediaType> lazyLoadedTypes,
      EpubProcessingPolicy policy)
      throws IOException {
    return loadResourcesWithWarnings(archivePath, defaultHtmlEncoding, lazyLoadedTypes, policy)
        .resources();
  }

  /**
   * Loads entries from the archive, returning both resources and any warnings for entries that were
   * skipped or had errors.
   */
  public static ResourceLoadResult loadResourcesWithWarnings(
      Path archivePath,
      String defaultHtmlEncoding,
      List<MediaType> lazyLoadedTypes,
      EpubProcessingPolicy policy)
      throws IOException {

    EpubProcessingPolicy resolvedPolicy =
        policy != null ? policy : EpubProcessingPolicy.defaultPolicy();
    boolean strictMode = isStrict(resolvedPolicy);

    validateArchiveFile(archivePath);
    validateArchiveBudget(archivePath, resolvedPolicy);

    LazyResourceProvider resourceProvider =
        new EpubResourceProvider(archivePath.toAbsolutePath().toString());

    Resources result = new Resources();
    List<EpubReader.IngestionWarning> warnings = new ArrayList<>();
    Set<String> seenPaths = new HashSet<>();

    try (ArchiveReader reader = ArchiveReader.openZip(archivePath)) {
      loadFromArchiveReader(
          reader,
          result,
          warnings,
          seenPaths,
          resolvedPolicy,
          strictMode,
          defaultHtmlEncoding,
          lazyLoadedTypes,
          resourceProvider);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      // Central directory may be corrupted  -  try local header recovery
      if (strictMode && ArchiveReader.isAvailable()) {
        // Only treat as fatal in strict mode when the native library IS available
        // (i.e., the archive itself is truly unreadable, not just a missing library)
        throw new IOException("Failed to read archive: " + archivePath, e);
      }
      // Either non-strict mode or native library unavailable  -  fall back to pure Java
      if (!ArchiveReader.isAvailable()) {
        loadFromJavaZip(
            archivePath,
            result,
            warnings,
            seenPaths,
            resolvedPolicy,
            strictMode,
            defaultHtmlEncoding,
            lazyLoadedTypes,
            resourceProvider);
      } else {
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.ARCHIVE_CORRUPTED,
                "Standard ZIP read failed, attempting local header recovery: " + e.getMessage(),
                null));
        loadFromRecovery(
            archivePath, result, warnings, seenPaths, resolvedPolicy, defaultHtmlEncoding);
      }
    }

    return new ResourceLoadResult(result, warnings);
  }

  /** Standard archive reading path via NightCompress/libarchive. */
  private static void loadFromArchiveReader(
      ArchiveReader reader,
      Resources result,
      List<EpubReader.IngestionWarning> warnings,
      Set<String> seenPaths,
      EpubProcessingPolicy resolvedPolicy,
      boolean strictMode,
      String defaultHtmlEncoding,
      List<MediaType> lazyLoadedTypes,
      LazyResourceProvider resourceProvider)
      throws IOException, LibArchiveException {
    int entryCount = 0;
    long totalUncompressedBytes = 0;

    ArchiveEntry entry;
    while ((entry = reader.nextEntry()) != null) {
      entryCount++;
      if (entryCount > resolvedPolicy.maxEntries()) {
        String message =
            "Archive entry count exceeds policy limit: "
                + entryCount
                + " > "
                + resolvedPolicy.maxEntries();
        if (strictMode) {
          throw new IOException(message);
        }
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.ARCHIVE_ENTRY_LIMIT, message, null));
        break;
      }

      String name = entry.getName();
      if (name == null || name.endsWith("/")) {
        continue;
      }

      if (FilenameValidator.isSystemFile(name)) {
        continue;
      }

      if (!FilenameValidator.isSafeEntryName(name)) {
        if (strictMode) {
          throw new IOException("Unsafe archive entry path: " + name);
        }
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.RESOURCE_UNSAFE_PATH,
                "Skipping unsafe archive entry: " + name,
                name));
        continue;
      }

      String normalizedPath = normalizeEntryPathKey(name);
      if (!seenPaths.add(normalizedPath)) {
        if (strictMode) {
          throw new IOException("Duplicate archive entry path: " + name);
        }
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.RESOURCE_DUPLICATE,
                "Skipping duplicate archive entry: " + name,
                name));
        continue;
      }

      long declaredSize = entry.getSize() != null ? entry.getSize() : -1;
      if (declaredSize > resolvedPolicy.maxEntryBytes()) {
        String message =
            "Archive entry exceeds policy size limit: "
                + name
                + " ("
                + declaredSize
                + " > "
                + resolvedPolicy.maxEntryBytes()
                + " bytes)";
        if (strictMode) {
          throw new IOException(message);
        }
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.RESOURCE_SIZE_EXCEEDED, message, name));
        continue;
      }
      if (declaredSize > 0) {
        totalUncompressedBytes += declaredSize;
        if (totalUncompressedBytes > resolvedPolicy.maxTotalUncompressedBytes()) {
          String message =
              "Archive total uncompressed size exceeds policy limit: "
                  + totalUncompressedBytes
                  + " > "
                  + resolvedPolicy.maxTotalUncompressedBytes()
                  + " bytes";
          if (strictMode) {
            throw new IOException(message);
          }
          warnings.add(
              new EpubReader.IngestionWarning(
                  EpubReader.IngestionCode.ARCHIVE_SIZE_LIMIT, message, null));
          break;
        }
      }

      try {
        Resource resource;
        if (shouldLoadLazy(name, lazyLoadedTypes)) {
          long size = entry.getSize() != null ? entry.getSize() : 0;
          resource = new LazyResource(resourceProvider, size, name);
        } else {
          try (InputStream entryStream = reader.getEntryInputStream()) {
            resource = ResourceUtil.createResource(name, entryStream);
            if (resource.getData() != null) {
              long actualSize = resource.getData().length;
              if (actualSize > resolvedPolicy.maxEntryBytes()) {
                String message =
                    "Archive entry exceeds policy size limit after read: "
                        + name
                        + " ("
                        + actualSize
                        + " > "
                        + resolvedPolicy.maxEntryBytes()
                        + " bytes)";
                if (strictMode) {
                  throw new IOException(message);
                }
                warnings.add(
                    new EpubReader.IngestionWarning(
                        EpubReader.IngestionCode.RESOURCE_SIZE_EXCEEDED, message, name));
                continue;
              }
              // Promote large resources to off-heap when policy enables it
              if (resolvedPolicy.useOffHeapResources()
                  && actualSize >= resolvedPolicy.offHeapThresholdBytes()) {
                byte[] data = resource.getData();
                resource = new OffHeapResource(data, name);
              }
            }
          }
        }

        if (resource.getMediaType() == MediaTypes.XHTML) {
          resource.setInputEncoding(defaultHtmlEncoding);
        }
        result.add(resource);
      } catch (IOException e) {
        if (strictMode) {
          throw e;
        }
        String message = "Failed to load resource " + name + ": " + e.getMessage();
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.RESOURCE_LOAD_ERROR, message, name));
        log.log(System.Logger.Level.WARNING, message);
      } catch (Exception e) {
        if (strictMode) {
          throw new IOException("Failed to load resource: " + name, e);
        }
        String message = "Failed to load resource " + name + ": " + e.getMessage();
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.RESOURCE_LOAD_ERROR, message, name));
        log.log(System.Logger.Level.WARNING, message);
      }
    }
  }

  /**
   * Fallback path: recover entries from local file headers when the central directory is corrupted
   * and libarchive cannot read the archive.
   */
  private static void loadFromRecovery(
      Path archivePath,
      Resources result,
      List<EpubReader.IngestionWarning> warnings,
      Set<String> seenPaths,
      EpubProcessingPolicy resolvedPolicy,
      String defaultHtmlEncoding)
      throws IOException {
    List<ZipLocalHeaderRecovery.RecoveredEntry> recovered;
    try {
      recovered = ZipLocalHeaderRecovery.recover(archivePath, resolvedPolicy.maxEntries());
    } catch (IOException e) {
      throw new IOException("Archive recovery also failed for: " + archivePath, e);
    }

    if (recovered.isEmpty()) {
      throw new IOException("No entries could be recovered from: " + archivePath);
    }

    long totalUncompressed = 0;
    for (var entry : recovered) {
      String name = entry.name();

      if (FilenameValidator.isSystemFile(name)) continue;

      String normalizedPath = normalizeEntryPathKey(name);
      if (!seenPaths.add(normalizedPath)) continue;

      if (entry.uncompressedSize() > resolvedPolicy.maxEntryBytes()) {
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.RESOURCE_SIZE_EXCEEDED,
                "Recovered entry exceeds size limit: " + name,
                name));
        continue;
      }

      totalUncompressed += entry.uncompressedSize();
      if (totalUncompressed > resolvedPolicy.maxTotalUncompressedBytes()) {
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.ARCHIVE_SIZE_LIMIT,
                "Recovered archive total size exceeds limit",
                null));
        break;
      }

      Resource resource = new Resource(entry.data(), name);
      if (resource.getMediaType() == MediaTypes.XHTML) {
        resource.setInputEncoding(defaultHtmlEncoding);
      }
      result.add(resource);
    }
  }

  /**
   * Loads all entries from the input stream as resources.
   *
   * <p>The stream is written to a temporary file first, since NightCompress requires a file path
   * for random-access reading.
   *
   * @param inputStream the input stream containing the archive data
   * @param defaultHtmlEncoding encoding to use for XHTML files
   * @return the loaded resources
   * @throws IOException if reading fails
   */
  public static Resources loadResources(InputStream inputStream, String defaultHtmlEncoding)
      throws IOException {
    return loadResources(inputStream, defaultHtmlEncoding, EpubProcessingPolicy.defaultPolicy());
  }

  public static Resources loadResources(
      InputStream inputStream, String defaultHtmlEncoding, EpubProcessingPolicy policy)
      throws IOException {
    return loadResourcesWithWarnings(inputStream, defaultHtmlEncoding, policy).resources();
  }

  /**
   * Loads entries from an input stream, returning both resources and any warnings for entries that
   * were skipped or had errors.
   */
  public static ResourceLoadResult loadResourcesWithWarnings(
      InputStream inputStream, String defaultHtmlEncoding, EpubProcessingPolicy policy)
      throws IOException {

    EpubProcessingPolicy resolvedPolicy =
        policy != null ? policy : EpubProcessingPolicy.defaultPolicy();
    boolean strictMode = isStrict(resolvedPolicy);

    Resources result = new Resources();
    List<EpubReader.IngestionWarning> warnings = new ArrayList<>();

    // NightCompress's libarchive binding can only read from file descriptors, not streams
    Path tempFile = Files.createTempFile("epub4j-resources-", ".tmp");
    try {
      copyToTempWithLimit(inputStream, tempFile, resolvedPolicy.maxArchiveBytes());

      validateArchiveFile(tempFile);
      validateArchiveBudget(tempFile, resolvedPolicy);

      Set<String> seenPaths = new HashSet<>();
      try (ArchiveReader reader = ArchiveReader.openZip(tempFile)) {
        loadFromArchiveReader(
            reader,
            result,
            warnings,
            seenPaths,
            resolvedPolicy,
            strictMode,
            defaultHtmlEncoding,
            Collections.emptyList(),
            null);
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        if (strictMode && ArchiveReader.isAvailable()) {
          throw new IOException("Failed to read archive from stream", e);
        }
        if (!ArchiveReader.isAvailable()) {
          loadFromJavaZip(
              tempFile,
              result,
              warnings,
              seenPaths,
              resolvedPolicy,
              strictMode,
              defaultHtmlEncoding,
              Collections.emptyList(),
              null);
        } else {
          warnings.add(
              new EpubReader.IngestionWarning(
                  EpubReader.IngestionCode.ARCHIVE_CORRUPTED,
                  "Standard ZIP read failed, attempting local header recovery: " + e.getMessage(),
                  null));
          loadFromRecovery(
              tempFile, result, warnings, seenPaths, resolvedPolicy, defaultHtmlEncoding);
        }
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }

    return new ResourceLoadResult(result, warnings);
  }

  /**
   * Validates that the file at the given path is a plausible archive file. Prevents native crashes
   * from libarchive when given empty or non-ZIP files.
   */
  private static void validateArchiveFile(Path path) throws IOException {
    long size = Files.size(path);
    if (size == 0) {
      throw new IOException("File is empty: " + path);
    }
    // Smaller than a bare EOCD record means the file is truncated or not a ZIP at all
    if (size < 22) {
      throw new IOException(
          "File is too small to be a valid archive (" + size + " bytes): " + path);
    }
    // Reject non-ZIP files early before handing to libarchive (avoids native-level errors)
    byte[] header = new byte[4];
    try (InputStream in = Files.newInputStream(path)) {
      int read = in.read(header);
      if (read < 4 || header[0] != 0x50 || header[1] != 0x4B) {
        throw new IOException("File does not appear to be a valid ZIP/EPUB archive: " + path);
      }
    }
  }

  private static void validateArchiveBudget(Path path, EpubProcessingPolicy policy)
      throws IOException {
    long size = Files.size(path);
    if (size > policy.maxArchiveBytes()) {
      throw new IOException(
          "Archive exceeds policy size limit: "
              + size
              + " > "
              + policy.maxArchiveBytes()
              + " bytes");
    }
  }

  private static boolean isStrict(EpubProcessingPolicy policy) {
    return policy.mode() == EpubProcessingPolicy.Mode.STRICT;
  }

  private static String normalizeEntryPathKey(String path) {
    return path.toLowerCase(Locale.ROOT);
  }

  private static void copyToTempWithLimit(InputStream inputStream, Path tempFile, long maxBytes)
      throws IOException {
    if (inputStream == null) {
      throw new IllegalArgumentException("inputStream must not be null");
    }
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    long total = 0;
    try (OutputStream out = Files.newOutputStream(tempFile)) {
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new IOException(
              "Archive stream exceeds policy size limit during copy: "
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
   * Pure Java ZIP fallback when NightCompress/libarchive is not available. Uses
   * java.util.zip.ZipFile for random-access reading, enabling parallel extraction via virtual
   * threads when parallelLoading is enabled.
   */
  private static void loadFromJavaZip(
      Path archivePath,
      Resources result,
      List<EpubReader.IngestionWarning> warnings,
      Set<String> seenPaths,
      EpubProcessingPolicy resolvedPolicy,
      boolean strictMode,
      String defaultHtmlEncoding,
      List<MediaType> lazyLoadedTypes,
      LazyResourceProvider resourceProvider)
      throws IOException {

    // First pass: validate entries and collect eligible ones (lightweight, sequential)
    int maxEntries = resolvedPolicy.maxEntries();
    long maxEntryBytes = resolvedPolicy.maxEntryBytes();
    long maxTotalUncompressedBytes = resolvedPolicy.maxTotalUncompressedBytes();
    List<ValidatedEntry> eligible = new ArrayList<>(Math.max(16, Math.min(maxEntries, 512)));
    int eligibleCount = 0;
    long totalUncompressedBytes = 0;

    try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        if (zipEntry.isDirectory()) continue;

        if (eligibleCount >= maxEntries) {
          String message =
              "Archive entry count exceeds policy limit: "
                  + (eligibleCount + 1)
                  + " > "
                  + maxEntries;
          if (strictMode) throw new IOException(message);
          warnings.add(
              new EpubReader.IngestionWarning(
                  EpubReader.IngestionCode.ARCHIVE_ENTRY_LIMIT, message, null));
          break;
        }

        String name = zipEntry.getName();
        if (name == null || name.isEmpty()) continue;
        if (FilenameValidator.isSystemFile(name)) continue;

        if (!FilenameValidator.isSafeEntryName(name)) {
          if (strictMode) throw new IOException("Unsafe archive entry path: " + name);
          warnings.add(
              new EpubReader.IngestionWarning(
                  EpubReader.IngestionCode.RESOURCE_UNSAFE_PATH,
                  "Skipping unsafe archive entry: " + name,
                  name));
          continue;
        }

        String normalizedPath = normalizeEntryPathKey(name);
        if (!seenPaths.add(normalizedPath)) {
          if (strictMode) throw new IOException("Duplicate archive entry path: " + name);
          warnings.add(
              new EpubReader.IngestionWarning(
                  EpubReader.IngestionCode.RESOURCE_DUPLICATE,
                  "Skipping duplicate archive entry: " + name,
                  name));
          continue;
        }

        long declaredSize = zipEntry.getSize();
        if (declaredSize > maxEntryBytes) {
          String message =
              "Archive entry exceeds policy size limit: "
                  + name
                  + " ("
                  + declaredSize
                  + " > "
                  + maxEntryBytes
                  + " bytes)";
          if (strictMode) throw new IOException(message);
          warnings.add(
              new EpubReader.IngestionWarning(
                  EpubReader.IngestionCode.RESOURCE_SIZE_EXCEEDED, message, name));
          continue;
        }
        if (declaredSize > 0) {
          totalUncompressedBytes += declaredSize;
          if (totalUncompressedBytes > maxTotalUncompressedBytes) {
            String message =
                "Archive total uncompressed size exceeds policy limit: "
                    + totalUncompressedBytes
                    + " > "
                    + maxTotalUncompressedBytes
                    + " bytes";
            if (strictMode) throw new IOException(message);
            warnings.add(
                new EpubReader.IngestionWarning(
                    EpubReader.IngestionCode.ARCHIVE_SIZE_LIMIT, message, null));
            break;
          }
        }

        eligible.add(new ValidatedEntry(zipEntry, name));
        eligibleCount++;
      }

      // Second pass: extract entries  -  parallel with virtual threads when enabled
      if (resolvedPolicy.parallelLoading() && eligible.size() > 1) {
        loadEntriesParallel(
            zipFile,
            eligible,
            result,
            resolvedPolicy,
            defaultHtmlEncoding,
            lazyLoadedTypes,
            resourceProvider);
      } else {
        loadEntriesSequential(
            zipFile,
            eligible,
            result,
            warnings,
            resolvedPolicy,
            strictMode,
            defaultHtmlEncoding,
            lazyLoadedTypes,
            resourceProvider);
      }
    }
  }

  /**
   * Extracts ZIP entries sequentially - used when parallelLoading is disabled or for very small
   * archives where threading overhead isn't worthwhile.
   */
  private static void loadEntriesSequential(
      ZipFile zipFile,
      List<ValidatedEntry> entries,
      Resources result,
      List<EpubReader.IngestionWarning> warnings,
      EpubProcessingPolicy policy,
      boolean strictMode,
      String defaultHtmlEncoding,
      List<MediaType> lazyLoadedTypes,
      LazyResourceProvider resourceProvider)
      throws IOException {

    for (ValidatedEntry ve : entries) {
      try {
        Resource resource =
            extractEntry(
                zipFile,
                ve.entry(),
                ve.name(),
                policy,
                lazyLoadedTypes,
                resourceProvider,
                defaultHtmlEncoding);
        result.add(resource);
      } catch (IOException e) {
        if (strictMode) throw e;
        warnings.add(
            new EpubReader.IngestionWarning(
                EpubReader.IngestionCode.RESOURCE_LOAD_ERROR,
                "Failed to load resource " + ve.name() + ": " + e.getMessage(),
                ve.name()));
      }
    }
  }

  private record ValidatedEntry(ZipEntry entry, String name) {}

  /**
   * Extracts ZIP entries in parallel using virtual threads. ZipFile is thread-safe for concurrent
   * reads of different entries.
   */
  private static void loadEntriesParallel(
      ZipFile zipFile,
      List<ValidatedEntry> entries,
      Resources result,
      EpubProcessingPolicy policy,
      String defaultHtmlEncoding,
      List<MediaType> lazyLoadedTypes,
      LazyResourceProvider resourceProvider)
      throws IOException {

    try (var scope =
        StructuredTaskScope.open(StructuredTaskScope.Joiner.<Resource>allSuccessfulOrThrow())) {

      for (ValidatedEntry ve : entries) {
        scope.fork(
            () ->
                extractEntry(
                    zipFile,
                    ve.entry(),
                    ve.name(),
                    policy,
                    lazyLoadedTypes,
                    resourceProvider,
                    defaultHtmlEncoding));
      }

      var resultStream = scope.join();
      resultStream.forEach(subtask -> result.add(subtask.get()));

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Parallel ZIP extraction interrupted", e);
    }
  }

  /**
   * Extracts a single ZIP entry into a Resource. Thread-safe: ZipFile supports concurrent reads of
   * different entries.
   */
  private static Resource extractEntry(
      ZipFile zipFile,
      ZipEntry zipEntry,
      String name,
      EpubProcessingPolicy policy,
      List<MediaType> lazyLoadedTypes,
      LazyResourceProvider resourceProvider,
      String defaultHtmlEncoding)
      throws IOException {

    Resource resource;
    if (shouldLoadLazy(name, lazyLoadedTypes) && resourceProvider != null) {
      long size = zipEntry.getSize();
      resource = new LazyResource(resourceProvider, Math.max(size, 0), name);
    } else {
      try (InputStream entryStream = zipFile.getInputStream(zipEntry)) {
        int initialCapacity = initialCapacityHint(zipEntry.getSize(), policy.maxEntryBytes());
        byte[] data = readStreamWithLimit(entryStream, policy.maxEntryBytes(), initialCapacity);
        if (policy.useOffHeapResources() && data.length >= policy.offHeapThresholdBytes()) {
          resource = new OffHeapResource(data, name);
        } else {
          resource = new Resource(data, name);
        }
      }
    }

    if (resource.getMediaType() == MediaTypes.XHTML) {
      resource.setInputEncoding(defaultHtmlEncoding);
    }
    return resource;
  }

  /** Reads stream data with a size guard to prevent zip bomb expansion. */
  private static byte[] readStreamWithLimit(InputStream in, long maxBytes, int initialCapacity)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(initialCapacity);
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    long total = 0;
    int read;
    while ((read = in.read(buffer)) != -1) {
      total += read;
      if (total > maxBytes) {
        throw new IOException(
            "Entry data exceeds size limit during decompression: " + total + " > " + maxBytes);
      }
      baos.write(buffer, 0, read);
    }
    return baos.toByteArray();
  }

  private static int initialCapacityHint(long declaredSize, long maxBytes) {
    if (declaredSize <= 0) {
      return COPY_BUFFER_SIZE;
    }
    long bounded = Math.min(declaredSize, maxBytes);
    return (int) Math.min(Integer.MAX_VALUE, Math.max(COPY_BUFFER_SIZE, bounded));
  }

  /**
   * Whether the given href will load a mediaType that is in the collection of
   * lazilyLoadedMediaTypes.
   */
  private static boolean shouldLoadLazy(String href, Collection<MediaType> lazilyLoadedMediaTypes) {
    if (CollectionUtil.isEmpty(lazilyLoadedMediaTypes)) {
      return false;
    }
    MediaType mediaType = MediaTypes.determineMediaType(href);
    return lazilyLoadedMediaTypes.contains(mediaType);
  }
}
