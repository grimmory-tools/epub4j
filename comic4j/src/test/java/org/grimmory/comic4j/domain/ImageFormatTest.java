package org.grimmory.comic4j.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImageFormatTest {

  @ParameterizedTest
  @CsvSource({
    "image.jpg, JPEG",
    "image.jpeg, JPEG",
    "image.JPG, JPEG",
    "image.png, PNG",
    "image.PNG, PNG",
    "image.webp, WEBP",
    "image.avif, AVIF",
    "image.heic, HEIC",
    "image.heif, HEIC",
    "image.gif, GIF",
    "image.bmp, BMP"
  })
  void fromFileName(String fileName, ImageFormat expected) {
    assertEquals(expected, ImageFormat.fromFileName(fileName));
  }

  @Test
  void fromFileNameNullAndEmpty() {
    assertNull(ImageFormat.fromFileName(null));
    assertNull(ImageFormat.fromFileName(""));
    assertNull(ImageFormat.fromFileName("   "));
  }

  @Test
  void fromFileNameNoExtension() {
    assertNull(ImageFormat.fromFileName("image"));
    assertNull(ImageFormat.fromFileName("image."));
  }

  @Test
  void fromFileNameUnsupported() {
    assertNull(ImageFormat.fromFileName("document.pdf"));
    assertNull(ImageFormat.fromFileName("archive.cbz"));
  }

  @Test
  void isImageFileName() {
    assertTrue(ImageFormat.isImageFileName("page01.jpg"));
    assertTrue(ImageFormat.isImageFileName("cover.PNG"));
    assertFalse(ImageFormat.isImageFileName("ComicInfo.xml"));
    assertFalse(ImageFormat.isImageFileName(null));
  }

  @Test
  void fromFileNameWithPath() {
    assertEquals(ImageFormat.JPEG, ImageFormat.fromFileName("folder/subfolder/image.jpg"));
  }
}
