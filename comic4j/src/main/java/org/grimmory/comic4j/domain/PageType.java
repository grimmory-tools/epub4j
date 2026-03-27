package org.grimmory.comic4j.domain;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PageType {
  FRONT_COVER("FrontCover"),
  INNER_COVER("InnerCover"),
  ROUNDUP("Roundup"),
  STORY("Story"),
  ADVERTISEMENT("Advertisement"),
  EDITORIAL("Editorial"),
  LETTERS("Letters"),
  PREVIEW("Preview"),
  BACK_COVER("BackCover"),
  OTHER("Other"),
  DELETED("Deleted");

  private static final Map<String, PageType> BY_XML_VALUE =
      Stream.of(values())
          .collect(Collectors.toUnmodifiableMap(v -> v.xmlValue.toLowerCase(Locale.ROOT), v -> v));

  private final String xmlValue;

  PageType(String xmlValue) {
    this.xmlValue = xmlValue;
  }

  public String xmlValue() {
    return xmlValue;
  }

  public static PageType fromXmlValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return BY_XML_VALUE.get(value.strip().toLowerCase(Locale.ROOT));
  }
}
