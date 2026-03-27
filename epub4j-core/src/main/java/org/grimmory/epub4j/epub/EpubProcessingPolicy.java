package org.grimmory.epub4j.epub;

/**
 * Processing policy for EPUB ingestion and resource loading.
 *
 * <p>Resource-heavy operations are guarded by explicit memory and entry-count budgets to keep
 * ingestion predictable under malformed or oversized archives.
 *
 * <p>Concurrency settings control parallel resource loading via virtual threads. When {@code
 * parallelLoading} is enabled, archive entries are decompressed and decoded in parallel, bounded by
 * {@code maxConcurrency}.
 *
 * <p>The {@code parseTimeoutMs} setting guards against adversarial input that could cause parsing
 * to hang indefinitely.
 */
public record EpubProcessingPolicy(
    Mode mode,
    long maxArchiveBytes,
    int maxEntries,
    long maxEntryBytes,
    long maxTotalUncompressedBytes,
    boolean sanitizeXhtml,
    boolean parallelLoading,
    int maxConcurrency,
    long parseTimeoutMs) {

  public enum Mode {
    STRICT,
    RECOVER
  }

  public static final long DEFAULT_MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
  public static final int DEFAULT_MAX_ENTRIES = 20_000;
  public static final long DEFAULT_MAX_ENTRY_BYTES = 64L * 1024 * 1024;
  public static final long DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;
  public static final int DEFAULT_MAX_CONCURRENCY = Runtime.getRuntime().availableProcessors();
  public static final long DEFAULT_PARSE_TIMEOUT_MS = 30_000L;

  public EpubProcessingPolicy {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    if (maxArchiveBytes <= 0) {
      throw new IllegalArgumentException("maxArchiveBytes must be > 0");
    }
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be > 0");
    }
    if (maxEntryBytes <= 0) {
      throw new IllegalArgumentException("maxEntryBytes must be > 0");
    }
    if (maxTotalUncompressedBytes <= 0) {
      throw new IllegalArgumentException("maxTotalUncompressedBytes must be > 0");
    }
    if (maxConcurrency <= 0) {
      throw new IllegalArgumentException("maxConcurrency must be > 0");
    }
    if (parseTimeoutMs < 0) {
      throw new IllegalArgumentException("parseTimeoutMs must be >= 0 (0 = no timeout)");
    }
  }

  public static EpubProcessingPolicy defaultPolicy() {
    return new EpubProcessingPolicy(
        Mode.RECOVER,
        DEFAULT_MAX_ARCHIVE_BYTES,
        DEFAULT_MAX_ENTRIES,
        DEFAULT_MAX_ENTRY_BYTES,
        DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES,
        true,
        true,
        DEFAULT_MAX_CONCURRENCY,
        DEFAULT_PARSE_TIMEOUT_MS);
  }

  public static EpubProcessingPolicy strictPolicy() {
    return new EpubProcessingPolicy(
        Mode.STRICT,
        DEFAULT_MAX_ARCHIVE_BYTES,
        DEFAULT_MAX_ENTRIES,
        DEFAULT_MAX_ENTRY_BYTES,
        DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES,
        true,
        true,
        DEFAULT_MAX_CONCURRENCY,
        DEFAULT_PARSE_TIMEOUT_MS);
  }

  public EpubProcessingPolicy withMode(Mode value) {
    return new EpubProcessingPolicy(
        value,
        maxArchiveBytes,
        maxEntries,
        maxEntryBytes,
        maxTotalUncompressedBytes,
        sanitizeXhtml,
        parallelLoading,
        maxConcurrency,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withMaxArchiveBytes(long value) {
    return new EpubProcessingPolicy(
        mode,
        value,
        maxEntries,
        maxEntryBytes,
        maxTotalUncompressedBytes,
        sanitizeXhtml,
        parallelLoading,
        maxConcurrency,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withMaxEntries(int value) {
    return new EpubProcessingPolicy(
        mode,
        maxArchiveBytes,
        value,
        maxEntryBytes,
        maxTotalUncompressedBytes,
        sanitizeXhtml,
        parallelLoading,
        maxConcurrency,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withMaxEntryBytes(long value) {
    return new EpubProcessingPolicy(
        mode,
        maxArchiveBytes,
        maxEntries,
        value,
        maxTotalUncompressedBytes,
        sanitizeXhtml,
        parallelLoading,
        maxConcurrency,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withMaxTotalUncompressedBytes(long value) {
    return new EpubProcessingPolicy(
        mode,
        maxArchiveBytes,
        maxEntries,
        maxEntryBytes,
        value,
        sanitizeXhtml,
        parallelLoading,
        maxConcurrency,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withSanitizeXhtml(boolean value) {
    return new EpubProcessingPolicy(
        mode,
        maxArchiveBytes,
        maxEntries,
        maxEntryBytes,
        maxTotalUncompressedBytes,
        value,
        parallelLoading,
        maxConcurrency,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withParallelLoading(boolean value) {
    return new EpubProcessingPolicy(
        mode,
        maxArchiveBytes,
        maxEntries,
        maxEntryBytes,
        maxTotalUncompressedBytes,
        sanitizeXhtml,
        value,
        maxConcurrency,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withMaxConcurrency(int value) {
    return new EpubProcessingPolicy(
        mode,
        maxArchiveBytes,
        maxEntries,
        maxEntryBytes,
        maxTotalUncompressedBytes,
        sanitizeXhtml,
        parallelLoading,
        value,
        parseTimeoutMs);
  }

  public EpubProcessingPolicy withParseTimeoutMs(long value) {
    return new EpubProcessingPolicy(
        mode,
        maxArchiveBytes,
        maxEntries,
        maxEntryBytes,
        maxTotalUncompressedBytes,
        sanitizeXhtml,
        parallelLoading,
        maxConcurrency,
        value);
  }
}
