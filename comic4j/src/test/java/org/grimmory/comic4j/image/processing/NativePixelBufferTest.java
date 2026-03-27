package org.grimmory.comic4j.image.processing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class NativePixelBufferTest {

  @Test
  void fromImageAndBack() {
    BufferedImage src = createSolidImage(100, 80, Color.RED);
    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(src)) {
      assertEquals(100, buf.width());
      assertEquals(80, buf.height());
      assertEquals(8000, buf.pixelCount());

      BufferedImage result = buf.toImage();
      assertEquals(100, result.getWidth());
      assertEquals(80, result.getHeight());
      // Check center pixel is red (ARGB)
      int pixel = result.getRGB(50, 40);
      assertEquals(0xFF, (pixel >>> 16) & 0xFF, "Red channel");
      assertEquals(0x00, (pixel >>> 8) & 0xFF, "Green channel");
      assertEquals(0x00, pixel & 0xFF, "Blue channel");
    }
  }

  @Test
  void allocateCreatesEmptyBuffer() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(50, 30)) {
      assertEquals(50, buf.width());
      assertEquals(30, buf.height());
      // All pixels should be 0 (transparent black)
      assertEquals(0, buf.getPixel(0, 0));
      assertEquals(0, buf.getPixel(25, 15));
    }
  }

  @Test
  void getSetPixel() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(10, 10)) {
      int color = 0xFFAABBCC;
      buf.setPixel(5, 3, color);
      assertEquals(color, buf.getPixel(5, 3));
    }
  }

  @Test
  void subRegion() {
    BufferedImage src = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = src.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 50, 100);
    g.setColor(Color.BLUE);
    g.fillRect(50, 0, 50, 100);
    g.dispose();

    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(src)) {
      try (NativePixelBuffer left = buf.subRegion(0, 0, 50, 100)) {
        assertEquals(50, left.width());
        assertEquals(100, left.height());
        // Should be red
        int pixel = left.getPixel(25, 50);
        assertEquals(0xFF, (pixel >>> 16) & 0xFF);
      }

      try (NativePixelBuffer right = buf.subRegion(50, 0, 50, 100)) {
        assertEquals(50, right.width());
        // Should be blue
        int pixel = right.getPixel(25, 50);
        assertEquals(0xFF, pixel & 0xFF);
      }
    }
  }

  @Test
  void subRegionOutOfBoundsThrows() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(100, 100)) {
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            try (NativePixelBuffer ignored = buf.subRegion(50, 50, 60, 60)) {
              fail("Expected subRegion to throw for out-of-bounds request");
            }
          });
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            try (NativePixelBuffer ignored = buf.subRegion(-1, 0, 10, 10)) {
              fail("Expected subRegion to throw for negative coordinates");
            }
          });
    }
  }

  @Test
  void handlesNonArgbImage() {
    // TYPE_INT_RGB (no alpha)
    BufferedImage rgb = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = rgb.createGraphics();
    g.setColor(Color.GREEN);
    g.fillRect(0, 0, 10, 10);
    g.dispose();

    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(rgb)) {
      BufferedImage result = buf.toImage();
      int pixel = result.getRGB(5, 5);
      assertEquals(0xFF, (pixel >>> 24) & 0xFF, "Alpha should be opaque");
      assertEquals(0x00, (pixel >>> 16) & 0xFF, "Red");
      assertTrue(((pixel >>> 8) & 0xFF) > 0xF0, "Green should be high");
    }
  }

  @Test
  void handles3ByteBgrImage() {
    BufferedImage bgr = new BufferedImage(10, 10, BufferedImage.TYPE_3BYTE_BGR);
    Graphics2D g = bgr.createGraphics();
    g.setColor(Color.YELLOW);
    g.fillRect(0, 0, 10, 10);
    g.dispose();

    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(bgr)) {
      assertEquals(10, buf.width());
      BufferedImage result = buf.toImage();
      int pixel = result.getRGB(5, 5);
      assertTrue(((pixel >>> 16) & 0xFF) > 0xF0, "Red");
      assertTrue(((pixel >>> 8) & 0xFF) > 0xF0, "Green");
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
