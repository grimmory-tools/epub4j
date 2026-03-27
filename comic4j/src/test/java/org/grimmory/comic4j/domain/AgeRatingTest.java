package org.grimmory.comic4j.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgeRatingTest {

  @ParameterizedTest
  @CsvSource({
    "Unknown, UNKNOWN",
    "Adults Only 18+, ADULTS_ONLY_18_PLUS",
    "Early Childhood, EARLY_CHILDHOOD",
    "Everyone, EVERYONE",
    "Everyone 10+, EVERYONE_10_PLUS",
    "G, G",
    "Kids to Adults, KIDS_TO_ADULTS",
    "M, M",
    "MA15+, MA_15_PLUS",
    "Mature 17+, MATURE_17_PLUS",
    "PG, PG",
    "R18+, R_18_PLUS",
    "Rating Pending, RATING_PENDING",
    "Teen, TEEN",
    "X18+, X_18_PLUS"
  })
  void fromXmlValue(String xmlValue, AgeRating expected) {
    assertEquals(expected, AgeRating.fromXmlValue(xmlValue));
  }

  @Test
  void fromXmlValueCaseInsensitive() {
    assertEquals(AgeRating.TEEN, AgeRating.fromXmlValue("teen"));
    assertEquals(AgeRating.TEEN, AgeRating.fromXmlValue("TEEN"));
    assertEquals(AgeRating.MA_15_PLUS, AgeRating.fromXmlValue(" ma15+ "));
  }

  @Test
  void fromXmlValueNullBlank() {
    assertNull(AgeRating.fromXmlValue(null));
    assertNull(AgeRating.fromXmlValue(""));
  }

  @Test
  void minimumAgeValues() {
    assertNull(AgeRating.UNKNOWN.minimumAge());
    assertEquals(0, AgeRating.EVERYONE.minimumAge());
    assertEquals(13, AgeRating.TEEN.minimumAge());
    assertEquals(18, AgeRating.ADULTS_ONLY_18_PLUS.minimumAge());
    assertNull(AgeRating.RATING_PENDING.minimumAge());
  }

  @Test
  void xmlValueRoundTrip() {
    for (AgeRating rating : AgeRating.values()) {
      assertEquals(rating, AgeRating.fromXmlValue(rating.xmlValue()));
    }
  }
}
