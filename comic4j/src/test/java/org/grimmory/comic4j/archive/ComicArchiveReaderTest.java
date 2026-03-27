package org.grimmory.comic4j.archive;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.grimmory.comic4j.TestArchiveHelper;
import org.grimmory.comic4j.domain.*;
import org.grimmory.comic4j.error.ComicException;
import org.grimmory.comic4j.image.ImageEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComicArchiveReaderTest {

  @TempDir static Path tempDir;

  static Path minimalCbz;
  static Path fullCbz;
  static Path macOsCbz;
  static Path nestedCbz;
  static Path unsortedCbz;

  @BeforeAll
  static void setUp() throws IOException {
    assumeTrue(ComicArchiveReader.isAvailable(), "NightCompress/libarchive not available");

    minimalCbz = TestArchiveHelper.createMinimalCbz(tempDir, "minimal.cbz", 3);
    fullCbz =
        TestArchiveHelper.createCbzWithComicInfo(
            tempDir, "full.cbz", TestArchiveHelper.FULL_COMIC_INFO_XML, 3);
    macOsCbz = TestArchiveHelper.createCbzWithMacOSFiles(tempDir, "macos.cbz");
    nestedCbz = TestArchiveHelper.createCbzWithSubdirectories(tempDir, "nested.cbz");
    unsortedCbz = TestArchiveHelper.createCbzWithUnsortedPages(tempDir, "unsorted.cbz");
  }

  @Test
  void readBookMinimal() {
    ComicBook book = ComicArchiveReader.readBook(minimalCbz);

    assertEquals(minimalCbz, book.path());
    assertEquals(ArchiveFormat.ZIP, book.format());
    assertNull(book.comicInfo()); // no ComicInfo.xml
    assertEquals(3, book.pages().size());
    assertTrue(book.otherEntries().isEmpty());
  }

  @Test
  void readBookWithComicInfo() {
    ComicBook book = ComicArchiveReader.readBook(fullCbz);

    assertNotNull(book.comicInfo());
    assertEquals("Integration Test", book.comicInfo().getTitle());
    assertEquals("Test Series", book.comicInfo().getSeries());
    assertEquals("1", book.comicInfo().getNumber());
    assertEquals(10, book.comicInfo().getCount());
    assertEquals("Test Writer", book.comicInfo().getWriter());
    assertEquals("Test Publisher", book.comicInfo().getPublisher());
    assertEquals(AgeRating.TEEN, book.comicInfo().getAgeRating());
    assertEquals(ReadingDirection.LEFT_TO_RIGHT, book.comicInfo().getManga());
    assertEquals(YesNo.NO, book.comicInfo().getBlackAndWhite());

    assertEquals(3, book.pages().size());
    // ComicInfo.xml should be in otherEntries
    assertTrue(book.otherEntries().contains("ComicInfo.xml"));
  }

  @Test
  void readComicInfoPages() {
    ComicInfo info = ComicArchiveReader.readComicInfo(fullCbz);
    assertNotNull(info);
    assertNotNull(info.getPages());
    assertEquals(3, info.getPages().size());

    ComicPage cover = info.getPages().getFirst();
    assertEquals(0, cover.imageIndex());
    assertEquals(PageType.FRONT_COVER, cover.pageType());
    assertEquals(100, cover.imageWidth());
    assertEquals(150, cover.imageHeight());
  }

  @Test
  void readComicInfoNull() {
    ComicInfo info = ComicArchiveReader.readComicInfo(minimalCbz);
    assertNull(info);
  }

  @Test
  void listImages() {
    List<ImageEntry> images = ComicArchiveReader.listImages(minimalCbz);
    assertEquals(3, images.size());

    // Verify natural sort order
    assertEquals("page01.jpg", images.get(0).name());
    assertEquals("page02.jpg", images.get(1).name());
    assertEquals("page03.jpg", images.get(2).name());

    // Verify indices
    assertEquals(0, images.get(0).index());
    assertEquals(1, images.get(1).index());
    assertEquals(2, images.get(2).index());
  }

  @Test
  void listImagesFiltersMacOS() {
    List<ImageEntry> images = ComicArchiveReader.listImages(macOsCbz);
    assertEquals(2, images.size());
    // Only page01.jpg and page02.png should remain
    assertTrue(images.stream().anyMatch(e -> e.name().equals("page01.jpg")));
    assertTrue(images.stream().anyMatch(e -> e.name().equals("page02.png")));
  }

  @Test
  void listImagesNaturalSort() {
    List<ImageEntry> images = ComicArchiveReader.listImages(unsortedCbz);
    assertEquals(5, images.size());
    assertEquals("page1.jpg", images.get(0).name());
    assertEquals("page2.jpg", images.get(1).name());
    assertEquals("page3.jpg", images.get(2).name());
    assertEquals("page10.jpg", images.get(3).name());
    assertEquals("page20.jpg", images.get(4).name());
  }

  @Test
  void listImagesNested() {
    List<ImageEntry> images = ComicArchiveReader.listImages(nestedCbz);
    assertEquals(3, images.size());
  }

  @Test
  void extractImageByIndex() {
    byte[] image = ComicArchiveReader.extractImage(minimalCbz, 0);
    assertNotNull(image);
    assertTrue(image.length > 0);
    // JPEG magic bytes
    assertEquals((byte) 0xFF, image[0]);
    assertEquals((byte) 0xD8, image[1]);
  }

  @Test
  void extractImageByName() {
    byte[] image = ComicArchiveReader.extractImage(minimalCbz, "page01.jpg");
    assertNotNull(image);
    assertTrue(image.length > 0);
  }

  @Test
  void extractImageOutOfRange() {
    assertThrows(ComicException.class, () -> ComicArchiveReader.extractImage(minimalCbz, 999));
  }

  @Test
  void extractCoverWithMetadata() {
    byte[] cover = ComicArchiveReader.extractCover(fullCbz);
    assertNotNull(cover);
    assertTrue(cover.length > 0);
  }

  @Test
  void extractCoverWithoutMetadata() {
    byte[] cover = ComicArchiveReader.extractCover(minimalCbz);
    assertNotNull(cover);
    assertTrue(cover.length > 0);
  }

  @Test
  void getImageStream() throws IOException {
    try (InputStream is = ComicArchiveReader.getImageStream(minimalCbz, "page01.jpg")) {
      assertNotNull(is);
      byte[] data = is.readAllBytes();
      assertTrue(data.length > 0);
    }
  }

  @Test
  void detectFormat() {
    assertEquals(ArchiveFormat.ZIP, ComicArchiveReader.detectFormat(minimalCbz));
  }

  @Test
  void readBookNullThrows() {
    assertThrows(ComicException.class, () -> ComicArchiveReader.readBook(null));
  }

  @Test
  void readBookNonexistentThrows() {
    assertThrows(
        ComicException.class,
        () -> ComicArchiveReader.readBook(tempDir.resolve("nonexistent.cbz")));
  }

  @Test
  void imageEntryDisplayName() {
    List<ImageEntry> images = ComicArchiveReader.listImages(minimalCbz);
    assertEquals("page01", images.getFirst().displayName());
  }

  @Test
  void imageEntryFormat() {
    List<ImageEntry> images = ComicArchiveReader.listImages(macOsCbz);
    ImageEntry jpg =
        images.stream().filter(e -> e.name().equals("page01.jpg")).findFirst().orElseThrow();
    ImageEntry png =
        images.stream().filter(e -> e.name().equals("page02.png")).findFirst().orElseThrow();
    assertEquals(org.grimmory.comic4j.domain.ImageFormat.JPEG, jpg.format());
    assertEquals(org.grimmory.comic4j.domain.ImageFormat.PNG, png.format());
  }
}
