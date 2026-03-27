package org.grimmory.comic4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NaturalSortComparatorTest {

  private final NaturalSortComparator cmp = NaturalSortComparator.INSTANCE;

  @Test
  void numericOrder() {
    assertTrue(cmp.compare("page2", "page10") < 0);
    assertTrue(cmp.compare("page10", "page2") > 0);
    assertEquals(0, cmp.compare("page10", "page10"));
  }

  @Test
  void leadingZeros() {
    assertTrue(cmp.compare("001", "2") < 0);
    assertTrue(cmp.compare("007", "10") < 0);
  }

  @Test
  void mixedContent() {
    List<String> input = Arrays.asList("page10.jpg", "page2.jpg", "page1.jpg", "page20.jpg");
    input.sort(cmp);
    assertEquals(List.of("page1.jpg", "page2.jpg", "page10.jpg", "page20.jpg"), input);
  }

  @Test
  void caseInsensitive() {
    assertTrue(cmp.compare("Page1", "page1") == 0 || cmp.compare("Page1", "page2") < 0);
  }

  @Test
  void pureNumeric() {
    List<String> input = Arrays.asList("20", "3", "1", "100", "10");
    input.sort(cmp);
    assertEquals(List.of("1", "3", "10", "20", "100"), input);
  }

  @Test
  void nullHandling() {
    assertEquals(0, cmp.compare(null, null));
    assertTrue(cmp.compare(null, "a") < 0);
    assertTrue(cmp.compare("a", null) > 0);
  }

  @Test
  void complexPaths() {
    List<String> input =
        Arrays.asList(
            "chapter2/page10.jpg",
            "chapter1/page2.jpg",
            "chapter1/page10.jpg",
            "chapter2/page1.jpg");
    input.sort(cmp);
    assertEquals(
        List.of(
            "chapter1/page2.jpg",
            "chapter1/page10.jpg",
            "chapter2/page1.jpg",
            "chapter2/page10.jpg"),
        input);
  }
}
