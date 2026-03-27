package org.grimmory.comic4j.image.processing;

import org.grimmory.comic4j.domain.ReadingDirection;

/**
 * Configuration options for comic page processing pipeline. Controls which operations are applied
 * and their parameters.
 *
 * <p>Use {@link #builder()} to construct instances with sensible defaults.
 */
public record ComicProcessingOptions(
    boolean splitLandscape,
    ReadingDirection readingDirection,
    boolean removeBorders,
    int borderTolerance,
    float borderMinContentPercent,
    boolean normalize,
    boolean sharpen,
    float sharpenSigma,
    float sharpenGain,
    boolean despeckle,
    boolean grayscale,
    boolean quantize,
    int quantizeLevels,
    boolean resize,
    int maxWidth,
    int maxHeight,
    boolean keepAspectRatio) {

  public static Builder builder() {
    return new Builder();
  }

  public static ComicProcessingOptions defaults() {
    return builder().build();
  }

  public static final class Builder {
    private boolean splitLandscape = false;
    private ReadingDirection readingDirection = ReadingDirection.LEFT_TO_RIGHT;
    private boolean removeBorders = false;
    private int borderTolerance = 10;
    private float borderMinContentPercent = 0.5f;
    private boolean normalize = false;
    private boolean sharpen = false;
    private float sharpenSigma = 3.0f;
    private float sharpenGain = 1.0f;
    private boolean despeckle = false;
    private boolean grayscale = false;
    private boolean quantize = false;
    private int quantizeLevels = 16;
    private boolean resize = false;
    private int maxWidth = 2048;
    private int maxHeight = 2048;
    private boolean keepAspectRatio = true;

    private Builder() {}

    public Builder splitLandscape(boolean v) {
      this.splitLandscape = v;
      return this;
    }

    public Builder readingDirection(ReadingDirection v) {
      this.readingDirection = v;
      return this;
    }

    public Builder removeBorders(boolean v) {
      this.removeBorders = v;
      return this;
    }

    public Builder borderTolerance(int v) {
      this.borderTolerance = v;
      return this;
    }

    public Builder borderMinContentPercent(float v) {
      this.borderMinContentPercent = v;
      return this;
    }

    public Builder normalize(boolean v) {
      this.normalize = v;
      return this;
    }

    public Builder sharpen(boolean v) {
      this.sharpen = v;
      return this;
    }

    public Builder sharpenSigma(float v) {
      this.sharpenSigma = v;
      return this;
    }

    public Builder sharpenGain(float v) {
      this.sharpenGain = v;
      return this;
    }

    public Builder despeckle(boolean v) {
      this.despeckle = v;
      return this;
    }

    public Builder grayscale(boolean v) {
      this.grayscale = v;
      return this;
    }

    public Builder quantize(boolean v) {
      this.quantize = v;
      return this;
    }

    public Builder quantizeLevels(int v) {
      this.quantizeLevels = v;
      return this;
    }

    public Builder resize(boolean v) {
      this.resize = v;
      return this;
    }

    public Builder maxWidth(int v) {
      this.maxWidth = v;
      return this;
    }

    public Builder maxHeight(int v) {
      this.maxHeight = v;
      return this;
    }

    public Builder keepAspectRatio(boolean v) {
      this.keepAspectRatio = v;
      return this;
    }

    public ComicProcessingOptions build() {
      return new ComicProcessingOptions(
          splitLandscape,
          readingDirection,
          removeBorders,
          borderTolerance,
          borderMinContentPercent,
          normalize,
          sharpen,
          sharpenSigma,
          sharpenGain,
          despeckle,
          grayscale,
          quantize,
          quantizeLevels,
          resize,
          maxWidth,
          maxHeight,
          keepAspectRatio);
    }
  }
}
