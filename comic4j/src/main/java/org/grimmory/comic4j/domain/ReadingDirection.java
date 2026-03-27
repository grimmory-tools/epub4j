package org.grimmory.comic4j.domain;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ReadingDirection {
  UNKNOWN("Unknown"),
  LEFT_TO_RIGHT("No"),
  RIGHT_TO_LEFT("Yes"),
  RIGHT_TO_LEFT_MANGA("YesAndRightToLeft");

  private static final Map<String, ReadingDirection> BY_XML_VALUE =
      Stream.of(values())
          .collect(Collectors.toUnmodifiableMap(v -> v.xmlValue.toLowerCase(Locale.ROOT), v -> v));

  private final String xmlValue;

  ReadingDirection(String xmlValue) {
    this.xmlValue = xmlValue;
  }

  public String xmlValue() {
    return xmlValue;
  }

  public static ReadingDirection fromXmlValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return BY_XML_VALUE.getOrDefault(value.strip().toLowerCase(Locale.ROOT), UNKNOWN);
  }
}
