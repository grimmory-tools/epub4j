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
 *
 * <p>When {@code useOffHeapResources} is enabled, resources exceeding {@code offHeapThresholdBytes}
 * are stored in off-heap memory via the Foreign Function and Memory API, reducing GC pressure for
 * large EPUBs.
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
    long parseTimeoutMs,
    boolean useOffHeapResources,
    long offHeapThresholdBytes) {

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
  public static final long DEFAULT_OFF_HEAP_THRESHOLD_BYTES = 64L * 1024;

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
    if (offHeapThresholdBytes < 0) {
      throw new IllegalArgumentException("offHeapThresholdBytes must be >= 0");
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
        DEFAULT_PARSE_TIMEOUT_MS,
        true,
        DEFAULT_OFF_HEAP_THRESHOLD_BYTES);
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
        DEFAULT_PARSE_TIMEOUT_MS,
        true,
        DEFAULT_OFF_HEAP_THRESHOLD_BYTES);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(EpubProcessingPolicy base) {
    return new Builder(base);
  }

  public static final class Builder {
    private Mode mode = Mode.RECOVER;
    private long maxArchiveBytes = DEFAULT_MAX_ARCHIVE_BYTES;
    private int maxEntries = DEFAULT_MAX_ENTRIES;
    private long maxEntryBytes = DEFAULT_MAX_ENTRY_BYTES;
    private long maxTotalUncompressedBytes = DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES;
    private boolean sanitizeXhtml = true;
    private boolean parallelLoading = true;
    private int maxConcurrency = DEFAULT_MAX_CONCURRENCY;
    private long parseTimeoutMs = DEFAULT_PARSE_TIMEOUT_MS;
    private boolean useOffHeapResources = true;
    private long offHeapThresholdBytes = DEFAULT_OFF_HEAP_THRESHOLD_BYTES;

    private Builder() {}

    private Builder(EpubProcessingPolicy base) {
      this.mode = base.mode();
      this.maxArchiveBytes = base.maxArchiveBytes();
      this.maxEntries = base.maxEntries();
      this.maxEntryBytes = base.maxEntryBytes();
      this.maxTotalUncompressedBytes = base.maxTotalUncompressedBytes();
      this.sanitizeXhtml = base.sanitizeXhtml();
      this.parallelLoading = base.parallelLoading();
      this.maxConcurrency = base.maxConcurrency();
      this.parseTimeoutMs = base.parseTimeoutMs();
      this.useOffHeapResources = base.useOffHeapResources();
      this.offHeapThresholdBytes = base.offHeapThresholdBytes();
    }

    public Builder mode(Mode value) {
      this.mode = value;
      return this;
    }

    public Builder maxArchiveBytes(long value) {
      this.maxArchiveBytes = value;
      return this;
    }

    public Builder maxEntries(int value) {
      this.maxEntries = value;
      return this;
    }

    public Builder maxEntryBytes(long value) {
      this.maxEntryBytes = value;
      return this;
    }

    public Builder maxTotalUncompressedBytes(long value) {
      this.maxTotalUncompressedBytes = value;
      return this;
    }

    public Builder sanitizeXhtml(boolean value) {
      this.sanitizeXhtml = value;
      return this;
    }

    public Builder parallelLoading(boolean value) {
      this.parallelLoading = value;
      return this;
    }

    public Builder maxConcurrency(int value) {
      this.maxConcurrency = value;
      return this;
    }

    public Builder parseTimeoutMs(long value) {
      this.parseTimeoutMs = value;
      return this;
    }

    public Builder useOffHeapResources(boolean value) {
      this.useOffHeapResources = value;
      return this;
    }

    public Builder offHeapThresholdBytes(long value) {
      this.offHeapThresholdBytes = value;
      return this;
    }

    public EpubProcessingPolicy build() {
      return new EpubProcessingPolicy(
          mode,
          maxArchiveBytes,
          maxEntries,
          maxEntryBytes,
          maxTotalUncompressedBytes,
          sanitizeXhtml,
          parallelLoading,
          maxConcurrency,
          parseTimeoutMs,
          useOffHeapResources,
          offHeapThresholdBytes);
    }
  }
}
