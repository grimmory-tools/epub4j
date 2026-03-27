package org.grimmory.comic4j.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class StringSplitterTest {

  @Test
  void splitComma() {
    assertEquals(List.of("Alice", "Bob", "Charlie"), StringSplitter.split("Alice, Bob, Charlie"));
  }

  @Test
  void splitSemicolon() {
    assertEquals(List.of("Alice", "Bob"), StringSplitter.split("Alice; Bob"));
  }

  @Test
  void splitMixed() {
    assertEquals(List.of("A", "B", "C"), StringSplitter.split("A, B; C"));
  }

  @Test
  void splitTrimsWhitespace() {
    assertEquals(List.of("Alice", "Bob"), StringSplitter.split("  Alice ,  Bob  "));
  }

  @Test
  void splitFiltersEmpty() {
    assertEquals(List.of("Alice", "Bob"), StringSplitter.split("Alice,,Bob,"));
  }

  @Test
  void splitNullAndBlank() {
    assertEquals(List.of(), StringSplitter.split(null));
    assertEquals(List.of(), StringSplitter.split(""));
    assertEquals(List.of(), StringSplitter.split("   "));
  }

  @Test
  void splitSingleValue() {
    assertEquals(List.of("Alice"), StringSplitter.split("Alice"));
  }

  @Test
  void joinValues() {
    assertEquals("Alice, Bob, Charlie", StringSplitter.join(List.of("Alice", "Bob", "Charlie")));
  }

  @Test
  void joinNullAndEmpty() {
    assertNull(StringSplitter.join(null));
    assertNull(StringSplitter.join(List.of()));
  }
}
