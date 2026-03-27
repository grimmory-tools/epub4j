package org.grimmory.comic4j.archive;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.grimmory.comic4j.TestArchiveHelper;
import org.grimmory.comic4j.domain.*;
import org.grimmory.comic4j.error.ComicException;
import org.grimmory.comic4j.image.ImageEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComicArchiveWriterTest {

  @TempDir static Path tempDir;

  @BeforeAll
  static void checkAvailability() {
    assumeTrue(ComicArchiveReader.isAvailable(), "NightCompress/libarchive not available");
  }

  @Test
  void writeToZipNewComicInfo() throws IOException {
    // Create CBZ without ComicInfo
    Path cbz = TestArchiveHelper.createMinimalCbz(tempDir, "write-new.cbz", 3);

    ComicInfo info = new ComicInfo();
    info.setTitle("Written Title");
    info.setSeries("Written Series");
    info.setWriter("Test Writer");

    ComicArchiveWriter.writeToZip(cbz, info);

    // Verify the ComicInfo was written
    ComicInfo read = ComicArchiveReader.readComicInfo(cbz);
    assertNotNull(read);
    assertEquals("Written Title", read.getTitle());
    assertEquals("Written Series", read.getSeries());
    assertEquals("Test Writer", read.getWriter());

    // Verify images are still there
    List<ImageEntry> images = ComicArchiveReader.listImages(cbz);
    assertEquals(3, images.size());
  }

  @Test
  void writeToZipReplaceComicInfo() throws IOException {
    Path cbz =
        TestArchiveHelper.createCbzWithComicInfo(
            tempDir, "write-replace.cbz", TestArchiveHelper.FULL_COMIC_INFO_XML, 3);

    // Verify original
    ComicInfo original = ComicArchiveReader.readComicInfo(cbz);
    assertEquals("Integration Test", original.getTitle());

    // Overwrite
    ComicInfo updated = new ComicInfo();
    updated.setTitle("Updated Title");
    updated.setSeries("Updated Series");
    updated.setNumber("42");

    ComicArchiveWriter.writeToZip(cbz, updated);

    // Verify updated
    ComicInfo read = ComicArchiveReader.readComicInfo(cbz);
    assertNotNull(read);
    assertEquals("Updated Title", read.getTitle());
    assertEquals("Updated Series", read.getSeries());
    assertEquals("42", read.getNumber());

    // Verify images preserved
    List<ImageEntry> images = ComicArchiveReader.listImages(cbz);
    assertEquals(3, images.size());
  }

  @Test
  void writeToZipPreservesAllEntries() throws IOException {
    Path cbz = TestArchiveHelper.createCbzWithMacOSFiles(tempDir, "write-preserve.cbz");

    ComicInfo info = new ComicInfo();
    info.setTitle("Preserve Test");

    ComicArchiveWriter.writeToZip(cbz, info);

    // Should still have images
    List<ImageEntry> images = ComicArchiveReader.listImages(cbz);
    assertEquals(2, images.size());

    // ComicInfo should be readable
    ComicInfo read = ComicArchiveReader.readComicInfo(cbz);
    assertEquals("Preserve Test", read.getTitle());
  }

  @Test
  void convertToZip() throws IOException {
    // Create a source CBZ (simulating conversion from another format)
    Path source = TestArchiveHelper.createMinimalCbz(tempDir, "source.cbz", 3);
    Path outputDir = tempDir.resolve("output");

    Path converted = ComicArchiveWriter.convertToZip(source, outputDir);

    assertTrue(Files.exists(converted));
    assertEquals("source.cbz", converted.getFileName().toString());

    List<ImageEntry> images = ComicArchiveReader.listImages(converted);
    assertEquals(3, images.size());
  }

  @Test
  void convertToZipWithInfo() throws IOException {
    Path source = TestArchiveHelper.createMinimalCbz(tempDir, "convert-info.cbz", 2);
    Path outputDir = tempDir.resolve("output-info");

    ComicInfo info = new ComicInfo();
    info.setTitle("Converted");
    info.setWriter("Convert Writer");

    Path converted = ComicArchiveWriter.convertToZipWithInfo(source, outputDir, info);

    assertTrue(Files.exists(converted));

    ComicInfo read = ComicArchiveReader.readComicInfo(converted);
    assertNotNull(read);
    assertEquals("Converted", read.getTitle());
    assertEquals("Convert Writer", read.getWriter());
  }

  @Test
  void supportsInPlaceWrite() {
    assertTrue(ComicArchiveWriter.supportsInPlaceWrite(ArchiveFormat.ZIP));
    assertFalse(ComicArchiveWriter.supportsInPlaceWrite(ArchiveFormat.RAR4));
    assertFalse(ComicArchiveWriter.supportsInPlaceWrite(ArchiveFormat.RAR5));
    assertFalse(ComicArchiveWriter.supportsInPlaceWrite(ArchiveFormat.SEVEN_ZIP));
    assertFalse(ComicArchiveWriter.supportsInPlaceWrite(ArchiveFormat.TAR));
  }

  @Test
  void writeToNonZipThrows() throws IOException {
    Path fakeRar = tempDir.resolve("fake.rar");
    // Write RAR4 magic bytes followed by junk
    Files.write(fakeRar, new byte[] {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0, 0, 0});

    ComicInfo info = new ComicInfo();
    info.setTitle("Should Fail");

    assertThrows(ComicException.class, () -> ComicArchiveWriter.writeToZip(fakeRar, info));
  }

  @Test
  void writeToNullThrows() {
    assertThrows(ComicException.class, () -> ComicArchiveWriter.writeToZip(null, new ComicInfo()));
  }

  @Test
  void fullRoundTrip() throws IOException {
    // Create → Read → Modify → Write → Read
    Path cbz =
        TestArchiveHelper.createCbzWithComicInfo(
            tempDir, "roundtrip.cbz", TestArchiveHelper.FULL_COMIC_INFO_XML, 3);

    // Read
    ComicBook book = ComicArchiveReader.readBook(cbz);
    assertEquals("Integration Test", book.comicInfo().getTitle());
    assertEquals(3, book.pages().size());

    // Modify
    ComicInfo modified = book.comicInfo();
    modified.setTitle("Round Trip Title");
    modified.setWriter("Round Trip Writer");
    modified.setVolume(99);
    modified.setBlackAndWhite(YesNo.YES);
    modified.setManga(ReadingDirection.RIGHT_TO_LEFT_MANGA);
    modified.setPages(
        List.of(
            ComicPage.builder().imageIndex(0).pageType(PageType.FRONT_COVER).build(),
            ComicPage.builder().imageIndex(1).pageType(PageType.STORY).build(),
            ComicPage.builder().imageIndex(2).pageType(PageType.BACK_COVER).build()));

    // Write back
    ComicArchiveWriter.writeToZip(cbz, modified);

    // Read again
    ComicBook updated = ComicArchiveReader.readBook(cbz);
    assertEquals("Round Trip Title", updated.comicInfo().getTitle());
    assertEquals("Round Trip Writer", updated.comicInfo().getWriter());
    assertEquals(99, updated.comicInfo().getVolume());
    assertEquals(YesNo.YES, updated.comicInfo().getBlackAndWhite());
    assertEquals(ReadingDirection.RIGHT_TO_LEFT_MANGA, updated.comicInfo().getManga());

    // Pages preserved
    assertEquals(3, updated.pages().size());

    // Page metadata round-trip
    assertNotNull(updated.comicInfo().getPages());
    assertEquals(3, updated.comicInfo().getPages().size());
    assertEquals(PageType.FRONT_COVER, updated.comicInfo().getPages().get(0).pageType());
    assertEquals(PageType.BACK_COVER, updated.comicInfo().getPages().get(2).pageType());
  }
}
