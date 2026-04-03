package org.grimmory.comic4j.image;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.grimmory.comic4j.TestArchiveHelper;
import org.grimmory.comic4j.error.ComicException;
import org.junit.jupiter.api.Test;

class ImageCodecTest {

  @Test
  void transcodeToJpeg_jpegInput_passthrough() throws IOException {
    byte[] jpeg = TestArchiveHelper.createTestJpeg(100, 100, Color.BLUE);
    byte[] result = ImageCodec.transcodeToJpeg(jpeg, 0.85f, 20_000_000L);
    // Should return same bytes (passthrough)
    assertSame(jpeg, result);
  }

  @Test
  void transcodeToJpeg_pngInput_convertsToJpeg() throws IOException {
    byte[] png = TestArchiveHelper.createTestPng(100, 100, Color.RED);
    byte[] result = ImageCodec.transcodeToJpeg(png, 0.85f, 20_000_000L);
    assertNotNull(result);
    assertTrue(result.length > 0);
    // Verify output starts with JPEG SOI marker
    assertEquals((byte) 0xFF, result[0]);
    assertEquals((byte) 0xD8, result[1]);
    // Should not be the same byte array
    assertNotSame(png, result);
  }

  @Test
  void transcodeToJpeg_stream_works() throws IOException {
    byte[] png = TestArchiveHelper.createTestPng(50, 50, Color.GREEN);
    byte[] result = ImageCodec.transcodeToJpeg(new ByteArrayInputStream(png), 0.85f, 20_000_000L);
    assertNotNull(result);
    assertEquals((byte) 0xFF, result[0]);
    assertEquals((byte) 0xD8, result[1]);
  }

  @Test
  void transcodeToJpeg_decompressionBomb_throws() throws IOException {
    byte[] png = TestArchiveHelper.createTestPng(200, 200, Color.RED);
    // Set a very low pixel limit
    assertThrows(ComicException.class, () -> ImageCodec.transcodeToJpeg(png, 0.85f, 100L));
  }

  @Test
  void transcodeToJpeg_nullInput_throws() {
    assertThrows(
        ComicException.class, () -> ImageCodec.transcodeToJpeg((byte[]) null, 0.85f, 20_000_000L));
  }

  @Test
  void transcodeToJpeg_emptyInput_throws() {
    assertThrows(
        ComicException.class, () -> ImageCodec.transcodeToJpeg(new byte[0], 0.85f, 20_000_000L));
  }

  @Test
  void transcodeToJpeg_invalidQuality_throws() throws IOException {
    byte[] png = TestArchiveHelper.createTestPng(10, 10, Color.RED);
    assertThrows(
        IllegalArgumentException.class, () -> ImageCodec.transcodeToJpeg(png, 1.5f, 20_000_000L));
    assertThrows(
        IllegalArgumentException.class, () -> ImageCodec.transcodeToJpeg(png, -0.1f, 20_000_000L));
  }

  @Test
  void isJpeg_variousExtensions() {
    assertTrue(ImageCodec.isJpeg("image.jpg"));
    assertTrue(ImageCodec.isJpeg("IMAGE.JPG"));
    assertTrue(ImageCodec.isJpeg("photo.jpeg"));
    assertFalse(ImageCodec.isJpeg("image.png"));
    assertFalse(ImageCodec.isJpeg("image.webp"));
    assertFalse(ImageCodec.isJpeg(null));
  }

  @Test
  void needsTranscode_jpeg_false() {
    assertFalse(ImageCodec.needsTranscode(org.grimmory.comic4j.domain.ImageFormat.JPEG));
  }

  @Test
  void needsTranscode_otherFormats_true() {
    assertTrue(ImageCodec.needsTranscode(org.grimmory.comic4j.domain.ImageFormat.PNG));
    assertTrue(ImageCodec.needsTranscode(org.grimmory.comic4j.domain.ImageFormat.WEBP));
    assertTrue(ImageCodec.needsTranscode(org.grimmory.comic4j.domain.ImageFormat.GIF));
    assertTrue(ImageCodec.needsTranscode(org.grimmory.comic4j.domain.ImageFormat.BMP));
  }
}
