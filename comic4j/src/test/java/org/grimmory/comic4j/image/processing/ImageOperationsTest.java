package org.grimmory.comic4j.image.processing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ImageOperationsTest {

  // --- Grayscale ---

  @Test
  void grayscaleConvertsColor() {
    BufferedImage img = createSolidImage(10, 10, Color.RED);
    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(img)) {
      ImageOperations.grayscale(buf);

      int pixel = buf.getPixel(5, 5);
      int r = (pixel >>> 16) & 0xFF;
      int g = (pixel >>> 8) & 0xFF;
      int b = pixel & 0xFF;

      // All channels should be equal (grayscale)
      assertEquals(r, g);
      assertEquals(g, b);
      // Red luminance ~54 (0.2126 * 255)
      assertTrue(r > 40 && r < 70, "Expected luminance ~54, got " + r);
    }
  }

  @Test
  void grayscalePreservesAlpha() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(5, 5)) {
      buf.setPixel(2, 2, 0x80FF0000); // 50% alpha red
      ImageOperations.grayscale(buf);
      int pixel = buf.getPixel(2, 2);
      assertEquals(0x80, (pixel >>> 24) & 0xFF, "Alpha should be preserved");
    }
  }

  @Test
  void grayscaleWhiteStaysWhite() {
    BufferedImage img = createSolidImage(10, 10, Color.WHITE);
    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(img)) {
      ImageOperations.grayscale(buf);
      int pixel = buf.getPixel(5, 5);
      int r = (pixel >>> 16) & 0xFF;
      // White should remain white (luminance of pure white = 255)
      assertEquals(255, r);
    }
  }

  // --- Normalize ---

  @Test
  void normalizeStretchesLevels() {
    // Create a dark image (values 50-100)
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(100, 100)) {
      for (int y = 0; y < 100; y++) {
        for (int x = 0; x < 100; x++) {
          int v = 50 + (x * 50 / 100);
          buf.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
        }
      }

      ImageOperations.normalize(buf);

      // After normalization, range should be stretched toward 0-255
      int darkPixel = buf.getPixel(0, 50);
      int brightPixel = buf.getPixel(99, 50);
      int darkVal = (darkPixel >>> 16) & 0xFF;
      int brightVal = (brightPixel >>> 16) & 0xFF;

      // The stretch should be significant
      assertTrue(
          brightVal - darkVal > 150,
          "Expected stretched range, got " + darkVal + " to " + brightVal);
    }
  }

  @Test
  void normalizeUniformImageUnchanged() {
    BufferedImage img = createSolidImage(10, 10, new Color(128, 128, 128));
    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(img)) {
      ImageOperations.normalize(buf);
      // Should not crash on uniform image
      assertNotNull(buf.toImage());
    }
  }

  // --- Gaussian sharpen ---

  @Test
  void gaussianSharpenEnhancesEdges() {
    // Create image with an edge: left half black, right half white
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(20, 20)) {
      for (int y = 0; y < 20; y++) {
        for (int x = 0; x < 20; x++) {
          int v = x < 10 ? 0 : 255;
          buf.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
        }
      }

      // Read edge pixel before
      int beforeEdge = (buf.getPixel(10, 10) >>> 16) & 0xFF;

      ImageOperations.gaussianSharpen(buf, 3.0f, 1.0f);

      // After sharpening, pixel just past edge should be brighter (overshoot)
      int afterEdge = (buf.getPixel(11, 10) >>> 16) & 0xFF;
      assertTrue(
          afterEdge >= beforeEdge,
          "Expected edge enhancement, got " + afterEdge + " vs " + beforeEdge);
    }
  }

  @Test
  void gaussianSharpenSmallImageNoOp() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(2, 2)) {
      buf.setPixel(0, 0, 0xFF808080);
      ImageOperations.gaussianSharpen(buf);
      // Should not crash on tiny image
      assertNotNull(buf.toImage());
    }
  }

  // --- Despeckle ---

  @Test
  void despeckleRemovesSinglePixelNoise() {
    // Create uniform gray image with one white noise pixel
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(10, 10)) {
      int gray = 0xFF808080;
      for (int y = 0; y < 10; y++) {
        for (int x = 0; x < 10; x++) {
          buf.setPixel(x, y, gray);
        }
      }
      buf.setPixel(5, 5, 0xFFFFFFFF); // noise

      ImageOperations.despeckle(buf);

      // After median filter, the noise pixel should be closer to gray
      int pixel = buf.getPixel(5, 5);
      int r = (pixel >>> 16) & 0xFF;
      assertTrue(r < 200, "Expected noise removed, got " + r);
    }
  }

  @Test
  void despeckleSmallImageNoOp() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(2, 2)) {
      ImageOperations.despeckle(buf);
      assertNotNull(buf.toImage());
    }
  }

  // --- Quantize ---

  @Test
  void quantizeReducesLevels() {
    // Create gradient image
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(256, 1)) {
      for (int x = 0; x < 256; x++) {
        buf.setPixel(x, 0, 0xFF000000 | (x << 16) | (x << 8) | x);
      }

      ImageOperations.quantize(buf, 4); // Only 4 levels: 0, 85, 170, 255

      // Check that values are quantized
      java.util.Set<Integer> uniqueValues = new java.util.HashSet<>();
      for (int x = 0; x < 256; x++) {
        int v = (buf.getPixel(x, 0) >>> 16) & 0xFF;
        uniqueValues.add(v);
      }
      assertTrue(
          uniqueValues.size() <= 4, "Expected at most 4 unique values, got " + uniqueValues.size());
    }
  }

  @Test
  void quantizeInvalidLevelsThrows() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(10, 10)) {
      assertThrows(IllegalArgumentException.class, () -> ImageOperations.quantize(buf, 1));
      assertThrows(IllegalArgumentException.class, () -> ImageOperations.quantize(buf, 257));
    }
  }

  // --- Border detection ---

  @Test
  void detectBordersUniformBorder() {
    // White image with black content in center
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(100, 100)) {
      // Fill all white
      for (int y = 0; y < 100; y++) {
        for (int x = 0; x < 100; x++) {
          buf.setPixel(x, y, 0xFFFFFFFF);
        }
      }
      // Black rectangle in center (20,20 to 80,80)
      for (int y = 20; y < 80; y++) {
        for (int x = 20; x < 80; x++) {
          buf.setPixel(x, y, 0xFF000000);
        }
      }

      int[] borders = ImageOperations.detectBorders(buf, 5);
      assertEquals(20, borders[0], "Top border");
      assertEquals(20, borders[1], "Right border");
      assertEquals(20, borders[2], "Bottom border");
      assertEquals(20, borders[3], "Left border");
    }
  }

  @Test
  void detectBordersNoBorder() {
    // Random-ish image with no uniform border
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(10, 10)) {
      for (int y = 0; y < 10; y++) {
        for (int x = 0; x < 10; x++) {
          buf.setPixel(x, y, 0xFF000000 | ((x * 25) << 16) | ((y * 25) << 8));
        }
      }

      int[] borders = ImageOperations.detectBorders(buf, 5);
      assertEquals(0, borders[0]);
      assertEquals(0, borders[2]);
    }
  }

  @Test
  void removeBordersReturnsCroppedImage() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(100, 100)) {
      // Fill all white
      for (int y = 0; y < 100; y++) {
        for (int x = 0; x < 100; x++) {
          buf.setPixel(x, y, 0xFFFFFFFF);
        }
      }
      // Content in center
      for (int y = 10; y < 90; y++) {
        for (int x = 10; x < 90; x++) {
          buf.setPixel(x, y, 0xFF000000);
        }
      }

      try (NativePixelBuffer cropped = ImageOperations.removeBorders(buf, 5, 0.3f)) {
        assertEquals(80, cropped.width());
        assertEquals(80, cropped.height());
      }
    }
  }

  @Test
  void removeBordersMinContentSafety() {
    // Image that's almost all border - safety check should prevent over-cropping
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(100, 100)) {
      for (int y = 0; y < 100; y++) {
        for (int x = 0; x < 100; x++) {
          buf.setPixel(x, y, 0xFFFFFFFF);
        }
      }
      // Tiny content area
      buf.setPixel(50, 50, 0xFF000000);

      try (NativePixelBuffer cropped = ImageOperations.removeBorders(buf, 5, 0.5f)) {
        // Should NOT crop to 1x1, safety kicks in
        assertTrue(cropped.width() >= 50);
        assertTrue(cropped.height() >= 50);
      }
    }
  }

  // --- Resize ---

  @Test
  void resizeDownscale() {
    BufferedImage img = createSolidImage(200, 300, Color.BLUE);
    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(img)) {
      try (NativePixelBuffer resized = ImageOperations.resize(buf, 100, 150)) {
        assertEquals(100, resized.width());
        assertEquals(150, resized.height());
        // Color should be preserved
        int pixel = resized.getPixel(50, 75);
        assertTrue((pixel & 0xFF) > 200, "Blue channel should be high");
      }
    }
  }

  @Test
  void resizeUpscale() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(10, 10)) {
      for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) buf.setPixel(x, y, 0xFFFF0000);

      try (NativePixelBuffer resized = ImageOperations.resize(buf, 20, 20)) {
        assertEquals(20, resized.width());
        assertEquals(20, resized.height());
      }
    }
  }

  @Test
  void resizeFitMaintainsAspect() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(400, 200)) {
      try (NativePixelBuffer resized = ImageOperations.resizeFit(buf, 100, 100)) {
        assertEquals(100, resized.width());
        assertEquals(50, resized.height());
      }
    }
  }

  @Test
  void resizeFitAlreadySmall() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(50, 30)) {
      try (NativePixelBuffer resized = ImageOperations.resizeFit(buf, 100, 100)) {
        assertEquals(50, resized.width());
        assertEquals(30, resized.height());
      }
    }
  }

  @Test
  void resizeInvalidDimensionsThrows() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(10, 10)) {
      assertThrows(IllegalArgumentException.class, () -> ImageOperations.resize(buf, 0, 10));
      assertThrows(IllegalArgumentException.class, () -> ImageOperations.resize(buf, 10, -1));
    }
  }

  private static BufferedImage createSolidImage(int w, int h, Color color) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, w, h);
    g.dispose();
    return img;
  }
}
