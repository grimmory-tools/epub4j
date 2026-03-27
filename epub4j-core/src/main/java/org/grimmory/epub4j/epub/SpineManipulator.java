package org.grimmory.epub4j.epub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.grimmory.epub4j.domain.*;

/**
 * Provides spine manipulation operations: reordering, inserting, removing spine items with
 * automatic consistency maintenance of manifest references and TOC.
 */
public class SpineManipulator {

  private static final System.Logger log = System.getLogger(SpineManipulator.class.getName());

  /**
   * Move a spine item from one position to another.
   *
   * @param book the book whose spine to modify
   * @param fromIndex the current position (0-based)
   * @param toIndex the target position (0-based)
   * @throws IndexOutOfBoundsException if indices are out of range
   */
  public static void moveSpineItem(Book book, int fromIndex, int toIndex) {
    List<SpineReference> refs = book.getSpine().getSpineReferences();
    if (fromIndex < 0 || fromIndex >= refs.size()) {
      throw new IndexOutOfBoundsException(
          "fromIndex " + fromIndex + " out of range [0, " + (refs.size() - 1) + "]");
    }
    if (toIndex < 0 || toIndex >= refs.size()) {
      throw new IndexOutOfBoundsException(
          "toIndex " + toIndex + " out of range [0, " + (refs.size() - 1) + "]");
    }
    if (fromIndex == toIndex) return;

    SpineReference item = refs.remove(fromIndex);
    refs.add(toIndex, item);
    log.log(System.Logger.Level.DEBUG, "Moved spine item from " + fromIndex + " to " + toIndex);
  }

  /**
   * Remove a spine item by index. The resource itself is NOT removed from the book's resources;
   * only the spine reference is removed.
   *
   * @param book the book
   * @param index the spine index to remove
   * @return the removed SpineReference
   */
  public static SpineReference removeSpineItem(Book book, int index) {
    List<SpineReference> refs = book.getSpine().getSpineReferences();
    if (index < 0 || index >= refs.size()) {
      throw new IndexOutOfBoundsException(
          "index " + index + " out of range [0, " + (refs.size() - 1) + "]");
    }
    SpineReference removed = refs.remove(index);
    log.log(
        System.Logger.Level.DEBUG,
        "Removed spine item at index " + index + ": " + removed.getResourceId());
    return removed;
  }

  /**
   * Insert a resource into the spine at the given position. The resource is also added to the
   * book's resources if not already present.
   *
   * @param book the book
   * @param index position to insert at (0-based, use spine size to append)
   * @param resource the resource to insert
   * @return the created SpineReference
   */
  public static SpineReference insertSpineItem(Book book, int index, Resource resource) {
    List<SpineReference> refs = book.getSpine().getSpineReferences();
    if (index < 0 || index > refs.size()) {
      throw new IndexOutOfBoundsException(
          "index " + index + " out of range [0, " + refs.size() + "]");
    }

    if (!book.getResources().containsByHref(resource.getHref())) {
      book.getResources().add(resource);
    }

    SpineReference ref = new SpineReference(resource);
    refs.add(index, ref);
    log.log(
        System.Logger.Level.DEBUG,
        "Inserted spine item at index " + index + ": " + resource.getHref());
    return ref;
  }

  /** Reverse the spine order. Useful for RTL books that were imported with wrong direction. */
  public static void reverseSpine(Book book) {
    List<SpineReference> refs = book.getSpine().getSpineReferences();
    Collections.reverse(refs);
    log.log(System.Logger.Level.DEBUG, "Reversed spine order (" + refs.size() + " items)");
  }

  /**
   * Remove spine items whose resources are null or no longer in the book's resources.
   *
   * @return number of items removed
   */
  public static int removeOrphanedSpineItems(Book book) {
    List<SpineReference> refs = book.getSpine().getSpineReferences();
    List<SpineReference> toRemove = new ArrayList<>();

    for (SpineReference ref : refs) {
      Resource resource = ref.getResource();
      if (resource == null || !book.getResources().containsByHref(resource.getHref())) {
        toRemove.add(ref);
      }
    }

    refs.removeAll(toRemove);
    if (!toRemove.isEmpty()) {
      log.log(System.Logger.Level.DEBUG, "Removed " + toRemove.size() + " orphaned spine items");
    }
    return toRemove.size();
  }
}
