package org.grimmory.comic4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.grimmory.comic4j.error.ComicException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CbcReaderTest {

  @TempDir Path tempDir;

  @Test
  void readManifestWithComicsTxt() throws IOException {
    Path cbc =
        createCbcWithManifest(
            tempDir,
            "collection.cbc",
            "chapter01.cbz:Chapter One\nchapter02.cbz:Chapter Two\n",
            "chapter01.cbz",
            "chapter02.cbz");

    List<CbcReader.CbcEntry> entries = CbcReader.readManifest(cbc);
    assertEquals(2, entries.size());
    assertEquals("chapter01.cbz", entries.get(0).name());
    assertEquals("Chapter One", entries.get(0).title());
    assertEquals("chapter02.cbz", entries.get(1).name());
    assertEquals("Chapter Two", entries.get(1).title());
  }

  @Test
  void readManifestWithoutTitles() throws IOException {
    Path cbc =
        createCbcWithManifest(
            tempDir, "notitles.cbc", "vol01.cbz\nvol02.cbz\n", "vol01.cbz", "vol02.cbz");

    List<CbcReader.CbcEntry> entries = CbcReader.readManifest(cbc);
    assertEquals(2, entries.size());
    assertEquals("vol01", entries.get(0).title());
    assertEquals("vol02", entries.get(1).title());
  }

  @Test
  void readManifestSkipsCommentsAndBlanks() throws IOException {
    Path cbc =
        createCbcWithManifest(
            tempDir,
            "comments.cbc",
            "# This is a comment\n\nchapter01.cbz:Chapter One\n\n# Another comment\nchapter02.cbz\n",
            "chapter01.cbz",
            "chapter02.cbz");

    List<CbcReader.CbcEntry> entries = CbcReader.readManifest(cbc);
    assertEquals(2, entries.size());
  }

  @Test
  void readManifestSkipsMissingEntries() throws IOException {
    Path cbc =
        createCbcWithManifest(
            tempDir,
            "missing.cbc",
            "exists.cbz:Exists\nmissing.cbz:Missing\n",
            "exists.cbz"); // only exists.cbz is in the archive

    List<CbcReader.CbcEntry> entries = CbcReader.readManifest(cbc);
    assertEquals(1, entries.size());
    assertEquals("exists.cbz", entries.getFirst().name());
  }

  @Test
  void readManifestFallbackToNaturalSort() throws IOException {
    // Without comics.txt the reader should auto-discover archives
    Path cbc =
        createCbcWithoutManifest(tempDir, "nosort.cbc", "issue10.cbz", "issue2.cbz", "issue1.cbz");

    List<CbcReader.CbcEntry> entries = CbcReader.readManifest(cbc);
    assertEquals(3, entries.size());
    assertEquals("issue1.cbz", entries.get(0).name());
    assertEquals("issue2.cbz", entries.get(1).name());
    assertEquals("issue10.cbz", entries.get(2).name());
  }

  @Test
  void readManifestIgnoresNonArchiveFiles() throws IOException {
    Path cbc =
        createCbcWithoutManifest(tempDir, "mixed.cbc", "issue1.cbz", "readme.txt", "cover.jpg");

    List<CbcReader.CbcEntry> entries = CbcReader.readManifest(cbc);
    assertEquals(1, entries.size());
    assertEquals("issue1.cbz", entries.getFirst().name());
  }

  @Test
  void extractComic() throws IOException {
    byte[] dummyArchive = {0x50, 0x4B, 0x03, 0x04}; // ZIP magic
    Path cbc = createCbcWithContent(tempDir, "extract.cbc", "comic.cbz", dummyArchive);

    Path outputDir = tempDir.resolve("extracted");
    Path extracted = CbcReader.extractComic(cbc, "comic.cbz", outputDir);

    assertTrue(Files.exists(extracted));
    assertEquals("comic.cbz", extracted.getFileName().toString());
    assertArrayEquals(dummyArchive, Files.readAllBytes(extracted));
  }

  @Test
  void extractComicMissingEntryThrows() throws IOException {
    Path cbc = createCbcWithContent(tempDir, "noentry.cbc", "comic.cbz", new byte[] {0});

    assertThrows(
        ComicException.class,
        () -> CbcReader.extractComic(cbc, "nonexistent.cbz", tempDir.resolve("out")));
  }

  @Test
  void isCbc() {
    assertTrue(CbcReader.isCbc(Path.of("collection.cbc")));
    assertTrue(CbcReader.isCbc(Path.of("tmp", "MY_COLLECTION.CBC")));
    assertFalse(CbcReader.isCbc(Path.of("comic.cbz")));
    assertFalse(CbcReader.isCbc(null));
  }

  @Test
  void nullPathThrows() {
    assertThrows(ComicException.class, () -> CbcReader.readManifest(null));
  }

  @Test
  void nonexistentPathThrows() {
    assertThrows(
        ComicException.class,
        () -> CbcReader.readManifest(tempDir.resolve("nonexistent-collection.cbc")));
  }

  // --- Helpers ---

  private static Path createCbcWithManifest(
      Path dir, String name, String manifest, String... archives) throws IOException {
    Path cbc = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbc))) {
      zos.putNextEntry(new ZipEntry(CbcReader.MANIFEST_NAME));
      zos.write(manifest.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      for (String archive : archives) {
        zos.putNextEntry(new ZipEntry(archive));
        zos.write(new byte[] {0x50, 0x4B, 0x03, 0x04}); // ZIP magic bytes
        zos.closeEntry();
      }
    }
    return cbc;
  }

  private static Path createCbcWithoutManifest(Path dir, String name, String... entries)
      throws IOException {
    Path cbc = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbc))) {
      for (String entry : entries) {
        zos.putNextEntry(new ZipEntry(entry));
        zos.write(new byte[] {0x50, 0x4B, 0x03, 0x04});
        zos.closeEntry();
      }
    }
    return cbc;
  }

  private static Path createCbcWithContent(Path dir, String name, String entryName, byte[] content)
      throws IOException {
    Path cbc = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbc))) {
      zos.putNextEntry(new ZipEntry(entryName));
      zos.write(content);
      zos.closeEntry();
    }
    return cbc;
  }
}
