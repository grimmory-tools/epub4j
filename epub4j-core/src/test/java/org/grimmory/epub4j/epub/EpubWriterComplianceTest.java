package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import com.adobe.epubcheck.messages.MessageId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * EPUB3 compliance test suite for epub4j's writer.
 *
 * <p>This is the regression gate: if the final test passes, the writer is considered EPUB3
 * compliant. Each test method references the EPUBCheck message ID it guards against.
 *
 * <p>Every test writes an EPUB to a temp file, runs EPUBCheck 5.3.0 programmatically, and asserts
 * the specific error code is absent.
 */
class EpubWriterComplianceTest extends EpubCheckTestBase {

  @TempDir Path tmpDir;

  //  ZIP / OCF Container

  /**
   * PKG-006: mimetype file entry must be the first file in the archive. PKG-007: mimetype must use
   * STORED (uncompressed) method. PKG-005: mimetype must have no extra field.
   */
  @Test
  void testPKG006_mimetypeIsFirstEntry() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    // Structural assertion: mimetype is first entry and STORED
    try (ZipFile zf = new ZipFile(epub.toFile())) {
      var entries = zf.entries();
      assertTrue(entries.hasMoreElements(), "ZIP should have entries");
      ZipEntry first = entries.nextElement();
      assertEquals("mimetype", first.getName(), "First entry must be 'mimetype'");
      assertEquals(ZipEntry.STORED, first.getMethod(), "mimetype must use STORED method");
      assertEquals(
          0,
          first.getExtra() == null ? 0 : first.getExtra().length,
          "mimetype must have no extra field (PKG-005)");
    }

    // EPUBCheck validation
    assertMessageAbsent(epub.toFile(), MessageId.PKG_006);
    assertMessageAbsent(epub.toFile(), MessageId.PKG_007);
    assertMessageAbsent(epub.toFile(), MessageId.PKG_005);
  }

  /**
   * RSC-002: META-INF/container.xml must be present. RSC-003: container.xml must have a rootfile
   * with media-type application/oebps-package+xml.
   */
  @Test
  void testRSC002_containerXmlPresent() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      ZipEntry container = zf.getEntry("META-INF/container.xml");
      assertNotNull(container, "META-INF/container.xml must exist");
      String xml = new String(zf.getInputStream(container).readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(
          xml.contains("application/oebps-package+xml"),
          "container.xml must reference OPF media type");
    }

    assertMessageAbsent(epub.toFile(), MessageId.RSC_002);
    assertMessageAbsent(epub.toFile(), MessageId.RSC_003);
  }

  //  OPF Package Document

  /** OPF-001: Package version must be valid ("3.0" for EPUB3). */
  @Test
  void testOPF001_packageVersionIs30() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String opf = readZipEntry(zf, "OEBPS/content.opf");
      assertTrue(opf.contains("version=\"3.0\""), "OPF must declare version 3.0");
    }

    assertEpubValid(epub.toFile());
  }

  /**
   * OPF-048: Package must have unique-identifier attribute matching a dc:identifier id. OPF-030:
   * The referenced identifier must exist.
   */
  @Test
  void testOPF048_uniqueIdentifierPresent() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String opf = readZipEntry(zf, "OEBPS/content.opf");
      assertTrue(
          opf.contains("unique-identifier=\"BookId\""),
          "Package must have unique-identifier attribute");
      assertTrue(opf.contains("id=\"BookId\""), "dc:identifier must have matching id attribute");
    }

    assertMessageAbsent(epub.toFile(), MessageId.OPF_048);
  }

  /**
   * OPF-049: EPUB2-only attributes must not appear in EPUB3. Specifically: opf:scheme on
   * dc:identifier, opf:role/opf:file-as on dc:creator, opf:event on dc:date.
   */
  @Test
  void testOPF049_noEpub2AttributesInEpub3() throws IOException {
    Book book = createMinimalValidBook();
    book.getMetadata().addAuthor(new Author("Second", "Writer"));
    book.getMetadata().addIdentifier(new Identifier(Identifier.Scheme.ISBN, "978-0-123456-78-9"));
    book.getMetadata()
        .addDate(
            new org.grimmory.epub4j.domain.Date(
                "2024-01-15", org.grimmory.epub4j.domain.Date.Event.PUBLICATION));

    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String opf = readZipEntry(zf, "OEBPS/content.opf");
      assertFalse(opf.contains("opf:scheme"), "EPUB3: opf:scheme must not appear");
      assertFalse(opf.contains("opf:role"), "EPUB3: opf:role must not appear");
      assertFalse(opf.contains("opf:file-as"), "EPUB3: opf:file-as must not appear");
      assertFalse(opf.contains("opf:event"), "EPUB3: opf:event must not appear");
    }

    assertEpubValid(epub.toFile());
  }

  /**
   * OPF-053: EPUB3 requires dcterms:modified metadata expressed as {@code <meta
   * property="dcterms:modified">}.
   */
  @Test
  void testOPF053_dctermsModifiedPresent() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String opf = readZipEntry(zf, "OEBPS/content.opf");
      assertTrue(
          opf.contains("property=\"dcterms:modified\""),
          "EPUB3 must have <meta property=\"dcterms:modified\">");
      // Must NOT use the old <dcterms:modified> element
      assertFalse(
          opf.contains("<dcterms:modified>"), "Must not use <dcterms:modified> element in EPUB3");
    }

    assertMessageAbsent(epub.toFile(), MessageId.OPF_053);
  }

  /** OPF-034: Duplicate spine itemrefs are not allowed. */
  @Test
  void testOPF034_noDuplicateSpineItems() throws IOException {
    Book book = createMinimalValidBook();
    // Add same resource to spine twice to trigger dedup
    Resource chapter = book.getResources().getByHref("chapter1.xhtml");
    if (chapter != null) {
      book.getSpine().addResource(chapter);
    }

    Path epub = writeBookToTempFile(book, tmpDir);
    assertMessageAbsent(epub.toFile(), MessageId.OPF_034);
  }

  //  NAV Document (EPUB3 requirement)

  /**
   * RSC-005 / OPF-015: EPUB3 requires a Navigation Document (nav) in the manifest with
   * properties="nav".
   */
  @Test
  void testNavDocumentPresent() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      // NAV document must exist in ZIP
      ZipEntry navEntry = zf.getEntry("OEBPS/toc.xhtml");
      assertNotNull(navEntry, "EPUB3 NAV document (toc.xhtml) must exist");

      // NAV must contain epub:type="toc"
      String nav = new String(zf.getInputStream(navEntry).readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(nav.contains("epub:type=\"toc\""), "NAV must contain <nav epub:type=\"toc\">");

      // Manifest must have properties="nav" on the NAV item
      String opf = readZipEntry(zf, "OEBPS/content.opf");
      assertTrue(
          opf.contains("properties=\"nav\""), "NAV manifest item must have properties=\"nav\"");
    }

    assertEpubValid(epub.toFile());
  }

  /** NAV document must include TOC entries that match the book's table of contents. */
  @Test
  void testNavDocumentContainsTocEntries() throws IOException {
    Book book = createMinimalValidBook();
    // Add a second chapter
    String xhtml2 =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 2</title></head>
                <body><p>Second chapter.</p></body>
                </html>""";
    book.addSection(
        "Chapter 2", new Resource(xhtml2.getBytes(StandardCharsets.UTF_8), "chapter2.xhtml"));

    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String nav = readZipEntry(zf, "OEBPS/toc.xhtml");
      assertTrue(nav.contains("chapter1.xhtml"), "NAV must reference chapter1");
      assertTrue(nav.contains("Chapter 1"), "NAV must contain chapter 1 title");
      assertTrue(nav.contains("chapter2.xhtml"), "NAV must reference chapter2");
      assertTrue(nav.contains("Chapter 2"), "NAV must contain chapter 2 title");
    }

    assertEpubValid(epub.toFile());
  }

  //  NCX (backwards compatibility)

  /** NCX-001: NCX dtb:uid must match OPF unique identifier. */
  @Test
  void testNCX001_ncxUidMatchesOpfIdentifier() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String ncx = readZipEntry(zf, "OEBPS/toc.ncx");

      // Extract the identifier value from OPF
      assertTrue(
          ncx.contains("urn:uuid:12345678-1234-1234-1234-123456789012"),
          "NCX dtb:uid must match book identifier");
    }

    assertMessageAbsent(epub.toFile(), MessageId.NCX_001);
  }

  //  Manifest & Resources

  /**
   * OPF-003: All resources in the EPUB must be declared in the manifest. The cover image must have
   * properties="cover-image" in EPUB3.
   */
  @Test
  void testCoverImagePropertiesAttribute() throws IOException {
    Book book = createMinimalValidBook();
    book.setCoverImage(new Resource(createMinimalPng(), "cover.png"));

    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String opf = readZipEntry(zf, "OEBPS/content.opf");
      // Cover image item must have properties="cover-image"
      assertTrue(
          opf.contains("properties=\"cover-image\""),
          "Cover image manifest item must have properties=\"cover-image\"");
      // Must NOT have legacy EPUB2 <meta name="cover" ...>
      assertFalse(
          opf.contains("name=\"cover\""),
          "EPUB3 must not use legacy <meta name=\"cover\"> element");
    }

    assertEpubValid(epub.toFile());
  }

  /** OPF-033: Spine must have at least one linear item. */
  @Test
  void testOPF033_spineHasLinearItem() throws IOException {
    Book book = createMinimalValidBook();
    Path epub = writeBookToTempFile(book, tmpDir);

    try (ZipFile zf = new ZipFile(epub.toFile())) {
      String opf = readZipEntry(zf, "OEBPS/content.opf");
      // At least one itemref should exist without linear="no"
      assertTrue(opf.contains("<itemref idref="), "Spine must have at least one itemref");
    }

    assertMessageAbsent(epub.toFile(), MessageId.OPF_033);
  }

  //  Metadata round-trip

  /** Title must survive a write->read round-trip and EPUBCheck must report no errors. */
  @Test
  void testMetadataTitleRoundTrip() throws IOException {
    Book book = createMinimalValidBook();
    book.getMetadata().addTitle("My Special Title");

    Path epub = writeBookToTempFile(book, tmpDir);
    assertEpubValid(epub.toFile());

    // Round-trip: read back
    Book readBook = new EpubReader().readEpub(Files.newInputStream(epub));
    assertTrue(
        readBook.getMetadata().getTitles().contains("My Special Title"),
        "Added title must survive write->read round-trip");
  }

  /** Author must survive a write->read round-trip and EPUBCheck must report no errors. */
  @Test
  void testMetadataAuthorRoundTrip() throws IOException {
    Book book = createMinimalValidBook();

    Path epub = writeBookToTempFile(book, tmpDir);
    assertEpubValid(epub.toFile());

    Book readBook = new EpubReader().readEpub(Files.newInputStream(epub));
    assertFalse(
        readBook.getMetadata().getAuthors().isEmpty(),
        "Author must survive write->read round-trip");
    assertEquals("Test", readBook.getMetadata().getAuthors().getFirst().getFirstname());
    assertEquals("Author", readBook.getMetadata().getAuthors().getFirst().getLastname());
  }

  /** Identifier must survive a write->read round-trip and EPUBCheck must report no errors. */
  @Test
  void testMetadataIdentifierRoundTrip() throws IOException {
    Book book = createMinimalValidBook();

    Path epub = writeBookToTempFile(book, tmpDir);
    assertEpubValid(epub.toFile());

    Book readBook = new EpubReader().readEpub(Files.newInputStream(epub));
    assertFalse(
        readBook.getMetadata().getIdentifiers().isEmpty(),
        "Identifier must survive write->read round-trip");
    assertEquals(
        "12345678-1234-1234-1234-123456789012",
        readBook.getMetadata().getIdentifiers().getFirst().getValue(),
        "URN prefix should be stripped; scheme captures the type");
  }

  //  FINAL REGRESSION GATE

  /**
   * Creates a Book with one XHTML spine item, one cover image (PNG), title, author, unique
   * identifier, writes it to a temp file, and asserts zero EPUBCheck FATAL or ERROR messages.
   *
   * <p>This test IS the regression gate: if it passes, the writer is considered EPUB3 compliant.
   */
  @Test
  void testFullComplianceRegressionGate() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("EPUB3 Compliance Test");
    book.getMetadata().addAuthor(new Author("Jane", "Doe"));
    book.getMetadata()
        .addIdentifier(
            new Identifier(
                Identifier.Scheme.UUID, "urn:uuid:a1b2c3d4-e5f6-7890-abcd-ef1234567890"));

    // One XHTML spine item
    String xhtml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 1</title></head>
                <body>
                <h1>Chapter 1</h1>
                <p>This is a compliance test chapter.</p>
                </body>
                </html>""";
    book.addSection(
        "Chapter 1", new Resource(xhtml.getBytes(StandardCharsets.UTF_8), "chapter1.xhtml"));

    // One cover image (PNG)
    book.setCoverImage(new Resource(createMinimalPng(), "cover.png"));

    // Write and validate
    Path epub = writeBookToTempFile(book, tmpDir);
    assertEpubValid(epub.toFile());
  }

  //  Helpers

  private static String readZipEntry(ZipFile zf, String entryName) throws IOException {
    ZipEntry entry = zf.getEntry(entryName);
    assertNotNull(entry, "ZIP entry " + entryName + " must exist");
    return new String(zf.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
  }
}
