package org.grimmory.epub4j.epub;

import java.util.Optional;

/**
 * Pluggable encoding detection strategy for byte data.
 *
 * <p>Implementations may use ICU, chardet, or other heuristic engines. The native module provides
 * an ICU-backed implementation via {@code NativeEncodingDetector}; register it with {@link
 * EncodingNormalizer#setEncodingDetector(EncodingDetector)} to enable heuristic fallback when BOM
 * and declarations are absent.
 */
@FunctionalInterface
public interface EncodingDetector {

  /**
   * Result of heuristic encoding detection.
   *
   * @param encoding Java charset name (e.g. "UTF-8", "windows-1251")
   * @param confidence 0–100, where 100 means certain
   */
  record DetectionResult(String encoding, int confidence) {}

  /**
   * Attempt to detect the encoding of the given byte data.
   *
   * @param data raw bytes to analyze
   * @return detection result, or empty if the detector cannot determine the encoding
   */
  Optional<DetectionResult> detect(byte[] data);
}
