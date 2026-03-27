package org.grimmory.comic4j.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReadingDirectionTest {

  @Test
  void fromXmlValueStandard() {
    assertEquals(ReadingDirection.UNKNOWN, ReadingDirection.fromXmlValue("Unknown"));
    assertEquals(ReadingDirection.LEFT_TO_RIGHT, ReadingDirection.fromXmlValue("No"));
    assertEquals(ReadingDirection.RIGHT_TO_LEFT, ReadingDirection.fromXmlValue("Yes"));
    assertEquals(
        ReadingDirection.RIGHT_TO_LEFT_MANGA, ReadingDirection.fromXmlValue("YesAndRightToLeft"));
  }

  @Test
  void fromXmlValueCaseInsensitive() {
    assertEquals(
        ReadingDirection.RIGHT_TO_LEFT_MANGA, ReadingDirection.fromXmlValue("yesandrighttoleft"));
    assertEquals(ReadingDirection.LEFT_TO_RIGHT, ReadingDirection.fromXmlValue("NO"));
  }

  @Test
  void fromXmlValueNullBlank() {
    assertNull(ReadingDirection.fromXmlValue(null));
    assertNull(ReadingDirection.fromXmlValue(""));
    assertNull(ReadingDirection.fromXmlValue("  "));
  }

  @Test
  void fromXmlValueUnknownDefault() {
    assertEquals(ReadingDirection.UNKNOWN, ReadingDirection.fromXmlValue("garbage"));
  }

  @Test
  void xmlValueRoundTrip() {
    for (ReadingDirection dir : ReadingDirection.values()) {
      assertEquals(dir, ReadingDirection.fromXmlValue(dir.xmlValue()));
    }
  }
}
