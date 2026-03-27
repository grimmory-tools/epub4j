package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class SearchReplaceTest {

  private static Resource xhtml(String href, String body) {
    String html = "<html><body>" + body + "</body></html>";
    return new Resource(null, html.getBytes(StandardCharsets.UTF_8), href, MediaTypes.XHTML);
  }

  private static Book bookWith(Resource... resources) {
    Book book = new Book();
    for (Resource r : resources) {
      book.getResources().add(r);
    }
    return book;
  }

  @Test
  void searchFindsPlainText() {
    Book book = bookWith(xhtml("ch1.xhtml", "<p>Hello world</p>"));
    SearchReplace.SearchResult result = SearchReplace.search(book, "Hello", true);
    assertEquals(1, result.matchCount());
    assertEquals("Hello", result.matches().getFirst().matchText());
  }

  @Test
  void searchCaseInsensitive() {
    Book book = bookWith(xhtml("ch1.xhtml", "<p>Hello world</p>"));
    SearchReplace.SearchResult result = SearchReplace.search(book, "hello", false);
    assertEquals(1, result.matchCount());
  }

  @Test
  void searchCaseSensitiveMisses() {
    Book book = bookWith(xhtml("ch1.xhtml", "<p>Hello world</p>"));
    SearchReplace.SearchResult result = SearchReplace.search(book, "hello", true);
    assertEquals(0, result.matchCount());
  }

  @Test
  void searchAcrossMultipleResources() {
    Book book =
        bookWith(
            xhtml("ch1.xhtml", "<p>Hello world</p>"), xhtml("ch2.xhtml", "<p>Hello again</p>"));
    SearchReplace.SearchResult result = SearchReplace.search(book, "Hello", true);
    assertEquals(2, result.matchCount());
    assertEquals(2, result.resourcesAffected());
  }

  @Test
  void searchRegexWithPattern() {
    Book book = bookWith(xhtml("ch1.xhtml", "<p>Page 42 and page 99</p>"));
    SearchReplace.SearchResult result = SearchReplace.searchRegex(book, "\\d+", 0);
    assertEquals(2, result.matchCount());
  }

  @Test
  void replaceAllModifiesContent() throws IOException {
    Resource r = xhtml("ch1.xhtml", "<p>Hello world</p>");
    Book book = bookWith(r);
    SearchReplace.SearchResult result = SearchReplace.replaceAll(book, "Hello", "Goodbye", true);
    assertEquals(1, result.totalReplacements());
    String content = new String(r.getData(), StandardCharsets.UTF_8);
    assertTrue(content.contains("Goodbye"));
    assertFalse(content.contains("Hello"));
  }

  @Test
  void replaceAllRegexWithBackreference() throws IOException {
    Resource r = xhtml("ch1.xhtml", "<p>foo123bar</p>");
    Book book = bookWith(r);
    SearchReplace.replaceAllRegex(book, "(\\d+)", "[$1]", 0);
    String content = new String(r.getData(), StandardCharsets.UTF_8);
    assertTrue(content.contains("[123]"));
  }

  @Test
  void replaceInResourceCountsReplacements() throws IOException {
    Resource r = xhtml("ch1.xhtml", "<p>aaa bbb aaa</p>");
    int count = SearchReplace.replaceInResource(r, "aaa", "ccc", true);
    assertEquals(2, count);
    String content = new String(r.getData(), StandardCharsets.UTF_8);
    assertFalse(content.contains("aaa"));
    assertTrue(content.contains("ccc"));
  }

  @Test
  void searchMatchHasContext() {
    Book book = bookWith(xhtml("ch1.xhtml", "<p>Before TARGET After</p>"));
    SearchReplace.SearchResult result = SearchReplace.search(book, "TARGET", true);
    SearchReplace.SearchMatch match = result.matches().getFirst();
    assertTrue(match.contextBefore().contains("Before"));
    assertTrue(match.contextAfter().contains("After"));
    assertEquals(1, match.lineNumber());
  }

  @Test
  void noMatchesReturnsEmptyResult() {
    Book book = bookWith(xhtml("ch1.xhtml", "<p>Nothing here</p>"));
    SearchReplace.SearchResult result = SearchReplace.search(book, "MISSING", true);
    assertEquals(0, result.matchCount());
    assertEquals(0, result.resourcesAffected());
    assertEquals(0, result.totalReplacements());
  }

  @Test
  void replaceDoesNotModifyUnmatchedResources() throws IOException {
    Resource r1 = xhtml("ch1.xhtml", "<p>Match here</p>");
    Resource r2 = xhtml("ch2.xhtml", "<p>No match</p>");
    byte[] originalData = r2.getData().clone();
    Book book = bookWith(r1, r2);
    SearchReplace.replaceAll(book, "Match here", "Replaced", true);
    assertArrayEquals(originalData, r2.getData());
  }
}
