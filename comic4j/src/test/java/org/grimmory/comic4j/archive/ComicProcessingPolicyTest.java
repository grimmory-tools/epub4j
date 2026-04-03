package org.grimmory.comic4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComicProcessingPolicyTest {

  @Test
  void defaults_hasReasonableValues() {
    ComicProcessingPolicy policy = ComicProcessingPolicy.defaults();
    assertEquals(512L * 1024 * 1024, policy.maxArchiveBytes());
    assertEquals(64L * 1024 * 1024, policy.maxEntryBytes());
    assertEquals(1024L * 1024 * 1024, policy.maxTotalUncompressedBytes());
    assertEquals(10_000, policy.maxEntries());
    assertEquals(20_000_000L, policy.maxPixelCount());
  }

  @Test
  void builder_customValues() {
    ComicProcessingPolicy policy =
        ComicProcessingPolicy.builder()
            .maxArchiveBytes(100L * 1024 * 1024)
            .maxEntryBytes(10L * 1024 * 1024)
            .maxTotalUncompressedBytes(500L * 1024 * 1024)
            .maxEntries(5_000)
            .maxPixelCount(10_000_000L)
            .build();

    assertEquals(100L * 1024 * 1024, policy.maxArchiveBytes());
    assertEquals(10L * 1024 * 1024, policy.maxEntryBytes());
    assertEquals(500L * 1024 * 1024, policy.maxTotalUncompressedBytes());
    assertEquals(5_000, policy.maxEntries());
    assertEquals(10_000_000L, policy.maxPixelCount());
  }

  @Test
  void unlimited_hasMaxValues() {
    ComicProcessingPolicy policy = ComicProcessingPolicy.unlimited();
    assertEquals(Long.MAX_VALUE, policy.maxArchiveBytes());
    assertEquals(Long.MAX_VALUE, policy.maxEntryBytes());
    assertEquals(Long.MAX_VALUE, policy.maxTotalUncompressedBytes());
    assertEquals(Integer.MAX_VALUE, policy.maxEntries());
    assertEquals(Long.MAX_VALUE, policy.maxPixelCount());
  }

  @Test
  void invalidValues_throw() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ComicProcessingPolicy.builder().maxArchiveBytes(0).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ComicProcessingPolicy.builder().maxArchiveBytes(-1).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ComicProcessingPolicy.builder().maxEntryBytes(0).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ComicProcessingPolicy.builder().maxTotalUncompressedBytes(0).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ComicProcessingPolicy.builder().maxEntries(0).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ComicProcessingPolicy.builder().maxPixelCount(0).build());
  }
}
