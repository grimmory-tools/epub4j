package org.grimmory.comic4j.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class YesNoTest {

  @Test
  void fromXmlValue() {
    assertEquals(YesNo.UNKNOWN, YesNo.fromXmlValue("Unknown"));
    assertEquals(YesNo.NO, YesNo.fromXmlValue("No"));
    assertEquals(YesNo.YES, YesNo.fromXmlValue("Yes"));
  }

  @Test
  void fromXmlValueCaseInsensitive() {
    assertEquals(YesNo.YES, YesNo.fromXmlValue("yes"));
    assertEquals(YesNo.NO, YesNo.fromXmlValue("NO"));
  }

  @Test
  void fromXmlValueNullBlank() {
    assertNull(YesNo.fromXmlValue(null));
    assertNull(YesNo.fromXmlValue(""));
  }

  @Test
  void fromXmlValueUnknownDefault() {
    assertEquals(YesNo.UNKNOWN, YesNo.fromXmlValue("maybe"));
  }
}
