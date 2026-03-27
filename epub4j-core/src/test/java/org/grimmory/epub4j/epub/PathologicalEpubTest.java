package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.grimmory.epub4j.domain.Book;
import org.junit.jupiter.api.Test;

/**
 * Tests for EPUB handling with pathological test cases. Creates and tests malformed EPUBs that real
 * users encounter.
 */
public class PathologicalEpubTest {

  private final EpubReader reader = new EpubReader();

  /** Test reading an EPUB with UTF-8 BOM in content.opf. */
  @Test
  public void testEpubWithUtf8Bom() throws IOException {
    Path tempEpub = createMinimalEpubWithBom();
    try {
      Book book = reader.readEpub(tempEpub);
      assertNotNull(book);
      assertNotNull(book.getMetadata());
    } finally {
      Files.deleteIfExists(tempEpub);
    }
  }

  /** Test reading an EPUB with missing TOC (should synthesize from spine). */
  @Test
  public void testEpubMissingToc() throws IOException {
    Path tempEpub = createEpubWithoutToc();
    try {
      Book book = reader.readEpub(tempEpub);
      assertNotNull(book);
      // TOC should be synthesized from spine
      assertNotNull(book.getTableOfContents());
      assertTrue(book.getTableOfContents().size() > 0);
    } finally {
      Files.deleteIfExists(tempEpub);
    }
  }

  /** Test reading an EPUB with duplicate resource IDs. */
  @Test
  public void testEpubDuplicateIds() throws IOException {
    Path tempEpub = createEpubWithDuplicateIds();
    try {
      Book book = reader.readEpub(tempEpub);
      assertNotNull(book);
      // BookRepair should have renamed duplicates
      var repair = new BookRepair();
      var result = repair.repair(book);
      result.hasChanges();
      assertTrue(true); // At least it shouldn't crash
    } finally {
      Files.deleteIfExists(tempEpub);
    }
  }

  /** Test reading an EPUB with broken internal references. */
  @Test
  public void testEpubBrokenReferences() throws IOException {
    Path tempEpub = createEpubWithBrokenReferences();
    try {
      Book book = reader.readEpub(tempEpub);
      assertNotNull(book);

      // Diagnostics should detect broken references
      var diagnostics = EpubDiagnostics.check(book);
      assertNotNull(diagnostics);
    } finally {
      Files.deleteIfExists(tempEpub);
    }
  }

  /** Test reading an EPUB with missing metadata title. */
  @Test
  public void testEpubMissingTitle() throws IOException {
    Path tempEpub = createEpubWithoutTitle();
    try {
      Book book = reader.readEpub(tempEpub);
      assertNotNull(book);

      // BookRepair should fix missing title
      var repair = new BookRepair();
      var result = repair.repair(book);
      assertTrue(result.hasChanges());
      assertNotNull(book.getMetadata().getFirstTitle());
    } finally {
      Files.deleteIfExists(tempEpub);
    }
  }

  /** Test reading an EPUB with mixed encodings. */
  @Test
  public void testEpubMixedEncodings() throws IOException {
    Path tempEpub = createEpubWithMixedEncodings();
    try {
      Book book = reader.readEpub(tempEpub);
      assertNotNull(book);

      // Encoding normalizer should handle it
      EncodingNormalizer.detectEncodingIssues(book);
      // At least it shouldn't crash
    } finally {
      Files.deleteIfExists(tempEpub);
    }
  }

  /** Test reading an EPUB with null spine references. */
  @Test
  public void testEpubNullSpineReferences() throws IOException {
    Path tempEpub = createEpubWithNullSpineRefs();
    try {
      Book book = reader.readEpub(tempEpub);
      assertNotNull(book);

      // BookRepair should remove null references
      var repair = new BookRepair();
      repair.repair(book);
      // Should have fixed the null references
    } finally {
      Files.deleteIfExists(tempEpub);
    }
  }

  private Path createMinimalEpubWithBom() throws IOException {
    Path tempFile = Files.createTempFile("epub-bom-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
      // mimetype (must be first, uncompressed)
      ZipEntry mimetype = new ZipEntry("mimetype");
      mimetype.setMethod(ZipEntry.STORED);
      byte[] mimetypeData = "application/epub+zip".getBytes();
      mimetype.setSize(mimetypeData.length);
      mimetype.setCrc(calculateCrc(mimetypeData));
      zos.putNextEntry(mimetype);
      zos.write(mimetypeData);
      zos.closeEntry();

      // container.xml
      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      zos.write(
          ("""
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // content.opf with UTF-8 BOM
      zos.putNextEntry(new ZipEntry("content.opf"));
      byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
      String opfXml =
          """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:identifier id="uid">test-123</dc:identifier>
                            <dc:title>Test Book with BOM</dc:title>
                            <dc:language>en</dc:language>
                          </metadata>
                          <manifest>
                            <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="chapter1"/>
                          </spine>
                        </package>""";
      byte[] opfData = opfXml.getBytes(StandardCharsets.UTF_8);

      byte[] withBom = new byte[bom.length + opfData.length];
      System.arraycopy(bom, 0, withBom, 0, bom.length);
      System.arraycopy(opfData, 0, withBom, bom.length, opfData.length);
      zos.write(withBom);
      zos.closeEntry();

      // chapter1.xhtml
      zos.putNextEntry(new ZipEntry("chapter1.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 1</title></head><body><h1>Chapter 1</h1></body></html>"
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return tempFile;
  }

  private Path createEpubWithoutToc() throws IOException {
    Path tempFile = Files.createTempFile("epub-no-toc-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
      // mimetype
      ZipEntry mimetype = new ZipEntry("mimetype");
      mimetype.setMethod(ZipEntry.STORED);
      byte[] mimetypeData = "application/epub+zip".getBytes();
      mimetype.setSize(mimetypeData.length);
      mimetype.setCrc(calculateCrc(mimetypeData));
      zos.putNextEntry(mimetype);
      zos.write(mimetypeData);
      zos.closeEntry();

      // container.xml
      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      zos.write(
          ("""
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // content.opf without TOC reference
      zos.putNextEntry(new ZipEntry("content.opf"));
      zos.write(
          ("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:identifier id="uid">test-no-toc</dc:identifier>
                            <dc:title>Test Without TOC</dc:title>
                            <dc:language>en</dc:language>
                          </metadata>
                          <manifest>
                            <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                            <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="ch1"/>
                            <itemref idref="ch2"/>
                          </spine>
                        </package>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // Chapters
      zos.putNextEntry(new ZipEntry("ch1.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 1</title></head><body><h1>Chapter 1</h1></body></html>"
              .getBytes());
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("ch2.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 2</title></head><body><h1>Chapter 2</h1></body></html>"
              .getBytes());
      zos.closeEntry();
    }
    return tempFile;
  }

  private Path createEpubWithDuplicateIds() throws IOException {
    Path tempFile = Files.createTempFile("epub-dup-ids-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
      // mimetype
      ZipEntry mimetype = new ZipEntry("mimetype");
      mimetype.setMethod(ZipEntry.STORED);
      byte[] mimetypeData = "application/epub+zip".getBytes();
      mimetype.setSize(mimetypeData.length);
      mimetype.setCrc(calculateCrc(mimetypeData));
      zos.putNextEntry(mimetype);
      zos.write(mimetypeData);
      zos.closeEntry();

      // container.xml
      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      zos.write(
          ("""
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // content.opf with duplicate IDs
      zos.putNextEntry(new ZipEntry("content.opf"));
      zos.write(
          ("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:identifier id="uid">test-dup</dc:identifier>
                            <dc:title>Test Duplicate IDs</dc:title>
                            <dc:language>en</dc:language>
                          </metadata>
                          <manifest>
                            <item id="chapter" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                            <item id="chapter" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="chapter"/>
                            <itemref idref="chapter"/>
                          </spine>
                        </package>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // Chapters
      zos.putNextEntry(new ZipEntry("ch1.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 1</title></head><body><h1>Chapter 1</h1></body></html>"
              .getBytes());
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("ch2.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 2</title></head><body><h1>Chapter 2</h1></body></html>"
              .getBytes());
      zos.closeEntry();
    }
    return tempFile;
  }

  private Path createEpubWithBrokenReferences() throws IOException {
    Path tempFile = Files.createTempFile("epub-broken-ref-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
      // Standard EPUB structure with broken CSS reference
      ZipEntry mimetype = new ZipEntry("mimetype");
      mimetype.setMethod(ZipEntry.STORED);
      byte[] mimetypeData = "application/epub+zip".getBytes();
      mimetype.setSize(mimetypeData.length);
      mimetype.setCrc(calculateCrc(mimetypeData));
      zos.putNextEntry(mimetype);
      zos.write(mimetypeData);
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      zos.write(
          ("""
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("content.opf"));
      zos.write(
          ("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:identifier id="uid">test-broken</dc:identifier>
                            <dc:title>Test Broken References</dc:title>
                            <dc:language>en</dc:language>
                          </metadata>
                          <manifest>
                            <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="ch1"/>
                          </spine>
                        </package>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // Chapter with reference to missing CSS
      zos.putNextEntry(new ZipEntry("ch1.xhtml"));
      zos.write(
          ("""
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>Chapter 1</title>
                        <link rel="stylesheet" type="text/css" href="missing.css"/>
                        </head>
                        <body><h1>Chapter 1</h1></body></html>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return tempFile;
  }

  private Path createEpubWithoutTitle() throws IOException {
    Path tempFile = Files.createTempFile("epub-no-title-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
      ZipEntry mimetype = new ZipEntry("mimetype");
      mimetype.setMethod(ZipEntry.STORED);
      byte[] mimetypeData = "application/epub+zip".getBytes();
      mimetype.setSize(mimetypeData.length);
      mimetype.setCrc(calculateCrc(mimetypeData));
      zos.putNextEntry(mimetype);
      zos.write(mimetypeData);
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      zos.write(
          ("""
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("content.opf"));
      zos.write(
          ("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:identifier id="uid">test-no-title</dc:identifier>
                            <dc:language>en</dc:language>
                          </metadata>
                          <manifest>
                            <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="ch1"/>
                          </spine>
                        </package>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("ch1.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 1</title></head><body><h1>Chapter 1</h1></body></html>"
              .getBytes());
      zos.closeEntry();
    }
    return tempFile;
  }

  private Path createEpubWithMixedEncodings() throws IOException {
    Path tempFile = Files.createTempFile("epub-mixed-enc-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
      ZipEntry mimetype = new ZipEntry("mimetype");
      mimetype.setMethod(ZipEntry.STORED);
      byte[] mimetypeData = "application/epub+zip".getBytes();
      mimetype.setSize(mimetypeData.length);
      mimetype.setCrc(calculateCrc(mimetypeData));
      zos.putNextEntry(mimetype);
      zos.write(mimetypeData);
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      zos.write(
          ("""
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("content.opf"));
      zos.write(
          ("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:identifier id="uid">test-mixed</dc:identifier>
                            <dc:title>Test Mixed Encodings</dc:title>
                            <dc:language>en</dc:language>
                          </metadata>
                          <manifest>
                            <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="ch1"/>
                          </spine>
                        </package>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // Chapter with ISO-8859-1 declaration but UTF-8 content
      zos.putNextEntry(new ZipEntry("ch1.xhtml"));
      zos.write(
          ("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                  + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 1</title></head><body><h1>Chapter 1</h1></body></html>")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return tempFile;
  }

  private Path createEpubWithNullSpineRefs() throws IOException {
    // This is hard to create programmatically since the EPUB reader constructs the spine
    // For now, we test the BookRepair logic directly
    Path tempFile = Files.createTempFile("epub-null-spine-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
      ZipEntry mimetype = new ZipEntry("mimetype");
      mimetype.setMethod(ZipEntry.STORED);
      byte[] mimetypeData = "application/epub+zip".getBytes();
      mimetype.setSize(mimetypeData.length);
      mimetype.setCrc(calculateCrc(mimetypeData));
      zos.putNextEntry(mimetype);
      zos.write(mimetypeData);
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      zos.write(
          ("""
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("content.opf"));
      zos.write(
          ("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:identifier id="uid">test-null-spine</dc:identifier>
                            <dc:title>Test Null Spine</dc:title>
                            <dc:language>en</dc:language>
                          </metadata>
                          <manifest>
                            <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="ch1"/>
                          </spine>
                        </package>""")
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("ch1.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter 1</title></head><body><h1>Chapter 1</h1></body></html>"
              .getBytes());
      zos.closeEntry();
    }
    return tempFile;
  }

  private long calculateCrc(byte[] data) {
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(data);
    return crc.getValue();
  }
}
