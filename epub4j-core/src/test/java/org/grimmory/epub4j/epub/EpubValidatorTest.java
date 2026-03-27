package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

public class EpubValidatorTest {

  @Test
  void testValidEpubPasses() throws IOException {
    byte[] epub = createMinimalEpub();
    var result = EpubValidator.validate(new ByteArrayInputStream(epub));
    assertTrue(result.isValid(), "Valid EPUB should pass: " + result.errors());
    assertTrue(result.warnings().isEmpty(), "No warnings expected: " + result.warnings());
  }

  @Test
  void testInvalidMagicBytesDetected() throws IOException {
    byte[] notZip = "This is not a ZIP file".getBytes(StandardCharsets.UTF_8);
    var result = EpubValidator.validate(new ByteArrayInputStream(notZip));
    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("PKG_004")),
        "Should report PKG_004 for invalid PK signature");
  }

  @Test
  void testTooSmallFile() throws IOException {
    byte[] tiny = {0x50, 0x4B};
    var result = EpubValidator.validate(new ByteArrayInputStream(tiny));
    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("too small")),
        "Should report file too small");
  }

  @Test
  void testWrongFirstEntryDetected() throws IOException {
    byte[] epub = createZipWithFirstEntry("notmimetype", "application/epub+zip");
    var result = EpubValidator.validate(new ByteArrayInputStream(epub));
    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("PKG_006")),
        "Should report PKG_006 for wrong first entry");
  }

  @Test
  void testWrongMimetypeContentDetected() throws IOException {
    byte[] epub = createZipWithFirstEntry("mimetype", "application/pdf");
    var result = EpubValidator.validate(new ByteArrayInputStream(epub));
    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("PKG_007")),
        "Should report PKG_007 for wrong mimetype content");
  }

  @Test
  void testCompressedMimetypeDetected() throws IOException {
    // Create a ZIP where mimetype is DEFLATED instead of STORED
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      ZipEntry entry = new ZipEntry("mimetype");
      entry.setMethod(ZipEntry.DEFLATED); // wrong: should be STORED
      zos.putNextEntry(entry);
      zos.write("application/epub+zip".getBytes(StandardCharsets.US_ASCII));
      zos.closeEntry();
    }
    var result = EpubValidator.validate(new ByteArrayInputStream(baos.toByteArray()));
    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("PKG_007") && e.contains("compressed")),
        "Should report PKG_007 for compressed mimetype: " + result.errors());
  }

  @Test
  void testExtraFieldDetected() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      ZipEntry entry = new ZipEntry("mimetype");
      entry.setMethod(ZipEntry.STORED);
      byte[] data = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
      entry.setSize(data.length);
      entry.setCompressedSize(data.length);
      entry.setExtra(new byte[] {0x01, 0x02, 0x03, 0x04}); // non-empty extra field
      CRC32 crc = new CRC32();
      crc.update(data);
      entry.setCrc(crc.getValue());
      zos.putNextEntry(entry);
      zos.write(data);
      zos.closeEntry();
    }
    var result = EpubValidator.validate(new ByteArrayInputStream(baos.toByteArray()));
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("PKG_005")),
        "Should report PKG_005 for extra field: " + result.errors());
  }

  @Test
  void testRealTestBook() throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/testbook1.epub")) {
      assertNotNull(in, "Test book resource should exist");
      var result = EpubValidator.validate(in);
      assertTrue(result.isValid(), "testbook1.epub should be valid: " + result.errors());
    }
  }

  @Test
  void testMissingContainerDetected() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      addStoredEntry(zos, "mimetype", "application/epub+zip");
    }

    var result = EpubValidator.validate(new ByteArrayInputStream(baos.toByteArray()));
    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("EPUB_010")),
        "Should report missing container.xml: " + result.errors());
  }

  @Test
  void testSpineReferencesUnknownManifestIdDetected() throws IOException {
    String opf =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Broken Spine</dc:title>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
          </manifest>
          <spine>
            <itemref idref="chapter-1"/>
          </spine>
        </package>
        """;

    byte[] epub = createMinimalEpub(opf);
    var result = EpubValidator.validate(new ByteArrayInputStream(epub));

    assertFalse(result.isValid());
    assertTrue(
        result.errors().stream().anyMatch(e -> e.contains("EPUB_028")),
        "Should report unknown spine idref: " + result.errors());
  }

  @Test
  void testDetailedValidationReturnsStructuredIssues() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      addStoredEntry(zos, "mimetype", "application/epub+zip");
    }

    var result = EpubValidator.validateDetailed(new ByteArrayInputStream(baos.toByteArray()));
    assertFalse(result.isValid());
    assertTrue(result.errors().stream().anyMatch(i -> i.code().equals("EPUB_010")));
    assertTrue(
        result.errors().stream()
            .allMatch(i -> i.severity() == EpubValidator.ValidationSeverity.ERROR));
  }

  @Test
  void testDetailedValidationSummaryAndCounts() throws IOException {
    String opf =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Mixed Issues</dc:title>
          </metadata>
          <manifest>
            <item id="chapter" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="missing-id"/>
          </spine>
        </package>
        """;

    var result = EpubValidator.validateDetailed(new ByteArrayInputStream(createMinimalEpub(opf)));

    assertFalse(result.isValid());
    assertTrue(result.hasErrors());
    assertTrue(result.hasWarnings());
    assertEquals(1, result.errorCount());
    assertEquals(1, result.warningCount());
    assertEquals("errors=1, warnings=1", result.summary());
    assertEquals(1, result.issueCountsByCode().get("EPUB_028"));
    assertEquals(1, result.issueCountsByCode().get("EPUB_031"));
  }

  private static byte[] createMinimalEpub() throws IOException {
    String defaultOpf =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:uuid:1234</dc:identifier>
            <dc:title>Minimal</dc:title>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="chap1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="chap1"/>
          </spine>
        </package>
        """;
    return createMinimalEpub(defaultOpf);
  }

  private static byte[] createMinimalEpub(String opf) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      addStoredEntry(zos, "mimetype", "application/epub+zip");

      addDeflatedEntry(
          zos,
          "META-INF/container.xml",
          """
              <?xml version="1.0" encoding="UTF-8"?>
              <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                  <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
              </container>
              """);

      addDeflatedEntry(zos, "OEBPS/content.opf", opf);
      addDeflatedEntry(
          zos,
          "OEBPS/nav.xhtml",
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Nav</title></head><body><nav epub:type=\"toc\" xmlns:epub=\"http://www.idpf.org/2007/ops\"></nav></body></html>");
      addDeflatedEntry(
          zos,
          "OEBPS/chapter1.xhtml",
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter</title></head><body><p>Hello</p></body></html>");
    }
    return baos.toByteArray();
  }

  private static byte[] createZipWithFirstEntry(String entryName, String content)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      addStoredEntry(zos, entryName, content);

      addDeflatedEntry(
          zos,
          "META-INF/container.xml",
          """
              <?xml version="1.0" encoding="UTF-8"?>
              <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                  <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
              </container>
              """);

      addDeflatedEntry(
          zos,
          "OEBPS/content.opf",
          """
              <?xml version="1.0" encoding="UTF-8"?>
              <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test</dc:title></metadata>
                <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest>
                <spine><itemref idref="nav"/></spine>
              </package>
              """);
      addDeflatedEntry(
          zos, "OEBPS/nav.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"></html>");
    }
    return baos.toByteArray();
  }

  private static void addStoredEntry(ZipOutputStream zos, String entryName, String content)
      throws IOException {
    ZipEntry entry = new ZipEntry(entryName);
    entry.setMethod(ZipEntry.STORED);
    byte[] data = content.getBytes(StandardCharsets.US_ASCII);
    entry.setSize(data.length);
    entry.setCompressedSize(data.length);
    entry.setExtra(new byte[0]);
    CRC32 crc = new CRC32();
    crc.update(data);
    entry.setCrc(crc.getValue());
    zos.putNextEntry(entry);
    zos.write(data);
    zos.closeEntry();
  }

  private static void addDeflatedEntry(ZipOutputStream zos, String entryName, String content)
      throws IOException {
    ZipEntry entry = new ZipEntry(entryName);
    entry.setMethod(ZipEntry.DEFLATED);
    zos.putNextEntry(entry);
    zos.write(content.getBytes(StandardCharsets.UTF_8));
    zos.closeEntry();
  }
}
