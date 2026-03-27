package org.grimmory.comic4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EntryFilterTest {

  @Test
  void validEntries() {
    assertTrue(EntryFilter.isContentEntry("page01.jpg"));
    assertTrue(EntryFilter.isContentEntry("Chapter 1/page01.jpg"));
    assertTrue(EntryFilter.isContentEntry("ComicInfo.xml"));
  }

  @Test
  void nullAndEmpty() {
    assertFalse(EntryFilter.isContentEntry(null));
    assertFalse(EntryFilter.isContentEntry(""));
    assertFalse(EntryFilter.isContentEntry("   "));
  }

  @Test
  void macosResourceForks() {
    assertFalse(EntryFilter.isContentEntry("__MACOSX/"));
    assertFalse(EntryFilter.isContentEntry("__MACOSX/._page01.jpg"));
    assertFalse(EntryFilter.isContentEntry("__MACOSX"));
  }

  @Test
  void dotFiles() {
    assertFalse(EntryFilter.isContentEntry("._page01.jpg"));
    assertFalse(EntryFilter.isContentEntry(".hidden"));
    assertFalse(EntryFilter.isContentEntry("folder/.hidden"));
  }

  @Test
  void systemFiles() {
    assertFalse(EntryFilter.isContentEntry(".DS_Store"));
    assertFalse(EntryFilter.isContentEntry("Thumbs.db"));
    assertFalse(EntryFilter.isContentEntry("thumbs.db"));
    assertFalse(EntryFilter.isContentEntry("desktop.ini"));
    assertFalse(EntryFilter.isContentEntry("Desktop.ini"));
  }

  @Test
  void pathTraversal() {
    assertFalse(EntryFilter.isContentEntry("../../../etc/passwd"));
    assertFalse(EntryFilter.isContentEntry("folder/../secret.txt"));
  }

  @Test
  void directoryEntries() {
    assertFalse(EntryFilter.isContentEntry("folder/"));
    assertFalse(EntryFilter.isContentEntry("folder/subfolder/"));
  }
}
