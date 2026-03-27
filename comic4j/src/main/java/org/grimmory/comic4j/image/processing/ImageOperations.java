package org.grimmory.comic4j.image.processing;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Native pixel-level image operations using FFM direct memory access. All operations work on {@link
 * NativePixelBuffer} instances for maximum throughput.
 *
 * <p>Operations are ported from established image processing pipelines and optimized for comic book
 * page processing (high-resolution grayscale/color scans).
 */
public final class ImageOperations {

  private ImageOperations() {}

  // --- Grayscale conversion ---

  /**
   * Converts an image to grayscale using luminance weights (ITU-R BT.709). Operates directly on
   * native memory for cache-friendly sequential access.
   */
  public static void grayscale(NativePixelBuffer buffer) {
    MemorySegment seg = buffer.segment();
    int count = buffer.pixelCount();

    for (int i = 0; i < count; i++) {
      int argb = seg.getAtIndex(ValueLayout.JAVA_INT, i);
      int a = (argb >>> 24) & 0xFF;
      int r = (argb >>> 16) & 0xFF;
      int g = (argb >>> 8) & 0xFF;
      int b = argb & 0xFF;

      // BT.709 luminance: 0.2126R + 0.7152G + 0.0722B
      // Use fixed-point: (r*2126 + g*7152 + b*722) / 10000
      int lum = (r * 2126 + g * 7152 + b * 722) / 10000;
      seg.setAtIndex(ValueLayout.JAVA_INT, i, (a << 24) | (lum << 16) | (lum << 8) | lum);
    }
  }

  // --- Normalize (auto-levels) ---

  /**
   * Normalizes image levels by stretching the histogram to fill the full 0-255 range. Equivalent to
   * auto-levels in image editors. Uses a 0.5% clip on each end to ignore outlier pixels (dust,
   * sensor noise).
   */
  public static void normalize(NativePixelBuffer buffer) {
    MemorySegment seg = buffer.segment();
    int count = buffer.pixelCount();

    // Build luminance histogram
    int[] histogram = new int[256];
    for (int i = 0; i < count; i++) {
      int argb = seg.getAtIndex(ValueLayout.JAVA_INT, i);
      int r = (argb >>> 16) & 0xFF;
      int g = (argb >>> 8) & 0xFF;
      int b = argb & 0xFF;
      int lum = (r * 2126 + g * 7152 + b * 722) / 10000;
      histogram[lum]++;
    }

    // Find clip points at 0.5% on each end
    int clipPixels = Math.max(1, count / 200);
    int lo = 0, hi = 255;
    int cumLo = 0, cumHi = 0;
    while (lo < 255 && cumLo + histogram[lo] <= clipPixels) {
      cumLo += histogram[lo++];
    }
    while (hi > 0 && cumHi + histogram[hi] <= clipPixels) {
      cumHi += histogram[hi--];
    }

    if (lo >= hi) return; // Already normalized or uniform

    // Build lookup table
    int[] lut = new int[256];
    float scale = 255.0f / (hi - lo);
    for (int v = 0; v < 256; v++) {
      lut[v] = Math.clamp(Math.round((v - lo) * scale), 0, 255);
    }

    // Apply to all channels
    for (int i = 0; i < count; i++) {
      int argb = seg.getAtIndex(ValueLayout.JAVA_INT, i);
      int a = (argb >>> 24) & 0xFF;
      int r = lut[(argb >>> 16) & 0xFF];
      int g = lut[(argb >>> 8) & 0xFF];
      int b = lut[argb & 0xFF];
      seg.setAtIndex(ValueLayout.JAVA_INT, i, (a << 24) | (r << 16) | (g << 8) | b);
    }
  }

  // --- Gaussian sharpen ---

  /**
   * Applies unsharp mask sharpening with the given sigma and gain. Uses a 3x3 Gaussian blur
   * approximation for the mask, then: output = original + gain * (original - blurred)
   */
  public static void gaussianSharpen(NativePixelBuffer buffer, float sigma, float gain) {
    int w = buffer.width();
    int h = buffer.height();
    if (w < 3 || h < 3) return;

    MemorySegment src = buffer.segment();

    // Compute 3x3 Gaussian kernel weights from sigma
    float center = 1.0f;
    float edge = (float) Math.exp(-0.5 / (sigma * sigma));
    float corner = (float) Math.exp(-1.0 / (sigma * sigma));
    float sum = center + 4 * edge + 4 * corner;
    center /= sum;
    edge /= sum;
    corner /= sum;

    // Allocate temporary buffer for blurred result
    int count = w * h;
    int[] blurred = new int[count * 3]; // R, G, B channels separated

    // Apply 3x3 Gaussian blur (separated channels for precision)
    for (int y = 1; y < h - 1; y++) {
      for (int x = 1; x < w - 1; x++) {
        float rSum = 0, gSum = 0, bSum = 0;
        for (int dy = -1; dy <= 1; dy++) {
          for (int dx = -1; dx <= 1; dx++) {
            int argb = src.getAtIndex(ValueLayout.JAVA_INT, (long) (y + dy) * w + (x + dx));
            float weight = (dx == 0 && dy == 0) ? center : (dx == 0 || dy == 0) ? edge : corner;
            rSum += ((argb >>> 16) & 0xFF) * weight;
            gSum += ((argb >>> 8) & 0xFF) * weight;
            bSum += (argb & 0xFF) * weight;
          }
        }
        int idx = y * w + x;
        blurred[idx] = Math.round(rSum);
        blurred[idx + count] = Math.round(gSum);
        blurred[idx + count * 2] = Math.round(bSum);
      }
    }

    // Apply unsharp mask: output = original + gain * (original - blurred)
    for (int y = 1; y < h - 1; y++) {
      for (int x = 1; x < w - 1; x++) {
        int idx = y * w + x;
        int argb = src.getAtIndex(ValueLayout.JAVA_INT, idx);
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        r = Math.clamp(Math.round(r + gain * (r - blurred[idx])), 0, 255);
        g = Math.clamp(Math.round(g + gain * (g - blurred[idx + count])), 0, 255);
        b = Math.clamp(Math.round(b + gain * (b - blurred[idx + count * 2])), 0, 255);

        src.setAtIndex(ValueLayout.JAVA_INT, idx, (a << 24) | (r << 16) | (g << 8) | b);
      }
    }
  }

  /** Sharpens with default parameters (sigma=3.0, gain=1.0). */
  public static void gaussianSharpen(NativePixelBuffer buffer) {
    gaussianSharpen(buffer, 3.0f, 1.0f);
  }

  // --- Despeckle (median filter) ---

  /**
   * Applies a 3x3 median filter to reduce noise/speckle while preserving edges. Each pixel is
   * replaced by the median of its 3x3 neighborhood. Particularly effective for scanned comic pages
   * with dust/grain artifacts.
   */
  public static void despeckle(NativePixelBuffer buffer) {
    int w = buffer.width();
    int h = buffer.height();
    if (w < 3 || h < 3) return;

    MemorySegment src = buffer.segment();
    int count = w * h;

    // Allocate output buffer
    int[] output = new int[count];

    // Copy border pixels unchanged
    for (int x = 0; x < w; x++) {
      output[x] = src.getAtIndex(ValueLayout.JAVA_INT, x);
      output[(h - 1) * w + x] = src.getAtIndex(ValueLayout.JAVA_INT, (long) (h - 1) * w + x);
    }
    for (int y = 0; y < h; y++) {
      output[y * w] = src.getAtIndex(ValueLayout.JAVA_INT, (long) y * w);
      output[y * w + w - 1] = src.getAtIndex(ValueLayout.JAVA_INT, (long) y * w + w - 1);
    }

    int[] rArr = new int[9], gArr = new int[9], bArr = new int[9];

    for (int y = 1; y < h - 1; y++) {
      for (int x = 1; x < w - 1; x++) {
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
          for (int dx = -1; dx <= 1; dx++) {
            int argb = src.getAtIndex(ValueLayout.JAVA_INT, (long) (y + dy) * w + (x + dx));
            rArr[n] = (argb >>> 16) & 0xFF;
            gArr[n] = (argb >>> 8) & 0xFF;
            bArr[n] = argb & 0xFF;
            n++;
          }
        }

        int a = (src.getAtIndex(ValueLayout.JAVA_INT, (long) y * w + x) >>> 24) & 0xFF;
        int r = median9(rArr);
        int g = median9(gArr);
        int b = median9(bArr);
        output[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
      }
    }

    // Copy back to native memory
    MemorySegment.copy(output, 0, src, ValueLayout.JAVA_INT, 0, count);
  }

  // --- Quantize (color reduction) ---

  /**
   * Reduces the number of distinct color levels per channel. A levels value of 16 reduces each
   * 8-bit channel to 4-bit depth (posterize). Useful for reducing file size of comic pages with
   * limited color palettes.
   *
   * @param levels number of output levels per channel (2-256)
   */
  public static void quantize(NativePixelBuffer buffer, int levels) {
    if (levels < 2 || levels > 256) {
      throw new IllegalArgumentException("Levels must be between 2 and 256, got: " + levels);
    }

    MemorySegment seg = buffer.segment();
    int count = buffer.pixelCount();

    // Build LUT: map each 0-255 input to the nearest quantized level
    int[] lut = new int[256];
    float step = 255.0f / (levels - 1);
    for (int v = 0; v < 256; v++) {
      int quantized = Math.round(Math.round(v / step) * step);
      lut[v] = Math.clamp(quantized, 0, 255);
    }

    for (int i = 0; i < count; i++) {
      int argb = seg.getAtIndex(ValueLayout.JAVA_INT, i);
      int a = (argb >>> 24) & 0xFF;
      int r = lut[(argb >>> 16) & 0xFF];
      int g = lut[(argb >>> 8) & 0xFF];
      int b = lut[argb & 0xFF];
      seg.setAtIndex(ValueLayout.JAVA_INT, i, (a << 24) | (r << 16) | (g << 8) | b);
    }
  }

  // --- Border detection and removal ---

  /**
   * Detects uniform borders around the image. Returns insets [top, right, bottom, left] in pixels.
   * A border is considered uniform if all pixels in a row/column have the same color (within the
   * given tolerance).
   *
   * @param tolerance maximum per-channel difference to consider "same color" (0-255)
   */
  public static int[] detectBorders(NativePixelBuffer buffer, int tolerance) {
    int w = buffer.width();
    int h = buffer.height();
    MemorySegment seg = buffer.segment();

    // Get reference color from corner pixel
    int refColor = seg.getAtIndex(ValueLayout.JAVA_INT, 0);

    int top = 0, bottom = 0, left = 0, right = 0;

    // Detect top border
    for (int y = 0; y < h; y++) {
      if (!isUniformRow(seg, w, y, refColor, tolerance)) break;
      top++;
    }

    // Detect bottom border
    for (int y = h - 1; y >= top; y--) {
      if (!isUniformRow(seg, w, y, refColor, tolerance)) break;
      bottom++;
    }

    // Detect left border
    for (int x = 0; x < w; x++) {
      if (!isUniformColumn(seg, w, x, top, h - bottom, refColor, tolerance)) break;
      left++;
    }

    // Detect right border
    for (int x = w - 1; x >= left; x--) {
      if (!isUniformColumn(seg, w, x, top, h - bottom, refColor, tolerance)) break;
      right++;
    }

    return new int[] {top, right, bottom, left};
  }

  /**
   * Removes uniform borders, returning a new cropped buffer. If no significant borders are
   * detected, returns a copy of the original.
   *
   * @param tolerance maximum per-channel difference for border detection
   * @param minContentPercent minimum percentage of original dimensions the content must occupy
   *     (0.0-1.0)
   */
  public static NativePixelBuffer removeBorders(
      NativePixelBuffer buffer, int tolerance, float minContentPercent) {
    int[] borders = detectBorders(buffer, tolerance);
    int top = borders[0], right = borders[1], bottom = borders[2], left = borders[3];

    int contentW = buffer.width() - left - right;
    int contentH = buffer.height() - top - bottom;

    // Safety check: don't crop too aggressively
    if (contentW < buffer.width() * minContentPercent
        || contentH < buffer.height() * minContentPercent) {
      return buffer.subRegion(0, 0, buffer.width(), buffer.height());
    }

    if (contentW <= 0 || contentH <= 0) {
      return buffer.subRegion(0, 0, buffer.width(), buffer.height());
    }

    return buffer.subRegion(left, top, contentW, contentH);
  }

  // --- Resize ---

  /**
   * Resizes the image using bilinear interpolation. Returns a new buffer with the target
   * dimensions.
   */
  public static NativePixelBuffer resize(
      NativePixelBuffer buffer, int targetWidth, int targetHeight) {
    if (targetWidth <= 0 || targetHeight <= 0) {
      throw new IllegalArgumentException("Target dimensions must be positive");
    }

    int srcW = buffer.width();
    int srcH = buffer.height();
    MemorySegment src = buffer.segment();

    NativePixelBuffer dst = NativePixelBuffer.allocate(targetWidth, targetHeight);
    MemorySegment dstSeg = dst.segment();

    float xRatio = (float) srcW / targetWidth;
    float yRatio = (float) srcH / targetHeight;

    for (int y = 0; y < targetHeight; y++) {
      float srcY = y * yRatio;
      int y0 = Math.min((int) srcY, srcH - 1);
      int y1 = Math.min(y0 + 1, srcH - 1);
      float yFrac = srcY - y0;

      for (int x = 0; x < targetWidth; x++) {
        float srcX = x * xRatio;
        int x0 = Math.min((int) srcX, srcW - 1);
        int x1 = Math.min(x0 + 1, srcW - 1);
        float xFrac = srcX - x0;

        // Bilinear interpolation of 4 corners
        int c00 = src.getAtIndex(ValueLayout.JAVA_INT, (long) y0 * srcW + x0);
        int c10 = src.getAtIndex(ValueLayout.JAVA_INT, (long) y0 * srcW + x1);
        int c01 = src.getAtIndex(ValueLayout.JAVA_INT, (long) y1 * srcW + x0);
        int c11 = src.getAtIndex(ValueLayout.JAVA_INT, (long) y1 * srcW + x1);

        int pixel = bilinear(c00, c10, c01, c11, xFrac, yFrac);
        dstSeg.setAtIndex(ValueLayout.JAVA_INT, (long) y * targetWidth + x, pixel);
      }
    }

    return dst;
  }

  /** Resizes maintaining aspect ratio, fitting within the given max dimensions. */
  public static NativePixelBuffer resizeFit(NativePixelBuffer buffer, int maxWidth, int maxHeight) {
    int w = buffer.width();
    int h = buffer.height();

    if (w <= maxWidth && h <= maxHeight) {
      return buffer.subRegion(0, 0, w, h); // Copy, already fits
    }

    float scale = Math.min((float) maxWidth / w, (float) maxHeight / h);
    int newW = Math.max(1, Math.round(w * scale));
    int newH = Math.max(1, Math.round(h * scale));
    return resize(buffer, newW, newH);
  }

  // --- Internal helpers ---

  private static boolean isUniformRow(
      MemorySegment seg, int width, int y, int refColor, int tolerance) {
    for (int x = 0; x < width; x++) {
      int pixel = seg.getAtIndex(ValueLayout.JAVA_INT, (long) y * width + x);
      if (!colorsMatch(pixel, refColor, tolerance)) return false;
    }
    return true;
  }

  private static boolean isUniformColumn(
      MemorySegment seg, int width, int x, int startY, int endY, int refColor, int tolerance) {
    for (int y = startY; y < endY; y++) {
      int pixel = seg.getAtIndex(ValueLayout.JAVA_INT, (long) y * width + x);
      if (!colorsMatch(pixel, refColor, tolerance)) return false;
    }
    return true;
  }

  private static boolean colorsMatch(int c1, int c2, int tolerance) {
    int dr = Math.abs(((c1 >>> 16) & 0xFF) - ((c2 >>> 16) & 0xFF));
    int dg = Math.abs(((c1 >>> 8) & 0xFF) - ((c2 >>> 8) & 0xFF));
    int db = Math.abs((c1 & 0xFF) - (c2 & 0xFF));
    return dr <= tolerance && dg <= tolerance && db <= tolerance;
  }

  /** Fast median of exactly 9 values using a sorting network. */
  private static int median9(int[] a) {
    // Partial sort: only need the 5th element (median)
    // Use a minimal comparison network
    sort2(a, 0, 1);
    sort2(a, 3, 4);
    sort2(a, 6, 7);
    sort2(a, 1, 2);
    sort2(a, 4, 5);
    sort2(a, 7, 8);
    sort2(a, 0, 1);
    sort2(a, 3, 4);
    sort2(a, 6, 7);
    sort2(a, 0, 3);
    sort2(a, 3, 6);
    sort2(a, 0, 3);
    sort2(a, 1, 4);
    sort2(a, 4, 7);
    sort2(a, 1, 4);
    sort2(a, 2, 5);
    sort2(a, 5, 8);
    sort2(a, 2, 5);
    sort2(a, 1, 3);
    sort2(a, 5, 7);
    sort2(a, 2, 6);
    sort2(a, 4, 6);
    sort2(a, 2, 4);
    sort2(a, 2, 3);
    sort2(a, 5, 6);
    sort2(a, 3, 4);
    sort2(a, 4, 5);
    return a[4];
  }

  private static void sort2(int[] a, int i, int j) {
    if (a[i] > a[j]) {
      int tmp = a[i];
      a[i] = a[j];
      a[j] = tmp;
    }
  }

  private static int bilinear(int c00, int c10, int c01, int c11, float xf, float yf) {
    float w00 = (1 - xf) * (1 - yf);
    float w10 = xf * (1 - yf);
    float w01 = (1 - xf) * yf;
    float w11 = xf * yf;

    int a =
        Math.clamp(
            Math.round(
                ((c00 >>> 24) & 0xFF) * w00
                    + ((c10 >>> 24) & 0xFF) * w10
                    + ((c01 >>> 24) & 0xFF) * w01
                    + ((c11 >>> 24) & 0xFF) * w11),
            0,
            255);
    int r =
        Math.clamp(
            Math.round(
                ((c00 >>> 16) & 0xFF) * w00
                    + ((c10 >>> 16) & 0xFF) * w10
                    + ((c01 >>> 16) & 0xFF) * w01
                    + ((c11 >>> 16) & 0xFF) * w11),
            0,
            255);
    int g =
        Math.clamp(
            Math.round(
                ((c00 >>> 8) & 0xFF) * w00
                    + ((c10 >>> 8) & 0xFF) * w10
                    + ((c01 >>> 8) & 0xFF) * w01
                    + ((c11 >>> 8) & 0xFF) * w11),
            0,
            255);
    int b =
        Math.clamp(
            Math.round(
                (c00 & 0xFF) * w00 + (c10 & 0xFF) * w10 + (c01 & 0xFF) * w01 + (c11 & 0xFF) * w11),
            0,
            255);

    return (a << 24) | (r << 16) | (g << 8) | b;
  }
}
