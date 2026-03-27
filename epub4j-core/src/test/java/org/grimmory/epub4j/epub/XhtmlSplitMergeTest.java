package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class XhtmlSplitMergeTest {

  private static final String MULTI_CHAPTER_XHTML =
      """
            <?xml version="1.0" encoding="UTF-8"?>\
            <html xmlns="http://www.w3.org/1999/xhtml">\
            <head><title>Book</title></head>\
            <body>\
            <h1>Chapter One</h1><p>First chapter content.</p>\
            <h1>Chapter Two</h1><p>Second chapter content.</p>\
            <h1>Chapter Three</h1><p>Third chapter content.</p>\
            </body></html>""";

  @Test
  void splitAtH1Headings() {
    Book book = new Book();
    Resource res =
        new Resource(
            "ch1",
            MULTI_CHAPTER_XHTML.getBytes(StandardCharsets.UTF_8),
            "text/content.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);

    XhtmlSplitMerge.SplitResult result = XhtmlSplitMerge.splitAtHeadings(book, res, 1);
    assertEquals(3, result.fragmentCount());
    assertEquals("text/content.xhtml", result.originalHref());

    // First fragment keeps original href
    assertEquals("text/content.xhtml", result.fragments().getFirst().getHref());
    // Spine should have 3 items
    assertEquals(3, book.getSpine().size());
  }

  @Test
  void splitPreservesHeadSection() throws IOException {
    Book book = new Book();
    Resource res =
        new Resource(
            "ch1",
            MULTI_CHAPTER_XHTML.getBytes(StandardCharsets.UTF_8),
            "content.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);

    XhtmlSplitMerge.SplitResult result = XhtmlSplitMerge.splitAtHeadings(book, res, 1);
    for (Resource frag : result.fragments()) {
      String content = new String(frag.getData(), StandardCharsets.UTF_8);
      assertTrue(content.contains("<head>"));
      assertTrue(content.contains("</body>"));
      assertTrue(content.contains("</html>"));
    }
  }

  @Test
  void splitWithNoHeadingsReturnsSingleFragment() {
    String xhtml =
        """
                <?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml">\
                <head><title>No headings</title></head>\
                <body><p>Just text.</p></body></html>""";
    Book book = new Book();
    Resource res =
        new Resource("ch1", xhtml.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);

    XhtmlSplitMerge.SplitResult result = XhtmlSplitMerge.splitAtHeadings(book, res, 1);
    assertEquals(1, result.fragmentCount());
  }

  @Test
  void splitWithNoBodyReturnsSingleFragment() {
    String xhtml = "<html><head></head><p>no body tags</p></html>";
    Book book = new Book();
    Resource res =
        new Resource("ch1", xhtml.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);

    XhtmlSplitMerge.SplitResult result = XhtmlSplitMerge.splitAtHeadings(book, res, 1);
    assertEquals(1, result.fragmentCount());
  }

  @Test
  void splitAtH2Level() {
    String xhtml =
        """
                <?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml">\
                <head><title>Book</title></head>\
                <body>\
                <h1>Part One</h1><p>Intro</p>\
                <h2>Section A</h2><p>Details</p>\
                <h2>Section B</h2><p>More details</p>\
                </body></html>""";
    Book book = new Book();
    Resource res =
        new Resource(
            "ch1", xhtml.getBytes(StandardCharsets.UTF_8), "content.xhtml", MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);

    XhtmlSplitMerge.SplitResult result = XhtmlSplitMerge.splitAtHeadings(book, res, 2);
    assertEquals(3, result.fragmentCount());
  }

  @Test
  void splitThrowsOnInvalidHeadingLevel() {
    Book book = new Book();
    Resource res = new Resource("ch1", "data".getBytes(), "ch1.xhtml", MediaTypes.XHTML);
    assertThrows(
        IllegalArgumentException.class, () -> XhtmlSplitMerge.splitAtHeadings(book, res, 0));
    assertThrows(
        IllegalArgumentException.class, () -> XhtmlSplitMerge.splitAtHeadings(book, res, 7));
  }

  @Test
  void splitGeneratesCorrectFragmentHrefs() {
    Book book = new Book();
    Resource res =
        new Resource(
            "ch1",
            MULTI_CHAPTER_XHTML.getBytes(StandardCharsets.UTF_8),
            "text/chapter.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);

    XhtmlSplitMerge.SplitResult result = XhtmlSplitMerge.splitAtHeadings(book, res, 1);
    assertEquals("text/chapter.xhtml", result.fragments().get(0).getHref());
    assertTrue(result.fragments().get(1).getHref().startsWith("text/chapter_"));
    assertTrue(result.fragments().get(1).getHref().endsWith(".xhtml"));
  }

  @Test
  void mergesTwoResources() throws IOException {
    Book book = new Book();
    Resource ch1 = addXhtmlResource(book, "ch1.xhtml", "Chapter 1", "<p>First.</p>");
    Resource ch2 = addXhtmlResource(book, "ch2.xhtml", "Chapter 2", "<p>Second.</p>");

    XhtmlSplitMerge.MergeResult result = XhtmlSplitMerge.merge(book, List.of(ch1, ch2));
    assertEquals(2, result.sourceCount());
    assertEquals("ch1.xhtml", result.merged().getHref());

    String content = new String(result.merged().getData(), StandardCharsets.UTF_8);
    assertTrue(content.contains("First."));
    assertTrue(content.contains("Second."));
    assertTrue(content.contains("epub-merge-separator"));

    // Spine should have 1 item
    assertEquals(1, book.getSpine().size());
  }

  @Test
  void mergeThrowsOnSingleResource() {
    Book book = new Book();
    Resource ch1 = addXhtmlResource(book, "ch1.xhtml", "Ch1", "<p>text</p>");
    assertThrows(IllegalArgumentException.class, () -> XhtmlSplitMerge.merge(book, List.of(ch1)));
  }

  @Test
  void mergePreservesFirstResourceId() {
    Book book = new Book();
    Resource ch1 = addXhtmlResource(book, "ch1.xhtml", "Ch1", "<p>a</p>");
    Resource ch2 = addXhtmlResource(book, "ch2.xhtml", "Ch2", "<p>b</p>");

    XhtmlSplitMerge.MergeResult result = XhtmlSplitMerge.merge(book, List.of(ch1, ch2));
    assertEquals(ch1.getId(), result.merged().getId());
  }

  @Test
  void mergeRemovesOldResources() {
    Book book = new Book();
    Resource ch1 = addXhtmlResource(book, "ch1.xhtml", "Ch1", "<p>a</p>");
    Resource ch2 = addXhtmlResource(book, "ch2.xhtml", "Ch2", "<p>b</p>");

    XhtmlSplitMerge.merge(book, List.of(ch1, ch2));
    assertFalse(book.getResources().containsByHref("ch2.xhtml"));
    assertTrue(book.getResources().containsByHref("ch1.xhtml"));
  }

  @Test
  void splitThenMergeRoundTrip() throws IOException {
    Book book = new Book();
    Resource res =
        new Resource(
            "ch1",
            MULTI_CHAPTER_XHTML.getBytes(StandardCharsets.UTF_8),
            "content.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);

    // Split
    XhtmlSplitMerge.SplitResult splitResult = XhtmlSplitMerge.splitAtHeadings(book, res, 1);
    assertEquals(3, splitResult.fragmentCount());
    assertEquals(3, book.getSpine().size());

    // Merge back
    XhtmlSplitMerge.MergeResult mergeResult = XhtmlSplitMerge.merge(book, splitResult.fragments());
    assertEquals(1, book.getSpine().size());

    String content = new String(mergeResult.merged().getData(), StandardCharsets.UTF_8);
    assertTrue(content.contains("Chapter One"));
    assertTrue(content.contains("Chapter Three"));
  }

  private static Resource addXhtmlResource(Book book, String href, String title, String body) {
    String xhtml =
        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>"
            + title
            + "</title></head>"
            + "<body>"
            + body
            + "</body></html>";
    Resource res =
        new Resource("id_" + href, xhtml.getBytes(StandardCharsets.UTF_8), href, MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);
    return res;
  }
}
