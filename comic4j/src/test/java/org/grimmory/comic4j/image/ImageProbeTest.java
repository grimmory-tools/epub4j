package org.grimmory.comic4j.image;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageProbeTest {

  @Test
  void readDimensions_jpeg() throws IOException {
    byte[] jpeg = createJpeg(320, 240);
    ImageDimensions dims = ImageProbe.readDimensions(jpeg);
    assertNotNull(dims);
    assertEquals(320, dims.width());
    assertEquals(240, dims.height());
    assertTrue(dims.wide());
  }

  @Test
  void readDimensions_png() throws IOException {
    byte[] png = createPng(200, 400);
    ImageDimensions dims = ImageProbe.readDimensions(png);
    assertNotNull(dims);
    assertEquals(200, dims.width());
    assertEquals(400, dims.height());
    assertFalse(dims.wide());
  }

  @Test
  void readDimensions_gif() throws IOException {
    byte[] gif = createGif(150, 150);
    ImageDimensions dims = ImageProbe.readDimensions(gif);
    assertNotNull(dims);
    assertEquals(150, dims.width());
    assertEquals(150, dims.height());
    assertFalse(dims.wide()); // equal dimensions → not wide
  }

  @Test
  void readDimensions_bmp() throws IOException {
    byte[] bmp = createBmp(800, 600);
    ImageDimensions dims = ImageProbe.readDimensions(bmp);
    assertNotNull(dims);
    assertEquals(800, dims.width());
    assertEquals(600, dims.height());
    assertTrue(dims.wide());
  }

  @Test
  void readDimensions_nullInput_returnsNull() {
    assertNull(ImageProbe.readDimensions((byte[]) null));
  }

  @Test
  void readDimensions_emptyInput_returnsNull() {
    assertNull(ImageProbe.readDimensions(new byte[0]));
  }

  @Test
  void readDimensions_tooShort_returnsNull() {
    assertNull(ImageProbe.readDimensions(new byte[] {1, 2, 3}));
  }

  @Test
  void readDimensions_randomBytes_returnsNull() {
    byte[] random = new byte[1024];
    for (int i = 0; i < random.length; i++) random[i] = (byte) (i * 7);
    assertNull(ImageProbe.readDimensions(random));
  }

  @Test
  void readDimensions_squareImage() throws IOException {
    byte[] jpeg = createJpeg(500, 500);
    ImageDimensions dims = ImageProbe.readDimensions(jpeg);
    assertNotNull(dims);
    assertEquals(500, dims.width());
    assertEquals(500, dims.height());
    assertFalse(dims.wide());
  }

  @Test
  void readDimensions_tallImage() throws IOException {
    byte[] png = createPng(100, 1000);
    ImageDimensions dims = ImageProbe.readDimensions(png);
    assertNotNull(dims);
    assertEquals(100, dims.width());
    assertEquals(1000, dims.height());
    assertFalse(dims.wide());
  }

  @Test
  void pixelCount() {
    ImageDimensions dims = ImageDimensions.of(1920, 1080);
    assertEquals(1920L * 1080L, dims.pixelCount());
  }

  @Test
  void imageDimensions_negativeDimensions_throws() {
    assertThrows(IllegalArgumentException.class, () -> ImageDimensions.of(-1, 100));
    assertThrows(IllegalArgumentException.class, () -> ImageDimensions.of(100, -1));
  }

  // --- Helpers ---

  private static byte[] createJpeg(int w, int h) throws IOException {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.BLUE);
    g.fillRect(0, 0, w, h);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", baos);
    return baos.toByteArray();
  }

  private static byte[] createPng(int w, int h) throws IOException {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, w, h);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "png", baos);
    return baos.toByteArray();
  }

  private static byte[] createGif(int w, int h) throws IOException {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.GREEN);
    g.fillRect(0, 0, w, h);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "gif", baos);
    return baos.toByteArray();
  }

  private static byte[] createBmp(int w, int h) throws IOException {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.YELLOW);
    g.fillRect(0, 0, w, h);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "bmp", baos);
    return baos.toByteArray();
  }
}
