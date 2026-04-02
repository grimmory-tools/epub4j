package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.junit.jupiter.api.Test;

public class EpubReaderTest {

  @Test
  public void testReadEpubStrictRejectsInvalidEpub() throws IOException {
    Path invalid = Files.createTempFile("epub4j-invalid-", ".epub");
    try {
      Files.writeString(invalid, "not-a-zip");
      EpubValidationException ex =
          assertThrows(
              EpubValidationException.class, () -> new EpubReader().readEpubStrict(invalid));
      assertTrue(ex.getMessage().contains("EPUB validation failed"));
      assertTrue(ex.issues().stream().anyMatch(i -> i.code().equals("PKG_004")));
    } finally {
      Files.deleteIfExists(invalid);
    }
  }

  @Test
  public void testReadEpubStrictAcceptsGeneratedEpub() throws IOException {
    Book book = new Book();
    book.setCoverImage(
        new Resource(this.getClass().getResourceAsStream("/book1/cover.png"), "cover.png"));
    book.addSection(
        "Introduction",
        new Resource(this.getClass().getResourceAsStream("/book1/chapter1.html"), "chapter1.html"));
    book.generateSpineFromTableOfContents();

    Path valid = Files.createTempFile("epub4j-valid-", ".epub");
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      (new EpubWriter()).write(book, out);
      Files.write(valid, out.toByteArray());

      Book readBook = new EpubReader().readEpubStrict(valid);
      assertNotNull(readBook);
      assertNotNull(readBook.getCoverPage());
    } finally {
      Files.deleteIfExists(valid);
    }
  }

  @Test
  public void testCustomBookProcessorIsApplied() throws IOException {
    Book book = new Book();
    book.setCoverImage(
        new Resource(this.getClass().getResourceAsStream("/book1/cover.png"), "cover.png"));
    book.addSection(
        "Introduction",
        new Resource(this.getClass().getResourceAsStream("/book1/chapter1.html"), "chapter1.html"));
    book.generateSpineFromTableOfContents();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    (new EpubWriter()).write(book, out);

    AtomicBoolean invoked = new AtomicBoolean(false);
    BookProcessor processor =
        inputBook -> {
          invoked.set(true);
          return inputBook;
        };

    Book readBook = new EpubReader(processor).readEpub(new ByteArrayInputStream(out.toByteArray()));

    assertTrue(invoked.get(), "Custom processor should be invoked");
    assertNotNull(readBook.getCoverPage());
  }

  @Test
  public void testCover_only_cover() throws IOException {
    Book book = new Book();

    book.setCoverImage(
        new Resource(this.getClass().getResourceAsStream("/book1/cover.png"), "cover.png"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    (new EpubWriter()).write(book, out);
    byte[] epubData = out.toByteArray();
    Book readBook = new EpubReader().readEpub(new ByteArrayInputStream(epubData));
    assertNotNull(readBook.getCoverImage());
  }

  @Test
  public void testCover_cover_one_section() throws IOException {
    Book book = new Book();

    book.setCoverImage(
        new Resource(this.getClass().getResourceAsStream("/book1/cover.png"), "cover.png"));
    book.addSection(
        "Introduction",
        new Resource(this.getClass().getResourceAsStream("/book1/chapter1.html"), "chapter1.html"));
    book.generateSpineFromTableOfContents();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    (new EpubWriter()).write(book, out);
    byte[] epubData = out.toByteArray();
    Book readBook = new EpubReader().readEpub(new ByteArrayInputStream(epubData));
    assertNotNull(readBook.getCoverPage());
    assertEquals(1, readBook.getSpine().size());
    assertEquals(1, readBook.getTableOfContents().size());
  }

  @Test
  public void testReadEpub_opf_ncx_docs() throws IOException {
    Book book = new Book();

    book.setCoverImage(
        new Resource(this.getClass().getResourceAsStream("/book1/cover.png"), "cover.png"));
    book.addSection(
        "Introduction",
        new Resource(this.getClass().getResourceAsStream("/book1/chapter1.html"), "chapter1.html"));
    book.generateSpineFromTableOfContents();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    (new EpubWriter()).write(book, out);
    byte[] epubData = out.toByteArray();
    Book readBook = new EpubReader().readEpub(new ByteArrayInputStream(epubData));
    assertNotNull(readBook.getCoverPage());
    assertEquals(1, readBook.getSpine().size());
    assertEquals(1, readBook.getTableOfContents().size());
    assertNotNull(readBook.getOpfResource());
    assertNotNull(readBook.getNcxResource());
    assertEquals(MediaTypes.NCX, readBook.getNcxResource().getMediaType());
  }

  @Test
  public void testRecoverModeReportsAndRepairsMissingToc() throws IOException {
    Path epub = createEpubWithoutToc();
    try {
      EpubReader.ReadResult result = new EpubReader().readEpubWithReport(epub);
      assertNotNull(result.book());
      assertNotNull(result.book().getTableOfContents());
      assertTrue(
          result.book().getTableOfContents().size() > 0,
          "Recover mode should synthesize TOC from spine");
      assertTrue(
          result.report().warnings().stream()
              .anyMatch(w -> w.code() == EpubReader.IngestionCode.TOC_MISSING),
          "Recover mode should emit stable TOC_MISSING warning");
      assertTrue(
          result.report().hasCorrections(), "Recover mode should report applied corrections");
    } finally {
      Files.deleteIfExists(epub);
    }
  }

  @Test
  public void testStrictModeFailsOnMissingToc() throws IOException {
    Path epub = createEpubWithoutToc();
    try {
      EpubReader strictReader = new EpubReader(null, EpubProcessingPolicy.strictPolicy());
      IOException ex = assertThrows(IOException.class, () -> strictReader.readEpub(epub));
      assertTrue(ex.getMessage().contains("TOC_MISSING"));
    } finally {
      Files.deleteIfExists(epub);
    }
  }

  @Test
  public void testRecoverModeWarnsOnInvalidMimetype() throws IOException {
    Path epub = createEpubWithMimetype();
    try {
      EpubReader.ReadResult result = new EpubReader().readEpubWithReport(epub);
      assertNotNull(result.book());
      assertTrue(
          result.report().warnings().stream()
              .anyMatch(w -> w.code() == EpubReader.IngestionCode.MIMETYPE_INVALID));
    } finally {
      Files.deleteIfExists(epub);
    }
  }

  @Test
  public void testStrictModeFailsOnInvalidMimetype() throws IOException {
    Path epub = createEpubWithMimetype();
    try {
      EpubReader strictReader = new EpubReader(null, EpubProcessingPolicy.strictPolicy());
      IOException ex = assertThrows(IOException.class, () -> strictReader.readEpub(epub));
      assertTrue(ex.getMessage().contains("MIMETYPE_INVALID"));
    } finally {
      Files.deleteIfExists(epub);
    }
  }

  private static Path createEpubWithMimetype() throws IOException {
    Path temp = Files.createTempFile("epub4j-mimetype-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(temp))) {
      zos.putNextEntry(new ZipEntry("mimetype"));
      zos.write("application/zip".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      String container =
          "<?xml version=\"1.0\"?>"
              + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
              + "<rootfiles><rootfile full-path=\"content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles>"
              + "</container>";
      zos.write(container.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("content.opf"));
      String opf =
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
              + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"uid\">"
              + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
              + "<dc:identifier id=\"uid\">urn:test:mimetype</dc:identifier>"
              + "<dc:title>Bad Mimetype</dc:title>"
              + "<dc:language>en</dc:language>"
              + "</metadata>"
              + "<manifest>"
              + "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
              + "</manifest>"
              + "<spine><itemref idref=\"c1\"/></spine>"
              + "</package>";
      zos.write(opf.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("chapter1.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>c1</title></head><body><h1>c1</h1></body></html>"
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return temp;
  }

  @Test
  public void testReadSanitizesDangerousXhtmlByDefault() throws IOException {
    String dangerousXhtml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head>"
            + "<body>"
            + "<script>alert('xss')</script>"
            + "<iframe src=\"https://evil.example.com\"></iframe>"
            + "<object data=\"x.swf\"><param name=\"a\" value=\"b\"/></object>"
            + "<p>Safe content</p>"
            + "</body></html>";
    Resources resources = new Resources();
    Resource res =
        new Resource(
            "ch1",
            dangerousXhtml.getBytes(StandardCharsets.UTF_8),
            "chapter1.xhtml",
            MediaTypes.XHTML);
    resources.add(res);

    new EpubReader().readEpub(resources);
    String content = new String(res.getData(), StandardCharsets.UTF_8);

    assertTrue(content.contains("<p>Safe content</p>"), "Safe content should be preserved");
    assertTrue(!content.contains("<script"), "Scripts should be stripped");
    assertTrue(!content.contains("<iframe"), "Iframes should be stripped");
    assertTrue(!content.contains("<object"), "Objects should be stripped");
  }

  @Test
  public void testReadPreservesDangerousXhtmlWhenSanitizationDisabled() throws IOException {
    String dangerousXhtml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head>"
            + "<body><script>alert('xss')</script><p>Safe</p></body></html>";
    Resources resources = new Resources();
    Resource res =
        new Resource(
            "ch1",
            dangerousXhtml.getBytes(StandardCharsets.UTF_8),
            "chapter1.xhtml",
            MediaTypes.XHTML);
    resources.add(res);

    EpubProcessingPolicy noSanitize =
        EpubProcessingPolicy.builder(EpubProcessingPolicy.defaultPolicy())
            .sanitizeXhtml(false)
            .build();
    new EpubReader(null, noSanitize).readEpub(resources);
    String content = new String(res.getData(), StandardCharsets.UTF_8);

    assertTrue(
        content.contains("<script>"), "Scripts should be preserved when sanitization disabled");
  }

  private static Path createEpubWithoutToc() throws IOException {
    Path temp = Files.createTempFile("epub4j-no-toc-", ".epub");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(temp))) {
      zos.putNextEntry(new ZipEntry("mimetype"));
      zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
      String container =
          "<?xml version=\"1.0\"?>"
              + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
              + "<rootfiles><rootfile full-path=\""
              + "content.opf"
              + "\" media-type=\"application/oebps-package+xml\"/></rootfiles>"
              + "</container>";
      zos.write(container.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("content.opf"));
      String opf =
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
              + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"uid\">"
              + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
              + "<dc:identifier id=\"uid\">urn:test:no-toc</dc:identifier>"
              + "<dc:title>No TOC</dc:title>"
              + "<dc:language>en</dc:language>"
              + "</metadata>"
              + "<manifest>"
              + "<item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
              + "</manifest>"
              + "<spine>"
              + "<itemref idref=\"c1\"/>"
              + "</spine>"
              + "</package>";
      zos.write(opf.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("chapter1.xhtml"));
      zos.write(
          "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>c1</title></head><body><h1>c1</h1></body></html>"
              .getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return temp;
  }
}
