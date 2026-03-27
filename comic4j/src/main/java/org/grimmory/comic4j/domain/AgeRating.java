package org.grimmory.comic4j.domain;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum AgeRating {
  UNKNOWN("Unknown", null),
  ADULTS_ONLY_18_PLUS("Adults Only 18+", 18),
  EARLY_CHILDHOOD("Early Childhood", 3),
  EVERYONE("Everyone", 0),
  EVERYONE_10_PLUS("Everyone 10+", 10),
  G("G", 0),
  KIDS_TO_ADULTS("Kids to Adults", 6),
  M("M", 15),
  MA_15_PLUS("MA15+", 15),
  MATURE_17_PLUS("Mature 17+", 17),
  PG("PG", 8),
  R_18_PLUS("R18+", 18),
  RATING_PENDING("Rating Pending", null),
  TEEN("Teen", 13),
  X_18_PLUS("X18+", 18);

  private static final Map<String, AgeRating> BY_XML_VALUE =
      Stream.of(values())
          .collect(Collectors.toUnmodifiableMap(v -> v.xmlValue.toLowerCase(Locale.ROOT), v -> v));

  private final String xmlValue;
  private final Integer minimumAge;

  AgeRating(String xmlValue, Integer minimumAge) {
    this.xmlValue = xmlValue;
    this.minimumAge = minimumAge;
  }

  public String xmlValue() {
    return xmlValue;
  }

  public Integer minimumAge() {
    return minimumAge;
  }

  public static AgeRating fromXmlValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return BY_XML_VALUE.getOrDefault(value.strip().toLowerCase(Locale.ROOT), UNKNOWN);
  }
}
