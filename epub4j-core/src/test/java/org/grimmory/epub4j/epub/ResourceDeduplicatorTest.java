package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class ResourceDeduplicatorTest {

  private static Resource xhtml(String href, String content) {
    return new Resource(null, content.getBytes(StandardCharsets.UTF_8), href, MediaTypes.XHTML);
  }

  @Test
  void noDuplicatesReturnsZero() {
    Book book = new Book();
    book.getResources().add(xhtml("ch1.xhtml", "<p>Chapter 1</p>"));
    book.getResources().add(xhtml("ch2.xhtml", "<p>Chapter 2</p>"));
    ResourceDeduplicator.DeduplicationResult result = ResourceDeduplicator.deduplicate(book);
    assertEquals(0, result.duplicateGroupCount());
    assertEquals(0, result.resourcesRemoved());
  }

  @Test
  void removesDuplicateResources() {
    String content = "<p>Same content</p>";
    Book book = new Book();
    book.getResources().add(xhtml("ch1.xhtml", content));
    book.getResources().add(xhtml("ch2.xhtml", content));
    ResourceDeduplicator.DeduplicationResult result = ResourceDeduplicator.deduplicate(book);
    assertEquals(1, result.duplicateGroupCount());
    assertEquals(1, result.resourcesRemoved());
    assertTrue(result.bytesSaved() > 0);
    // ch1 should remain (alphabetically first), ch2 removed
    assertNotNull(book.getResources().getByHref("ch1.xhtml"));
    assertNull(book.getResources().getByHref("ch2.xhtml"));
  }

  @Test
  void updatesSpineReferences() {
    String content = "<p>Shared</p>";
    Resource r1 = xhtml("ch1.xhtml", content);
    Resource r2 = xhtml("ch2.xhtml", content);
    Book book = new Book();
    book.getResources().add(r1);
    book.getResources().add(r2);
    book.getSpine().addSpineReference(new SpineReference(r2));

    ResourceDeduplicator.deduplicate(book);
    // Spine reference for r2 should now point to r1
    assertSame(r1, book.getSpine().getResource(0));
  }

  @Test
  void updatesTocReferences() {
    String content = "<p>Same</p>";
    Resource r1 = xhtml("a.xhtml", content);
    Resource r2 = xhtml("b.xhtml", content);
    Book book = new Book();
    book.getResources().add(r1);
    book.getResources().add(r2);
    book.getTableOfContents().addTOCReference(new TOCReference("ch", r2));

    ResourceDeduplicator.deduplicate(book);
    assertSame(r1, book.getTableOfContents().getTocReferences().getFirst().getResource());
  }

  @Test
  void findDuplicatesReturnsGroupsWithoutModifying() {
    String content = "<p>Duplicate</p>";
    Book book = new Book();
    book.getResources().add(xhtml("a.xhtml", content));
    book.getResources().add(xhtml("b.xhtml", content));
    book.getResources().add(xhtml("c.xhtml", "<p>Unique</p>"));

    Map<String, List<String>> dupes = ResourceDeduplicator.findDuplicates(book);
    assertEquals(1, dupes.size());
    List<String> hrefs = dupes.values().iterator().next();
    assertEquals(2, hrefs.size());
    // Both resources still exist (not modified)
    assertNotNull(book.getResources().getByHref("a.xhtml"));
    assertNotNull(book.getResources().getByHref("b.xhtml"));
  }

  @Test
  void handlesTripleDuplicates() {
    String content = "<p>Triple</p>";
    Book book = new Book();
    book.getResources().add(xhtml("a.xhtml", content));
    book.getResources().add(xhtml("b.xhtml", content));
    book.getResources().add(xhtml("c.xhtml", content));
    ResourceDeduplicator.DeduplicationResult result = ResourceDeduplicator.deduplicate(book);
    assertEquals(1, result.duplicateGroupCount());
    assertEquals(2, result.resourcesRemoved());
    assertNotNull(book.getResources().getByHref("a.xhtml"));
    assertNull(book.getResources().getByHref("b.xhtml"));
    assertNull(book.getResources().getByHref("c.xhtml"));
  }

  @Test
  void emptyBookReturnsZero() {
    Book book = new Book();
    ResourceDeduplicator.DeduplicationResult result = ResourceDeduplicator.deduplicate(book);
    assertEquals(0, result.duplicateGroupCount());
    assertEquals(0, result.resourcesRemoved());
    assertEquals(0, result.bytesSaved());
  }

  @Test
  void multipleGroupsCountedSeparately() {
    Book book = new Book();
    book.getResources().add(xhtml("a1.xhtml", "<p>Group A</p>"));
    book.getResources().add(xhtml("a2.xhtml", "<p>Group A</p>"));
    book.getResources().add(xhtml("b1.xhtml", "<p>Group B</p>"));
    book.getResources().add(xhtml("b2.xhtml", "<p>Group B</p>"));
    ResourceDeduplicator.DeduplicationResult result = ResourceDeduplicator.deduplicate(book);
    assertEquals(2, result.duplicateGroupCount());
    assertEquals(2, result.resourcesRemoved());
  }
}
