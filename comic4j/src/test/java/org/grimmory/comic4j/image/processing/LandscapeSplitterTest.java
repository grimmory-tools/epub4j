package org.grimmory.comic4j.image.processing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import org.grimmory.comic4j.domain.ReadingDirection;
import org.junit.jupiter.api.Test;

class LandscapeSplitterTest {

  @Test
  void isLandscape() {
    try (NativePixelBuffer wide = NativePixelBuffer.allocate(200, 100);
        NativePixelBuffer tall = NativePixelBuffer.allocate(100, 200);
        NativePixelBuffer square = NativePixelBuffer.allocate(100, 100)) {

      assertTrue(LandscapeSplitter.isLandscape(wide));
      assertFalse(LandscapeSplitter.isLandscape(tall));
      assertFalse(LandscapeSplitter.isLandscape(square));
    }
  }

  @Test
  void splitLandscapeLeftToRight() {
    // Left half red, right half blue
    BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 100, 100);
    g.setColor(Color.BLUE);
    g.fillRect(100, 0, 100, 100);
    g.dispose();

    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(img)) {
      List<NativePixelBuffer> pages = LandscapeSplitter.split(buf, ReadingDirection.LEFT_TO_RIGHT);

      assertEquals(2, pages.size());

      // First page should be left half (red)
      int pixel0 = pages.get(0).getPixel(50, 50);
      assertTrue(((pixel0 >>> 16) & 0xFF) > 200, "First page should be red");

      // Second page should be right half (blue)
      int pixel1 = pages.get(1).getPixel(50, 50);
      assertTrue((pixel1 & 0xFF) > 200, "Second page should be blue");

      assertEquals(100, pages.get(0).width());
      assertEquals(100, pages.get(1).width());

      pages.forEach(NativePixelBuffer::close);
    }
  }

  @Test
  void splitLandscapeMangaReverseOrder() {
    BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 100, 100);
    g.setColor(Color.BLUE);
    g.fillRect(100, 0, 100, 100);
    g.dispose();

    try (NativePixelBuffer buf = NativePixelBuffer.fromImage(img)) {
      List<NativePixelBuffer> pages =
          LandscapeSplitter.split(buf, ReadingDirection.RIGHT_TO_LEFT_MANGA);

      assertEquals(2, pages.size());

      // Manga: first page should be right half (blue)
      int pixel0 = pages.get(0).getPixel(50, 50);
      assertTrue((pixel0 & 0xFF) > 200, "Manga first page should be blue (right half)");

      // Second page should be left half (red)
      int pixel1 = pages.get(1).getPixel(50, 50);
      assertTrue(((pixel1 >>> 16) & 0xFF) > 200, "Manga second page should be red (left half)");

      pages.forEach(NativePixelBuffer::close);
    }
  }

  @Test
  void splitPortraitReturnsOnePage() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(100, 200)) {
      List<NativePixelBuffer> pages = LandscapeSplitter.split(buf, ReadingDirection.LEFT_TO_RIGHT);

      assertEquals(1, pages.size());
      assertEquals(100, pages.getFirst().width());
      assertEquals(200, pages.getFirst().height());

      pages.forEach(NativePixelBuffer::close);
    }
  }

  @Test
  void splitOddWidth() {
    // 201 pixels wide: left=100, right=101
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(201, 100)) {
      List<NativePixelBuffer> pages = LandscapeSplitter.split(buf, ReadingDirection.LEFT_TO_RIGHT);

      assertEquals(2, pages.size());
      assertEquals(100, pages.get(0).width());
      assertEquals(101, pages.get(1).width());

      pages.forEach(NativePixelBuffer::close);
    }
  }

  @Test
  void splitRightToLeftAlsoReversed() {
    try (NativePixelBuffer buf = NativePixelBuffer.allocate(200, 100)) {
      List<NativePixelBuffer> ltr = LandscapeSplitter.split(buf, ReadingDirection.LEFT_TO_RIGHT);
      List<NativePixelBuffer> rtl = LandscapeSplitter.split(buf, ReadingDirection.RIGHT_TO_LEFT);

      assertEquals(2, ltr.size());
      assertEquals(2, rtl.size());

      ltr.forEach(NativePixelBuffer::close);
      rtl.forEach(NativePixelBuffer::close);
    }
  }
}
