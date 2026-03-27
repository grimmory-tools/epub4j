package org.grimmory.comic4j.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility for splitting comma/semicolon-delimited values commonly found in ComicInfo.xml fields
 * (creators, genres, tags, etc.).
 */
public final class StringSplitter {

  private static final Pattern COMMA_SEMICOLON = Pattern.compile("[,;]");

  private StringSplitter() {}

  /**
   * Splits a comma/semicolon-delimited string into trimmed, non-empty values. Returns an empty list
   * for null or blank input.
   */
  public static List<String> split(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    String[] parts = COMMA_SEMICOLON.split(value);
    List<String> result = new ArrayList<>(parts.length);
    for (String part : parts) {
      String trimmed = part.strip();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return List.copyOf(result);
  }

  /** Joins a list of values with comma-space separator. Returns null for null or empty lists. */
  public static String join(List<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return String.join(", ", values);
  }
}
