package org.grimmory.epub4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import com.github.gotson.nightcompress.ArchiveEntry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.grimmory.epub4j.util.IOUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Tests for ArchiveReader wrapping NightCompress/libarchive. Tests are skipped if libarchive is not
 * available on the system.
 */
@EnabledIf("isLibArchiveAvailable")
public class ArchiveReaderTest {

  private static Path testEpubPath;

  static boolean isLibArchiveAvailable() {
    return ArchiveReader.isAvailable();
  }

  @BeforeAll
  static void setUp() throws IOException {
    File tempFile = File.createTempFile("archive-reader-test-", ".epub");
    try (var out = new FileOutputStream(tempFile);
        var in = ArchiveReaderTest.class.getResourceAsStream("/testbook1.epub")) {
      assertNotNull(in, "Missing test fixture: /testbook1.epub");
      IOUtil.copy(in, out);
    }
    testEpubPath = tempFile.toPath();
  }

  @AfterAll
  static void tearDown() throws IOException {
    if (testEpubPath != null) {
      Files.deleteIfExists(testEpubPath);
    }
  }

  @Test
  void testIsAvailable() {
    assertTrue(ArchiveReader.isAvailable());
  }

  @Test
  void testIterateEntries() throws Exception {
    List<String> entryNames = new ArrayList<>();
    try (ArchiveReader reader = ArchiveReader.openZip(testEpubPath)) {
      ArchiveEntry entry;
      while ((entry = reader.nextEntry()) != null) {
        entryNames.add(entry.getName());
      }
    }
    assertFalse(entryNames.isEmpty(), "Should find entries in EPUB");
    assertTrue(entryNames.contains("mimetype"), "Should contain mimetype entry");
    assertTrue(entryNames.contains("META-INF/container.xml"), "Should contain container.xml");
  }

  @Test
  void testExtractEntry() throws Exception {
    byte[] mimetypeBytes = ArchiveReader.extractEntry(testEpubPath, "mimetype");
    assertNotNull(mimetypeBytes, "Should extract mimetype entry");
    String mimetype = new String(mimetypeBytes).trim();
    assertEquals("application/epub+zip", mimetype);
  }

  @Test
  void testExtractEntryContent() throws Exception {
    byte[] containerXml = ArchiveReader.extractEntry(testEpubPath, "META-INF/container.xml");
    assertNotNull(containerXml);
    String content = new String(containerXml);
    assertTrue(content.contains("rootfile"), "container.xml should contain rootfile element");
  }

  @Test
  void testListEntries() throws Exception {
    List<ArchiveEntry> entries = ArchiveReader.listEntries(testEpubPath);
    assertNotNull(entries);
    assertFalse(entries.isEmpty());

    boolean hasMimetype = entries.stream().anyMatch(e -> "mimetype".equals(e.getName()));
    assertTrue(hasMimetype, "Entry list should include mimetype");
  }

  @Test
  void testEntryStreamContent() throws Exception {
    try (ArchiveReader reader = ArchiveReader.openZip(testEpubPath)) {
      ArchiveEntry entry;
      while ((entry = reader.nextEntry()) != null) {
        if ("mimetype".equals(entry.getName())) {
          try (InputStream is = reader.getEntryInputStream()) {
            byte[] data = is.readAllBytes();
            assertEquals("application/epub+zip", new String(data).trim());
          }
          return;
        }
      }
    }
    fail("Should have found mimetype entry");
  }

  @Test
  void testAutoDetectFormat() throws Exception {
    // Open without specifying format (auto-detect)
    try (ArchiveReader reader = ArchiveReader.open(testEpubPath)) {
      ArchiveEntry entry = reader.nextEntry();
      assertNotNull(entry, "Auto-detected format should yield entries");
    }
  }
}
