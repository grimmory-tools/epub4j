package org.grimmory.comic4j.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageTypeTest {

  @ParameterizedTest
  @CsvSource({
    "FrontCover, FRONT_COVER",
    "InnerCover, INNER_COVER",
    "Roundup, ROUNDUP",
    "Story, STORY",
    "Advertisement, ADVERTISEMENT",
    "Editorial, EDITORIAL",
    "Letters, LETTERS",
    "Preview, PREVIEW",
    "BackCover, BACK_COVER",
    "Other, OTHER",
    "Deleted, DELETED"
  })
  void fromXmlValue(String xmlValue, PageType expected) {
    assertEquals(expected, PageType.fromXmlValue(xmlValue));
  }

  @Test
  void fromXmlValueCaseInsensitive() {
    assertEquals(PageType.FRONT_COVER, PageType.fromXmlValue("frontcover"));
    assertEquals(PageType.FRONT_COVER, PageType.fromXmlValue("FRONTCOVER"));
    assertEquals(PageType.FRONT_COVER, PageType.fromXmlValue(" FrontCover "));
  }

  @Test
  void fromXmlValueNull() {
    assertNull(PageType.fromXmlValue(null));
    assertNull(PageType.fromXmlValue(""));
    assertNull(PageType.fromXmlValue("   "));
  }

  @Test
  void fromXmlValueUnknown() {
    assertNull(PageType.fromXmlValue("NonExistent"));
  }

  @Test
  void xmlValueRoundTrip() {
    for (PageType type : PageType.values()) {
      assertEquals(type, PageType.fromXmlValue(type.xmlValue()));
    }
  }
}
