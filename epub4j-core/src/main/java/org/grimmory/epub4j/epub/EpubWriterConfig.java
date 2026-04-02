package org.grimmory.epub4j.epub;

import java.util.Set;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;

/**
 * Configuration for EPUB ZIP output. Controls compression level and per-media-type storage method.
 *
 * <p>Already-compressed formats (JPEG, PNG, GIF, audio, video) are stored uncompressed by default
 * since re-deflating wastes CPU for negligible size reduction.
 */
public record EpubWriterConfig(int compressionLevel, Set<MediaType> storeMediaTypes) {

  /** Default media types that are already compressed and should be stored without deflation. */
  private static final Set<MediaType> DEFAULT_STORE_MEDIA_TYPES =
      Set.of(
          MediaTypes.JPG,
          MediaTypes.PNG,
          MediaTypes.GIF,
          MediaTypes.MP3,
          MediaTypes.OGG,
          MediaTypes.MP4);

  public EpubWriterConfig {
    if (compressionLevel < 0 || compressionLevel > 9) {
      throw new IllegalArgumentException(
          "compressionLevel must be between 0 and 9, got: " + compressionLevel);
    }
    storeMediaTypes = Set.copyOf(storeMediaTypes);
  }

  /** Default config: compression level 6, store already-compressed media types. */
  public static EpubWriterConfig defaultConfig() {
    return new EpubWriterConfig(6, DEFAULT_STORE_MEDIA_TYPES);
  }

  /** Maximum compression. Text/XML resources benefit; images/audio are still stored. */
  public static EpubWriterConfig maxCompression() {
    return new EpubWriterConfig(9, DEFAULT_STORE_MEDIA_TYPES);
  }

  /** No compression at all. Fastest writes, largest output. */
  public static EpubWriterConfig noCompression() {
    return new EpubWriterConfig(0, DEFAULT_STORE_MEDIA_TYPES);
  }

  /** Whether the given media type should be stored without compression. */
  public boolean shouldStore(MediaType mediaType) {
    return mediaType != null && storeMediaTypes.contains(mediaType);
  }

  public EpubWriterConfig withCompressionLevel(int level) {
    return new EpubWriterConfig(level, storeMediaTypes);
  }

  public EpubWriterConfig withStoreMediaTypes(Set<MediaType> types) {
    return new EpubWriterConfig(compressionLevel, types);
  }
}
