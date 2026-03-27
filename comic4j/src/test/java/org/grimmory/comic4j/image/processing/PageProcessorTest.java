package org.grimmory.comic4j.image.processing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import org.grimmory.comic4j.domain.ReadingDirection;
import org.junit.jupiter.api.Test;

class PageProcessorTest {

  @Test
  void defaultOptionsNoOp() {
    PageProcessor processor = new PageProcessor(ComicProcessingOptions.defaults());
    BufferedImage input = createSolidImage(100, 150, Color.RED);

    List<BufferedImage> results = processor.process(input);
    assertEquals(1, results.size());
    assertEquals(100, results.getFirst().getWidth());
    assertEquals(150, results.getFirst().getHeight());
  }

  @Test
  void processSingleReturnsOneImage() {
    PageProcessor processor =
        new PageProcessor(ComicProcessingOptions.builder().grayscale(true).build());

    BufferedImage input = createSolidImage(50, 50, Color.RED);
    BufferedImage result = processor.processSingle(input);

    assertNotNull(result);
    assertEquals(50, result.getWidth());
    // Should be grayscale
    int pixel = result.getRGB(25, 25);
    int r = (pixel >>> 16) & 0xFF;
    int g = (pixel >>> 8) & 0xFF;
    int b = pixel & 0xFF;
    assertEquals(r, g);
    assertEquals(g, b);
  }

  @Test
  void landscapeSplitProducesTwoPages() {
    PageProcessor processor =
        new PageProcessor(
            ComicProcessingOptions.builder()
                .splitLandscape(true)
                .readingDirection(ReadingDirection.LEFT_TO_RIGHT)
                .build());

    BufferedImage input = createSolidImage(200, 100, Color.BLUE);
    List<BufferedImage> results = processor.process(input);

    assertEquals(2, results.size());
    assertEquals(100, results.get(0).getWidth());
    assertEquals(100, results.get(0).getHeight());
    assertEquals(100, results.get(1).getWidth());
  }

  @Test
  void landscapeSplitPortraitUnchanged() {
    PageProcessor processor =
        new PageProcessor(ComicProcessingOptions.builder().splitLandscape(true).build());

    BufferedImage input = createSolidImage(100, 200, Color.GREEN);
    List<BufferedImage> results = processor.process(input);

    assertEquals(1, results.size());
    assertEquals(100, results.getFirst().getWidth());
    assertEquals(200, results.getFirst().getHeight());
  }

  @Test
  void fullPipeline() {
    PageProcessor processor =
        new PageProcessor(
            ComicProcessingOptions.builder()
                .normalize(true)
                .sharpen(true)
                .sharpenSigma(2.0f)
                .sharpenGain(0.5f)
                .despeckle(true)
                .grayscale(true)
                .quantize(true)
                .quantizeLevels(8)
                .resize(true)
                .maxWidth(50)
                .maxHeight(75)
                .keepAspectRatio(true)
                .build());

    BufferedImage input = createGradientImage(200, 300);
    List<BufferedImage> results = processor.process(input);

    assertEquals(1, results.size());
    BufferedImage result = results.getFirst();
    // Should be resized
    assertEquals(50, result.getWidth());
    assertEquals(75, result.getHeight());

    // Should be grayscale
    int pixel = result.getRGB(25, 37);
    int r = (pixel >>> 16) & 0xFF;
    int g = (pixel >>> 8) & 0xFF;
    int b = pixel & 0xFF;
    assertEquals(r, g);
    assertEquals(g, b);
  }

  @Test
  void resizeWithoutAspectRatio() {
    PageProcessor processor =
        new PageProcessor(
            ComicProcessingOptions.builder()
                .resize(true)
                .maxWidth(100)
                .maxHeight(100)
                .keepAspectRatio(false)
                .build());

    BufferedImage input = createSolidImage(200, 400, Color.CYAN);
    List<BufferedImage> results = processor.process(input);

    assertEquals(1, results.size());
    assertEquals(100, results.getFirst().getWidth());
    assertEquals(100, results.getFirst().getHeight());
  }

  @Test
  void borderRemovalInPipeline() {
    PageProcessor processor =
        new PageProcessor(
            ComicProcessingOptions.builder()
                .removeBorders(true)
                .borderTolerance(10)
                .borderMinContentPercent(0.3f)
                .build());

    // White border around black content
    BufferedImage input = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = input.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 100, 100);
    g.setColor(Color.BLACK);
    g.fillRect(10, 10, 80, 80);
    g.dispose();

    List<BufferedImage> results = processor.process(input);
    assertEquals(1, results.size());
    // Should be cropped
    assertTrue(results.getFirst().getWidth() < 100);
    assertTrue(results.getFirst().getHeight() < 100);
  }

  @Test
  void mangaSplitOrder() {
    PageProcessor processor =
        new PageProcessor(
            ComicProcessingOptions.builder()
                .splitLandscape(true)
                .readingDirection(ReadingDirection.RIGHT_TO_LEFT_MANGA)
                .build());

    // Left=red, right=blue
    BufferedImage input = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = input.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 100, 100);
    g.setColor(Color.BLUE);
    g.fillRect(100, 0, 100, 100);
    g.dispose();

    List<BufferedImage> results = processor.process(input);
    assertEquals(2, results.size());

    // First page should be blue (right half) for manga
    int pixel0 = results.getFirst().getRGB(50, 50);
    assertTrue((pixel0 & 0xFF) > 200, "First manga page should be blue");
  }

  private static BufferedImage createSolidImage(int w, int h, Color color) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, w, h);
    g.dispose();
    return img;
  }

  private static BufferedImage createGradientImage(int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setPaint(new GradientPaint(0, 0, Color.DARK_GRAY, w, h, Color.LIGHT_GRAY));
    g.fillRect(0, 0, w, h);
    g.dispose();
    return img;
  }
}
