package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class SpineManipulatorTest {

  @Test
  void moveSpineItemForward() {
    Book book = createBookWithSpine(3);
    Resource first = book.getSpine().getResource(0);
    SpineManipulator.moveSpineItem(book, 0, 2);
    assertEquals(first, book.getSpine().getResource(2));
  }

  @Test
  void moveSpineItemBackward() {
    Book book = createBookWithSpine(3);
    Resource last = book.getSpine().getResource(2);
    SpineManipulator.moveSpineItem(book, 2, 0);
    assertEquals(last, book.getSpine().getResource(0));
  }

  @Test
  void moveSameIndexIsNoOp() {
    Book book = createBookWithSpine(3);
    Resource r = book.getSpine().getResource(1);
    SpineManipulator.moveSpineItem(book, 1, 1);
    assertEquals(r, book.getSpine().getResource(1));
  }

  @Test
  void moveThrowsOnInvalidFromIndex() {
    Book book = createBookWithSpine(2);
    assertThrows(
        IndexOutOfBoundsException.class, () -> SpineManipulator.moveSpineItem(book, -1, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> SpineManipulator.moveSpineItem(book, 5, 0));
  }

  @Test
  void moveThrowsOnInvalidToIndex() {
    Book book = createBookWithSpine(2);
    assertThrows(
        IndexOutOfBoundsException.class, () -> SpineManipulator.moveSpineItem(book, 0, -1));
    assertThrows(IndexOutOfBoundsException.class, () -> SpineManipulator.moveSpineItem(book, 0, 5));
  }

  @Test
  void removeSpineItem() {
    Book book = createBookWithSpine(3);
    Resource middle = book.getSpine().getResource(1);
    SpineReference removed = SpineManipulator.removeSpineItem(book, 1);
    assertEquals(middle, removed.getResource());
    assertEquals(2, book.getSpine().size());
  }

  @Test
  void removeThrowsOnInvalidIndex() {
    Book book = createBookWithSpine(2);
    assertThrows(IndexOutOfBoundsException.class, () -> SpineManipulator.removeSpineItem(book, -1));
    assertThrows(IndexOutOfBoundsException.class, () -> SpineManipulator.removeSpineItem(book, 5));
  }

  @Test
  void insertSpineItem() {
    Book book = createBookWithSpine(2);
    Resource newRes = new Resource("new", "data".getBytes(), "new.xhtml", MediaTypes.XHTML);

    SpineReference ref = SpineManipulator.insertSpineItem(book, 1, newRes);
    assertEquals(3, book.getSpine().size());
    assertEquals(newRes, book.getSpine().getResource(1));
    assertNotNull(ref);
  }

  @Test
  void insertAddsResourceToBookIfNotPresent() {
    Book book = createBookWithSpine(1);
    Resource newRes = new Resource("new", "data".getBytes(), "added.xhtml", MediaTypes.XHTML);
    assertFalse(book.getResources().containsByHref("added.xhtml"));

    SpineManipulator.insertSpineItem(book, 1, newRes);
    assertTrue(book.getResources().containsByHref("added.xhtml"));
  }

  @Test
  void insertAtEndAppends() {
    Book book = createBookWithSpine(2);
    Resource newRes = new Resource("new", "data".getBytes(), "last.xhtml", MediaTypes.XHTML);
    SpineManipulator.insertSpineItem(book, 2, newRes);
    assertEquals(newRes, book.getSpine().getResource(2));
  }

  @Test
  void insertThrowsOnInvalidIndex() {
    Book book = createBookWithSpine(2);
    Resource r = new Resource("x", "d".getBytes(), "x.xhtml", MediaTypes.XHTML);
    assertThrows(
        IndexOutOfBoundsException.class, () -> SpineManipulator.insertSpineItem(book, -1, r));
    assertThrows(
        IndexOutOfBoundsException.class, () -> SpineManipulator.insertSpineItem(book, 10, r));
  }

  @Test
  void reverseSpine() {
    Book book = createBookWithSpine(3);
    Resource first = book.getSpine().getResource(0);
    Resource last = book.getSpine().getResource(2);
    SpineManipulator.reverseSpine(book);
    assertEquals(last, book.getSpine().getResource(0));
    assertEquals(first, book.getSpine().getResource(2));
  }

  @Test
  void removeOrphanedSpineItems() {
    Book book = createBookWithSpine(2);
    // Add a spine ref with null resource
    book.getSpine().getSpineReferences().add(new SpineReference(null));
    assertEquals(3, book.getSpine().size());

    int removed = SpineManipulator.removeOrphanedSpineItems(book);
    assertEquals(1, removed);
    assertEquals(2, book.getSpine().size());
  }

  @Test
  void removeOrphanedReturnsZeroWhenClean() {
    Book book = createBookWithSpine(3);
    assertEquals(0, SpineManipulator.removeOrphanedSpineItems(book));
  }

  private static Book createBookWithSpine(int count) {
    Book book = new Book();
    for (int i = 0; i < count; i++) {
      Resource res =
          new Resource("id" + i, ("content" + i).getBytes(), "ch" + i + ".xhtml", MediaTypes.XHTML);
      book.getResources().add(res);
      book.getSpine().addResource(res);
    }
    return book;
  }
}
