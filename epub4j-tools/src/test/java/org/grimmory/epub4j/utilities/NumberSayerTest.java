package org.grimmory.epub4j.utilities;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class NumberSayerTest {
  @Test
  public void test1() {
    Object[] testinput = {
      1, "one",
      42, "fourtytwo",
      127, "hundredtwentyseven",
      433, "fourhundredthirtythree"
    };
    for (int i = 0; i < testinput.length; i += 2) {
      assertEquals(testinput[i + 1], NumberSayer.getNumberName((Integer) testinput[i]));
    }
  }
}
