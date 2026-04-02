/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.archive;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comparator that sorts strings in natural order: numeric segments are compared by value rather
 * than lexicographically. This ensures "page2" comes before "page10".
 */
public final class NaturalSortComparator implements Comparator<String> {

  public static final NaturalSortComparator INSTANCE = new NaturalSortComparator();

  private static final Pattern SEGMENT_PATTERN = Pattern.compile("(\\d+)|(\\D+)");

  private NaturalSortComparator() {}

  @Override
  public int compare(String a, String b) {
    if (a == null && b == null) return 0;
    if (a == null) return -1;
    if (b == null) return 1;

    Matcher matcherA = SEGMENT_PATTERN.matcher(a);
    Matcher matcherB = SEGMENT_PATTERN.matcher(b);

    while (matcherA.find() && matcherB.find()) {
      String segA = matcherA.group();
      String segB = matcherB.group();

      int result;
      if (matcherA.group(1) != null && matcherB.group(1) != null) {
        // Both segments are numeric, so compare by value
        result = compareNumeric(segA, segB);
      } else {
        // At least one segment is non-numeric, so compare case-insensitively
        result = segA.compareToIgnoreCase(segB);
      }

      if (result != 0) {
        return result;
      }
    }

    // The string with remaining segments comes after
    return a.length() - b.length();
  }

  private static int compareNumeric(String a, String b) {
    // Strip leading zeros for comparison, but use length as tiebreaker
    // (so "007" and "7" are equal in value but "007" sorts stably)
    try {
      long numA = Long.parseLong(a);
      long numB = Long.parseLong(b);
      int cmp = Long.compare(numA, numB);
      return cmp != 0 ? cmp : Integer.compare(a.length(), b.length());
    } catch (NumberFormatException e) {
      // Fallback for numbers exceeding long range
      return a.compareTo(b);
    }
  }
}
