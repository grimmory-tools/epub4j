/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.archive;

/**
 * Configurable limits for comic archive processing. Guards against zip bombs, oversized entries,
 * and archives with excessive entry counts.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ComicProcessingPolicy policy = ComicProcessingPolicy.defaults();
 * // Or with custom limits:
 * ComicProcessingPolicy policy = ComicProcessingPolicy.builder()
 *     .maxArchiveBytes(256L * 1024 * 1024)
 *     .maxEntryBytes(32L * 1024 * 1024)
 *     .maxEntries(5_000)
 *     .build();
 * }</pre>
 */
public record ComicProcessingPolicy(
    long maxArchiveBytes,
    long maxEntryBytes,
    long maxTotalUncompressedBytes,
    int maxEntries,
    long maxPixelCount) {

  /** Default maximum compressed archive size: 512 MB. */
  public static final long DEFAULT_MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;

  /** Default maximum single entry (uncompressed) size: 64 MB. */
  public static final long DEFAULT_MAX_ENTRY_BYTES = 64L * 1024 * 1024;

  /** Default maximum total uncompressed bytes: 1 GB. */
  public static final long DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;

  /** Default maximum number of entries in an archive. */
  public static final int DEFAULT_MAX_ENTRIES = 10_000;

  /** Default maximum pixel count for decompression bomb detection: 20 megapixels. */
  public static final long DEFAULT_MAX_PIXEL_COUNT = 20_000_000L;

  public ComicProcessingPolicy {
    if (maxArchiveBytes <= 0) {
      throw new IllegalArgumentException("maxArchiveBytes must be positive");
    }
    if (maxEntryBytes <= 0) {
      throw new IllegalArgumentException("maxEntryBytes must be positive");
    }
    if (maxTotalUncompressedBytes <= 0) {
      throw new IllegalArgumentException("maxTotalUncompressedBytes must be positive");
    }
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive");
    }
    if (maxPixelCount <= 0) {
      throw new IllegalArgumentException("maxPixelCount must be positive");
    }
  }

  /** Returns a policy with all default limits. */
  public static ComicProcessingPolicy defaults() {
    return new ComicProcessingPolicy(
        DEFAULT_MAX_ARCHIVE_BYTES,
        DEFAULT_MAX_ENTRY_BYTES,
        DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES,
        DEFAULT_MAX_ENTRIES,
        DEFAULT_MAX_PIXEL_COUNT);
  }

  /** Returns a builder pre-populated with default values. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a policy with no enforced limits (for trusted archives). */
  public static ComicProcessingPolicy unlimited() {
    return new ComicProcessingPolicy(
        Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);
  }

  public static final class Builder {

    private long maxArchiveBytes = DEFAULT_MAX_ARCHIVE_BYTES;
    private long maxEntryBytes = DEFAULT_MAX_ENTRY_BYTES;
    private long maxTotalUncompressedBytes = DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES;
    private int maxEntries = DEFAULT_MAX_ENTRIES;
    private long maxPixelCount = DEFAULT_MAX_PIXEL_COUNT;

    private Builder() {}

    public Builder maxArchiveBytes(long maxArchiveBytes) {
      this.maxArchiveBytes = maxArchiveBytes;
      return this;
    }

    public Builder maxEntryBytes(long maxEntryBytes) {
      this.maxEntryBytes = maxEntryBytes;
      return this;
    }

    public Builder maxTotalUncompressedBytes(long maxTotalUncompressedBytes) {
      this.maxTotalUncompressedBytes = maxTotalUncompressedBytes;
      return this;
    }

    public Builder maxEntries(int maxEntries) {
      this.maxEntries = maxEntries;
      return this;
    }

    public Builder maxPixelCount(long maxPixelCount) {
      this.maxPixelCount = maxPixelCount;
      return this;
    }

    public ComicProcessingPolicy build() {
      return new ComicProcessingPolicy(
          maxArchiveBytes, maxEntryBytes, maxTotalUncompressedBytes, maxEntries, maxPixelCount);
    }
  }
}
