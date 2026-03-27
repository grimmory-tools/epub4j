package org.grimmory.comic4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.grimmory.comic4j.TestArchiveHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveDetectorTest {

  @TempDir Path tempDir;

  @Test
  void detectZipByMagicBytes() throws IOException {
    Path cbz = TestArchiveHelper.createMinimalCbz(tempDir, "test.cbz", 1);
    assertEquals(ArchiveFormat.ZIP, ArchiveDetector.detect(cbz));
  }

  @Test
  void detectZipByExtension() throws IOException {
    // Create a file with ZIP magic bytes but .zip extension
    Path zip = TestArchiveHelper.createMinimalCbz(tempDir, "test.zip", 1);
    assertEquals(ArchiveFormat.ZIP, ArchiveDetector.detect(zip));
  }

  @Test
  void detectByExtensionFallback() throws IOException {
    // Create files with different extensions but no valid magic bytes
    Path cbr = tempDir.resolve("test.cbr");
    Files.write(cbr, new byte[] {0, 0, 0, 0});
    assertEquals(ArchiveFormat.RAR4, ArchiveDetector.detect(cbr));

    Path cb7 = tempDir.resolve("test.cb7");
    Files.write(cb7, new byte[] {0, 0, 0, 0});
    assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveDetector.detect(cb7));
  }

  @Test
  void detectUnknown() throws IOException {
    Path txt = tempDir.resolve("test.txt");
    Files.writeString(txt, "not an archive");
    assertEquals(ArchiveFormat.UNKNOWN, ArchiveDetector.detect(txt));
  }

  @Test
  void detectNullAndMissing() {
    assertEquals(ArchiveFormat.UNKNOWN, ArchiveDetector.detect(null));
    assertEquals(
        ArchiveFormat.UNKNOWN, ArchiveDetector.detect(tempDir.resolve("nonexistent-file.cbz")));
  }

  @Test
  void archiveFormatFromExtension() {
    assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromExtension("comic.cbz"));
    assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromExtension("comic.zip"));
    assertEquals(ArchiveFormat.RAR4, ArchiveFormat.fromExtension("comic.cbr"));
    assertEquals(ArchiveFormat.RAR4, ArchiveFormat.fromExtension("comic.rar"));
    assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveFormat.fromExtension("comic.cb7"));
    assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveFormat.fromExtension("comic.7z"));
    assertEquals(ArchiveFormat.TAR, ArchiveFormat.fromExtension("comic.cbt"));
    assertEquals(ArchiveFormat.TAR, ArchiveFormat.fromExtension("comic.tar"));
    assertEquals(ArchiveFormat.TAR, ArchiveFormat.fromExtension("comic.tar.gz"));
    assertEquals(ArchiveFormat.UNKNOWN, ArchiveFormat.fromExtension("comic.pdf"));
    assertEquals(ArchiveFormat.UNKNOWN, ArchiveFormat.fromExtension(null));
  }

  @Test
  void archiveFormatWritable() {
    assertTrue(ArchiveFormat.ZIP.isWritable());
    assertFalse(ArchiveFormat.RAR4.isWritable());
    assertFalse(ArchiveFormat.RAR5.isWritable());
    assertFalse(ArchiveFormat.SEVEN_ZIP.isWritable());
    assertFalse(ArchiveFormat.TAR.isWritable());
  }
}
