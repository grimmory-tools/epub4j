package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class KepubConverterTest {

  private static final String SIMPLE_XHTML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
          + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
          + "<head><title>Test</title></head>"
          + "<body><p>Hello world</p><p>Second paragraph</p></body></html>";

  @Test
  void convertsSimpleXhtml() throws IOException {
    Book book = createBookWithContent(SIMPLE_XHTML);
    KepubConverter.ConversionResult result = KepubConverter.convert(book);

    assertEquals(1, result.chaptersProcessed());
    assertTrue(result.spansInserted() > 0);

    String converted = new String(book.getSpine().getResource(0).getData(), StandardCharsets.UTF_8);
    assertTrue(converted.contains("koboSpan"));
    assertTrue(converted.contains("kobo.1."));
  }

  @Test
  void insertsKoboContainerDivs() throws IOException {
    Book book = createBookWithContent(SIMPLE_XHTML);
    KepubConverter.convert(book);

    String converted = new String(book.getSpine().getResource(0).getData(), StandardCharsets.UTF_8);
    assertTrue(converted.contains("book-inner"));
    assertTrue(converted.contains("book-columns"));
  }

  @Test
  void doesNotWrapScriptContent() throws IOException {
    String xhtml =
        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>Test</title><script>var x = 1;</script></head>"
            + "<body><p>Text here</p></body></html>";
    Book book = createBookWithContent(xhtml);
    KepubConverter.convert(book);

    String converted = new String(book.getSpine().getResource(0).getData(), StandardCharsets.UTF_8);
    // Script content should NOT be wrapped in koboSpan
    assertFalse(converted.contains("koboSpan\">var x = 1</span>"));
  }

  @Test
  void handlesMultipleChapters() {
    Book book = new Book();
    for (int i = 1; i <= 3; i++) {
      String content =
          "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
              + "<head><title>Ch"
              + i
              + "</title></head>"
              + "<body><p>Chapter "
              + i
              + " content</p></body></html>";
      Resource res =
          new Resource(
              "ch" + i,
              content.getBytes(StandardCharsets.UTF_8),
              "ch" + i + ".xhtml",
              MediaTypes.XHTML);
      book.getResources().add(res);
      book.getSpine().addResource(res);
    }

    KepubConverter.ConversionResult result = KepubConverter.convert(book);
    assertEquals(3, result.chaptersProcessed());
  }

  @Test
  void skipsBlankTextNodes() {
    String xhtml =
        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>Test</title></head>"
            + "<body>  <p>Real text</p>  </body></html>";
    Book book = createBookWithContent(xhtml);
    KepubConverter.ConversionResult result = KepubConverter.convert(book);
    // Should only wrap "Real text", not whitespace
    assertTrue(result.spansInserted() >= 1);
  }

  @Test
  void skipsNonXhtmlResources() {
    Book book = new Book();
    Resource css =
        new Resource("css1", "body { color: red; }".getBytes(), "style.css", MediaTypes.CSS);
    book.getResources().add(css);
    book.getSpine().addSpineReference(new SpineReference(css));

    KepubConverter.ConversionResult result = KepubConverter.convert(book);
    assertEquals(0, result.chaptersProcessed());
    assertEquals(0, result.spansInserted());
  }

  @Test
  void handlesEmptyBook() {
    Book book = new Book();
    KepubConverter.ConversionResult result = KepubConverter.convert(book);
    assertEquals(0, result.chaptersProcessed());
    assertEquals(0, result.spansInserted());
  }

  @Test
  void spanIdsAreUnique() throws IOException {
    String xhtml =
        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>Test</title></head>"
            + "<body><p>First</p><p>Second</p><p>Third</p></body></html>";
    Book book = createBookWithContent(xhtml);
    KepubConverter.convert(book);

    String converted = new String(book.getSpine().getResource(0).getData(), StandardCharsets.UTF_8);
    assertTrue(converted.contains("kobo.1.1"));
    assertTrue(converted.contains("kobo.1.2"));
    assertTrue(converted.contains("kobo.1.3"));
  }

  private static Book createBookWithContent(String xhtml) {
    Book book = new Book();
    Resource res =
        new Resource("ch1", xhtml.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);
    return book;
  }
}
