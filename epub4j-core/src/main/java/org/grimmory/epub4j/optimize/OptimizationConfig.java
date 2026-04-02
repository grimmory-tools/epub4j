package org.grimmory.epub4j.optimize;

import java.util.Set;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;

/**
 * Configuration for the EPUB optimization pipeline. Each flag controls a specific optimization
 * step; disabled steps are skipped entirely.
 */
public record OptimizationConfig(
    boolean compressImages,
    int jpegQuality,
    int pngOptLevel,
    boolean removeUnusedResources,
    boolean subsetFonts,
    boolean optimizeCss,
    boolean deduplicateResources,
    Set<MediaType> imageMediaTypes) {

  public static final int DEFAULT_JPEG_QUALITY = 85;
  public static final int DEFAULT_PNG_OPT_LEVEL = 2;

  public OptimizationConfig {
    if (jpegQuality < 0 || jpegQuality > 100) {
      throw new IllegalArgumentException("jpegQuality must be between 0 and 100");
    }
    if (pngOptLevel < 1 || pngOptLevel > 7) {
      throw new IllegalArgumentException("pngOptLevel must be between 1 and 7");
    }
    imageMediaTypes = Set.copyOf(imageMediaTypes);
  }

  public static OptimizationConfig defaultConfig() {
    return new OptimizationConfig(
        true,
        DEFAULT_JPEG_QUALITY,
        DEFAULT_PNG_OPT_LEVEL,
        true,
        false,
        false,
        true,
        Set.of(MediaTypes.JPG, MediaTypes.PNG, MediaTypes.GIF));
  }

  public static OptimizationConfig aggressive() {
    return new OptimizationConfig(
        true,
        75,
        5,
        true,
        true,
        true,
        true,
        Set.of(MediaTypes.JPG, MediaTypes.PNG, MediaTypes.GIF));
  }

  public static OptimizationConfig none() {
    return new OptimizationConfig(
        false, DEFAULT_JPEG_QUALITY, DEFAULT_PNG_OPT_LEVEL, false, false, false, false, Set.of());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(OptimizationConfig base) {
    return new Builder(base);
  }

  public static final class Builder {
    private boolean compressImages = true;
    private int jpegQuality = DEFAULT_JPEG_QUALITY;
    private int pngOptLevel = DEFAULT_PNG_OPT_LEVEL;
    private boolean removeUnusedResources = true;
    private boolean subsetFonts;
    private boolean optimizeCss;
    private boolean deduplicateResources = true;
    private Set<MediaType> imageMediaTypes = Set.of(MediaTypes.JPG, MediaTypes.PNG, MediaTypes.GIF);

    private Builder() {}

    private Builder(OptimizationConfig base) {
      this.compressImages = base.compressImages();
      this.jpegQuality = base.jpegQuality();
      this.pngOptLevel = base.pngOptLevel();
      this.removeUnusedResources = base.removeUnusedResources();
      this.subsetFonts = base.subsetFonts();
      this.optimizeCss = base.optimizeCss();
      this.deduplicateResources = base.deduplicateResources();
      this.imageMediaTypes = base.imageMediaTypes();
    }

    public Builder compressImages(boolean value) {
      this.compressImages = value;
      return this;
    }

    public Builder jpegQuality(int value) {
      this.jpegQuality = value;
      return this;
    }

    public Builder pngOptLevel(int value) {
      this.pngOptLevel = value;
      return this;
    }

    public Builder removeUnusedResources(boolean value) {
      this.removeUnusedResources = value;
      return this;
    }

    public Builder subsetFonts(boolean value) {
      this.subsetFonts = value;
      return this;
    }

    public Builder optimizeCss(boolean value) {
      this.optimizeCss = value;
      return this;
    }

    public Builder deduplicateResources(boolean value) {
      this.deduplicateResources = value;
      return this;
    }

    public Builder imageMediaTypes(Set<MediaType> value) {
      this.imageMediaTypes = Set.copyOf(value);
      return this;
    }

    public OptimizationConfig build() {
      return new OptimizationConfig(
          compressImages,
          jpegQuality,
          pngOptLevel,
          removeUnusedResources,
          subsetFonts,
          optimizeCss,
          deduplicateResources,
          imageMediaTypes);
    }
  }
}
