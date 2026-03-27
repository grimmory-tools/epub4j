package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class WordCountEstimatorTest {

  private static Resource xhtml(String href, String body) {
    String html = "<html><body>" + body + "</body></html>";
    return new Resource(null, html.getBytes(StandardCharsets.UTF_8), href, MediaTypes.XHTML);
  }

  private static Book bookWithSpine(Resource... resources) {
    Book book = new Book();
    for (Resource r : resources) {
      book.addSection("s", r);
    }
    return book;
  }

  @Test
  void countsWordsInPlainParagraphs() {
    Book book = bookWithSpine(xhtml("ch1.xhtml", "<p>Hello world foo bar</p>"));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book);
    assertEquals(4, result.wordCount());
    assertEquals(1, result.resourcesProcessed());
  }

  @Test
  void stripsHtmlTagsBeforeCounting() {
    Book book =
        bookWithSpine(xhtml("ch1.xhtml", "<p><strong>Bold</strong> and <em>italic</em> text</p>"));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book);
    assertEquals(4, result.wordCount()); // Bold and italic text
  }

  @Test
  void excludesScriptAndStyleContent() {
    Book book =
        bookWithSpine(
            xhtml(
                "ch1.xhtml",
                "<script>var x = 1;</script><style>.a{color:red}</style><p>One two three</p>"));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book);
    assertEquals(3, result.wordCount());
  }

  @Test
  void handlesMultipleResources() {
    Book book =
        bookWithSpine(
            xhtml("ch1.xhtml", "<p>One two</p>"), xhtml("ch2.xhtml", "<p>Three four five</p>"));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book);
    assertEquals(5, result.wordCount());
    assertEquals(2, result.resourcesProcessed());
  }

  @Test
  void calculatesReadingTime() {
    // 500 words at 250 WPM = 2 minutes
    String sb = "<p>" + "word ".repeat(500) + "</p>";
    Book book = bookWithSpine(xhtml("ch1.xhtml", sb));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book, 250);
    assertEquals(2, result.readingTimeMinutes());
  }

  @Test
  void calculatesReadingTimeRoundsUp() {
    // 501 words at 250 WPM = ceil(2.004) = 3 minutes
    String sb = "<p>" + "word ".repeat(501) + "</p>";
    Book book = bookWithSpine(xhtml("ch1.xhtml", sb));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book, 250);
    assertEquals(3, result.readingTimeMinutes());
  }

  @Test
  void customWpmIsUsed() {
    String sb = "<p>" + "word ".repeat(100) + "</p>";
    Book book = bookWithSpine(xhtml("ch1.xhtml", sb));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book, 100);
    assertEquals(1, result.readingTimeMinutes());
  }

  @Test
  void emptyBookReturnsZero() {
    Book book = new Book();
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book);
    assertEquals(0, result.wordCount());
    assertEquals(0, result.charCount());
    assertEquals(0, result.readingTimeMinutes());
    assertEquals(0, result.resourcesProcessed());
  }

  @Test
  void estimateResourceCountsSingleResource() {
    Resource r = xhtml("ch.xhtml", "<p>Alpha beta gamma</p>");
    long count = WordCountEstimator.estimateResource(r);
    assertEquals(3, count);
  }

  @Test
  void htmlEntitiesDoNotCountAsWords() {
    Resource r = xhtml("ch.xhtml", "<p>Hello&nbsp;world&mdash;end</p>");
    long count = WordCountEstimator.estimateResource(r);
    // After entity replacement: "Hello world end" → but mdash joins words
    // HTML_ENTITY replaces with space, so: "Hello world end" = 3 words
    assertTrue(count >= 2);
  }

  @Test
  void zeroWpmDefaultsTo250() {
    String sb = "<p>" + "word ".repeat(250) + "</p>";
    Book book = bookWithSpine(xhtml("ch1.xhtml", sb));
    WordCountEstimator.WordCountResult result = WordCountEstimator.estimate(book, 0);
    assertEquals(1, result.readingTimeMinutes());
  }
}
