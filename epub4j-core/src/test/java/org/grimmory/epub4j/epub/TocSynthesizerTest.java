package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class TocSynthesizerTest {

  @Test
  void synthesizesFromSpineWithTitleTags() {
    Book book = new Book();
    addXhtmlSpineItem(
        book,
        "ch1.xhtml",
        "<html><head><title>Introduction</title></head><body><p>text</p></body></html>");
    addXhtmlSpineItem(
        book,
        "ch2.xhtml",
        "<html><head><title>Chapter One</title></head><body><p>text</p></body></html>");

    TableOfContents toc = TocSynthesizer.synthesize(book);
    assertEquals(2, toc.size());
    assertEquals("Introduction", toc.getTocReferences().get(0).getTitle());
    assertEquals("Chapter One", toc.getTocReferences().get(1).getTitle());
  }

  @Test
  void fallsBackToHeadingTag() {
    Book book = new Book();
    addXhtmlSpineItem(
        book,
        "ch1.xhtml",
        "<html><head><title></title></head><body><h1>The Beginning</h1></body></html>");

    TableOfContents toc = TocSynthesizer.synthesize(book);
    assertEquals("The Beginning", toc.getTocReferences().getFirst().getTitle());
  }

  @Test
  void fallsBackToFilename() {
    Book book = new Book();
    addXhtmlSpineItem(
        book, "chapter_03.xhtml", "<html><head></head><body><p>no headings</p></body></html>");

    TableOfContents toc = TocSynthesizer.synthesize(book);
    assertEquals("chapter 03", toc.getTocReferences().getFirst().getTitle());
  }

  @Test
  void handlesEmptySpine() {
    Book book = new Book();
    TableOfContents toc = TocSynthesizer.synthesize(book);
    assertEquals(0, toc.size());
  }

  @Test
  void skipsNullResources() {
    Book book = new Book();
    book.getSpine().getSpineReferences().add(new SpineReference(null));
    addXhtmlSpineItem(
        book, "ch1.xhtml", "<html><head><title>Valid</title></head><body></body></html>");

    TableOfContents toc = TocSynthesizer.synthesize(book);
    assertEquals(1, toc.size());
    assertEquals("Valid", toc.getTocReferences().getFirst().getTitle());
  }

  @Test
  void decodesHtmlEntitiesInTitle() {
    Book book = new Book();
    addXhtmlSpineItem(
        book, "ch1.xhtml", "<html><head><title>Rock &amp; Roll</title></head><body></body></html>");

    TableOfContents toc = TocSynthesizer.synthesize(book);
    assertEquals("Rock & Roll", toc.getTocReferences().getFirst().getTitle());
  }

  @Test
  void extractTitleReturnsNullForNonXhtml() {
    Resource res = new Resource("id", new byte[10], "image.png", MediaTypes.PNG);
    assertNull(TocSynthesizer.extractTitle(res));
  }

  @Test
  void titleFromHrefFormatsCleanly() {
    assertEquals("chapter 01", TocSynthesizer.titleFromHref("text/chapter_01.xhtml", 1));
    assertEquals("Chapter 5", TocSynthesizer.titleFromHref(null, 5));
  }

  private static void addXhtmlSpineItem(Book book, String href, String content) {
    Resource res =
        new Resource(
            "id_" + href, content.getBytes(StandardCharsets.UTF_8), href, MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);
  }
}
