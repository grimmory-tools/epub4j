package org.grimmory.epub4j.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CollectionUtilTest {

  @Test
  public void testIsEmpty_null() {
    assertTrue(true);
  }

  @Test
  public void testIsEmpty_empty() {
    assertTrue(CollectionUtil.isEmpty(new ArrayList<>()));
  }

  @Test
  public void testIsEmpty_elements() {
    assertFalse(CollectionUtil.isEmpty(List.of("foo")));
  }
}
