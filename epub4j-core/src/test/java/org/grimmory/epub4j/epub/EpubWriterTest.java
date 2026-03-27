package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.TOCReference;
import org.grimmory.epub4j.util.CollectionUtil;
import org.junit.jupiter.api.Test;

public class EpubWriterTest {

  @Test
  public void testBook1() throws IOException {
    // create test book
    Book book = createTestBook();

    // write book to byte[]
    byte[] bookData = writeBookToByteArray(book);
    try (FileOutputStream fileOutputStream = new FileOutputStream("foo.zip")) {
      fileOutputStream.write(bookData);
    }
    assertNotNull(bookData);
    assertTrue(bookData.length > 0);

    // read book from byte[]
    Book readBook = new EpubReader().readEpub(new ByteArrayInputStream(bookData));

    // assert book values are correct
    assertEquals(book.getMetadata().getTitles(), readBook.getMetadata().getTitles());
    // EPUB3: opf:scheme is not written, so scheme is not round-tripped
    assertEquals(
        CollectionUtil.first(book.getMetadata().getIdentifiers()).getValue(),
        CollectionUtil.first(readBook.getMetadata().getIdentifiers()).getValue());
    assertEquals(
        CollectionUtil.first(book.getMetadata().getAuthors()),
        CollectionUtil.first(readBook.getMetadata().getAuthors()));
    // EPUB3: guide element is not written, cover is found via properties="cover-image"
    assertNotNull(book.getCoverPage());
    assertNotNull(book.getCoverImage());
    assertEquals(4, readBook.getTableOfContents().size());
  }

  /**
   * Test for a very old bug where epub4j would throw a NullPointerException when writing a book
   * with a cover that has no id.
   *
   * @throws IOException
   * @throws FileNotFoundException
   */
  @Test
  public void testWritingBookWithCoverWithNullId() throws FileNotFoundException, IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Epub test book 1");
    book.getMetadata().addAuthor(new Author("Joe", "Tester"));
    InputStream is = this.getClass().getResourceAsStream("/book1/cover.png");
    book.setCoverImage(new Resource(is, "cover.png"));
    // Add Chapter 1
    InputStream is1 = this.getClass().getResourceAsStream("/book1/chapter1.html");
    book.addSection("Introduction", new Resource(is1, "chapter1.html"));

    EpubWriter epubWriter = new EpubWriter();
    epubWriter.write(book, new FileOutputStream("testbook1.epub"));
  }

  @Test
  public void testWritingMinimalBookDoesNotThrow() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Minimal");

    byte[] bookData = writeBookToByteArray(book);

    assertNotNull(bookData);
    assertTrue(bookData.length > 0);
  }

  @Test
  public void testWriterEncodesHrefInManifestAndZipEntries() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Encoded Href");
    book.addSection(
        "One",
        new Resource(
            "<html><body>chapter</body></html>".getBytes(StandardCharsets.UTF_8),
            "chapter one.xhtml"));

    byte[] bookData = writeBookToByteArray(book);
    Path tempFile = Files.createTempFile("epub4j-writer-test", ".epub");
    try {
      Files.write(tempFile, bookData);
      try (ZipFile zipFile = new ZipFile(tempFile.toFile())) {
        ZipEntry encodedChapter = zipFile.getEntry("OEBPS/chapter%20one.xhtml");
        assertNotNull(encodedChapter);

        ZipEntry opf = zipFile.getEntry("OEBPS/content.opf");
        assertNotNull(opf);
        String opfContent =
            new String(zipFile.getInputStream(opf).readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(opfContent.contains("href=\"chapter%20one.xhtml\""));
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  public void testWriterDeclaresExpectedNamespaces() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Has Namespaces");
    book.addSection(
        "One",
        new Resource(
            "<html><body>chapter</body></html>".getBytes(StandardCharsets.UTF_8),
            "chapter1.xhtml"));

    byte[] bookData = writeBookToByteArray(book);
    Path tempFile = Files.createTempFile("epub4j-writer-test", ".epub");
    try {
      Files.write(tempFile, bookData);
      try (ZipFile zipFile = new ZipFile(tempFile.toFile())) {
        ZipEntry opf = zipFile.getEntry("OEBPS/content.opf");
        assertNotNull(opf);
        String opfContent =
            new String(zipFile.getInputStream(opf).readAllBytes(), StandardCharsets.UTF_8);
        // EPUB3: OPF is the default namespace, DC is prefixed
        assertTrue(opfContent.contains("xmlns=\"http://www.idpf.org/2007/opf\""));
        assertTrue(opfContent.contains("xmlns:dc=\"http://purl.org/dc/elements/1.1/\""));
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  public void testGeneratedEpubPassesEpubCheck() throws IOException {
    Book book = createTestBook();
    byte[] bookData = writeBookToByteArray(book);

    // Validate with EPUBCheck
    EpubCheckValidationUtil.assertEpubValid(bookData);
  }

  @Test
  public void testMinimalEpubPassesEpubCheck() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Minimal Valid EPUB");
    // EPUB3: Use HTML5 DOCTYPE instead of XHTML 1.1
    book.addSection(
        "Chapter 1",
        new Resource(
            ("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <!DOCTYPE html>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>Chapter 1</title></head>
                        <body><p>Content</p></body></html>""")
                .getBytes(StandardCharsets.UTF_8),
            "chapter1.xhtml"));

    byte[] bookData = writeBookToByteArray(book);

    // Validate with EPUBCheck
    EpubCheckValidationUtil.assertEpubValid(bookData);
  }

  private Book createTestBook() throws IOException {
    Book book = new Book();

    book.getMetadata().addTitle("EPUB4J test book 1");
    book.getMetadata().addTitle("test2");

    book.getMetadata().addIdentifier(new Identifier(Identifier.Scheme.ISBN, "987654321"));
    book.getMetadata().addAuthor(new Author("Joe", "Tester"));
    book.setCoverPage(
        new Resource(this.getClass().getResourceAsStream("/book1/cover.html"), "cover.html"));
    book.setCoverImage(
        new Resource(this.getClass().getResourceAsStream("/book1/cover.png"), "cover.png"));
    book.addSection(
        "Chapter 1",
        new Resource(this.getClass().getResourceAsStream("/book1/chapter1.html"), "chapter1.html"));
    book.addResource(
        new Resource(this.getClass().getResourceAsStream("/book1/book1.css"), "book1.css"));
    TOCReference chapter2 =
        book.addSection(
            "Second chapter",
            new Resource(
                this.getClass().getResourceAsStream("/book1/chapter2.html"), "chapter2.html"));
    book.addResource(
        new Resource(this.getClass().getResourceAsStream("/book1/flowers.jpg"), "flowers.jpg"));
    book.addSection(
        chapter2,
        "Chapter 2 section 1",
        new Resource(
            this.getClass().getResourceAsStream("/book1/chapter2_1.html"), "chapter2_1.html"));
    book.addSection(
        "Chapter 3",
        new Resource(this.getClass().getResourceAsStream("/book1/chapter3.html"), "chapter3.html"));
    return book;
  }

  private static byte[] writeBookToByteArray(Book book) throws IOException {
    EpubWriter epubWriter = new EpubWriter();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    epubWriter.write(book, out);
    return out.toByteArray();
  }
}
