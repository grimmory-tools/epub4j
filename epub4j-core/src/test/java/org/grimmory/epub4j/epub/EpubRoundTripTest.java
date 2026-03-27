package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Random;
import java.util.zip.ZipInputStream;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.TOCReference;
import org.junit.jupiter.api.Test;

/**
 * End-to-end EPUB read/write round-trip tests verifying that NightCompress/libarchive and IOUtil
 * produce valid EPUBs.
 */
public class EpubRoundTripTest {

  @Test
  public void testWriteAndReadPreservesMetadata() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Test Title");
    book.getMetadata().addAuthor(new Author("First", "Last"));
    book.getMetadata().addDescription("A test book");
    book.addSection(
        "Chapter 1", new Resource("<html><body><h1>Ch1</h1></body></html>".getBytes(), "ch1.html"));

    byte[] epub = writeBook(book);
    Book readBook = readBook(epub);

    assertEquals("Test Title", readBook.getTitle());
    assertEquals(1, readBook.getMetadata().getAuthors().size());
    assertEquals("First", readBook.getMetadata().getAuthors().getFirst().getFirstname());
    assertEquals("Last", readBook.getMetadata().getAuthors().getFirst().getLastname());
  }

  @Test
  public void testWriteAndReadPreservesContent() throws IOException {
    byte[] htmlContent = "<html><body><p>Hello World</p></body></html>".getBytes();
    Book book = new Book();
    book.addSection("Test", new Resource(htmlContent, "chapter.html"));

    byte[] epub = writeBook(book);
    Book readBook = readBook(epub);

    Resource chapter = readBook.getResources().getByHref("chapter.html");
    assertNotNull(chapter);
    assertArrayEquals(htmlContent, chapter.getData());
  }

  @Test
  public void testWriteAndReadPreservesCover() throws IOException {
    Book book = new Book();
    try (InputStream coverStream = getClass().getResourceAsStream("/book1/cover.png")) {
      assertNotNull(coverStream, "Missing test fixture: /book1/cover.png");
      book.setCoverImage(new Resource(coverStream, "cover.png"));
    }
    book.addSection(
        "Intro", new Resource("<html><body>Intro</body></html>".getBytes(), "intro.html"));

    byte[] epub = writeBook(book);
    Book readBook = readBook(epub);

    assertNotNull(readBook.getCoverImage());
    assertTrue(readBook.getCoverImage().getData().length > 0);
  }

  @Test
  public void testWriteAndReadPreservesTableOfContents() throws IOException {
    Book book = new Book();
    book.addSection(
        "Chapter 1", new Resource("<html><body>Ch1</body></html>".getBytes(), "ch1.html"));
    TOCReference ch2 =
        book.addSection(
            "Chapter 2", new Resource("<html><body>Ch2</body></html>".getBytes(), "ch2.html"));
    book.addSection(
        ch2,
        "Section 2.1",
        new Resource("<html><body>S2.1</body></html>".getBytes(), "ch2_1.html"));
    book.addSection(
        "Chapter 3", new Resource("<html><body>Ch3</body></html>".getBytes(), "ch3.html"));

    byte[] epub = writeBook(book);
    Book readBook = readBook(epub);

    // TOC has 3 top-level + 1 nested = 4 total references
    assertTrue(readBook.getTableOfContents().size() >= 3, "Should have TOC entries");
    assertTrue(readBook.getSpine().size() >= 3, "Should have spine entries");
  }

  @Test
  public void testWriteAndReadMultipleResourceTypes() throws IOException {
    Book book = new Book();
    book.addSection(
        "Chapter",
        new Resource("<html><body><img src='img.png'/></body></html>".getBytes(), "ch.html"));
    book.getResources().add(new Resource(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}, "img.png"));
    book.getResources().add(new Resource("body { color: black; }".getBytes(), "style.css"));

    byte[] epub = writeBook(book);
    Book readBook = readBook(epub);

    assertNotNull(readBook.getResources().getByHref("ch.html"));
    assertNotNull(readBook.getResources().getByHref("img.png"));
    assertNotNull(readBook.getResources().getByHref("style.css"));
  }

  @Test
  public void testLargeBookRoundTrip() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Large Book");

    // Add 50 chapters with random content
    Random rand = new Random(42);
    for (int i = 0; i < 50; i++) {
      byte[] content = new byte[10000];
      rand.nextBytes(content);
      String html =
          "<html><body><p>" + Base64.getEncoder().encodeToString(content) + "</p></body></html>";
      book.addSection("Chapter " + (i + 1), new Resource(html.getBytes(), "chapter" + i + ".html"));
    }

    byte[] epub = writeBook(book);
    assertTrue(epub.length > 100000, "EPUB should be non-trivial size");

    Book readBook = readBook(epub);
    assertEquals(50, readBook.getTableOfContents().size());
    assertEquals(50, readBook.getSpine().size());
  }

  @Test
  public void testEpubIsValidZip() throws IOException {
    Book book = new Book();
    book.addSection("Test", new Resource("<html><body>test</body></html>".getBytes(), "ch.html"));

    byte[] epub = writeBook(book);

    // Should be readable by java.util.zip
    int entryCount = 0;
    try (ZipInputStream jzis = new ZipInputStream(new ByteArrayInputStream(epub))) {
      while (jzis.getNextEntry() != null) {
        jzis.readAllBytes();
        entryCount++;
      }
    }
    assertTrue(entryCount > 0, "EPUB must contain entries");
  }

  @Test
  public void testDoubleWriteProducesSameResult() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Double Write");
    book.addSection("Ch", new Resource("<html><body>content</body></html>".getBytes(), "ch.html"));

    byte[] epub1 = writeBook(book);
    byte[] epub2 = writeBook(book);

    // Re-read both and verify same content
    Book b1 = readBook(epub1);
    Book b2 = readBook(epub2);
    assertEquals(b1.getTitle(), b2.getTitle());
    assertEquals(b1.getTableOfContents().size(), b2.getTableOfContents().size());
  }

  private static byte[] writeBook(Book book) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new EpubWriter().write(book, out);
    return out.toByteArray();
  }

  private static Book readBook(byte[] epub) throws IOException {
    return new EpubReader().readEpub(new ByteArrayInputStream(epub));
  }
}
