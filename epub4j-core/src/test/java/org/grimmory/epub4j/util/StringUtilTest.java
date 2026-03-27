package org.grimmory.epub4j.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StringUtilTest {

  @Test
  public void testDefaultIfNull() {
    Object[] testData = {null, "", "", "", " ", " ", "foo", "foo"};
    for (int i = 0; i < testData.length; i += 2) {
      String actualResult = StringUtil.defaultIfNull((String) testData[i]);
      String expectedResult = (String) testData[i + 1];
      assertEquals(expectedResult, actualResult, (i / 2) + " : " + testData[i]);
    }
  }

  @Test
  public void testDefaultIfNull_with_default() {
    Object[] testData = {
      null, null, null, "", null, "", null, "", "", "foo", "", "foo", "", "foo", "", " ", " ", " ",
      null, "foo", "foo",
    };
    for (int i = 0; i < testData.length; i += 3) {
      String actualResult =
          StringUtil.defaultIfNull((String) testData[i], (String) testData[i + 1]);
      String expectedResult = (String) testData[i + 2];
      assertEquals(
          expectedResult, actualResult, (i / 3) + " : " + testData[i] + ", " + testData[i + 1]);
    }
  }

  @Test
  public void testIsEmpty() {
    Object[] testData = {null, true, "", true, " ", false, "asdfasfd", false};
    for (int i = 0; i < testData.length; i += 2) {
      boolean actualResult = StringUtil.isEmpty((String) testData[i]);
      boolean expectedResult = (Boolean) testData[i + 1];
      assertEquals(expectedResult, actualResult);
    }
  }

  @Test
  public void testIsBlank() {
    Object[] testData = {null, true, "", true, " ", true, "\t\t \n\n", true, "asdfasfd", false};
    for (int i = 0; i < testData.length; i += 2) {
      boolean actualResult = StringUtil.isBlank((String) testData[i]);
      boolean expectedResult = (Boolean) testData[i + 1];
      assertEquals(expectedResult, actualResult);
    }
  }

  @Test
  public void testIsNotBlank() {
    Object[] testData = {null, false, "", false, " ", false, "\t\t \n\n", false, "asdfasfd", true};
    for (int i = 0; i < testData.length; i += 2) {
      boolean actualResult = StringUtil.isNotBlank((String) testData[i]);
      boolean expectedResult = (Boolean) testData[i + 1];
      assertEquals(expectedResult, actualResult, (i / 2) + " : " + testData[i]);
    }
  }

  @Test
  public void testEquals() {
    Object[] testData = {
      null, null, true, "", "", true, null, "", false, "", null, false, null, "foo", false, "foo",
      null, false, "", "foo", false, "foo", "", false, "foo", "bar", false, "foo", "foo", true
    };
    for (int i = 0; i < testData.length; i += 3) {
      boolean actualResult = StringUtil.equals((String) testData[i], (String) testData[i + 1]);
      boolean expectedResult = (Boolean) testData[i + 2];
      assertEquals(
          expectedResult, actualResult, (i / 3) + " : " + testData[i] + ", " + testData[i + 1]);
    }
  }

  @Test
  public void testEndWithIgnoreCase() {
    Object[] testData = {
      null,
      null,
      true,
      "",
      "",
      true,
      "",
      "foo",
      false,
      "foo",
      "foo",
      true,
      "foo.bar",
      "bar",
      true,
      "foo.bar",
      "barX",
      false,
      "foo.barX",
      "bar",
      false,
      "foo",
      "bar",
      false,
      "foo.BAR",
      "bar",
      true,
      "foo.bar",
      "BaR",
      true
    };
    for (int i = 0; i < testData.length; i += 3) {
      boolean actualResult =
          StringUtil.endsWithIgnoreCase((String) testData[i], (String) testData[i + 1]);
      boolean expectedResult = (Boolean) testData[i + 2];
      assertEquals(
          expectedResult, actualResult, (i / 3) + " : " + testData[i] + ", " + testData[i + 1]);
    }
  }

  @Test
  public void testSubstringBefore() {
    Object[] testData = {
      "", ' ', "", "", 'X', "", "fox", 'x', "fo", "foo.bar", 'b', "foo.", "aXbXc", 'X', "a",
    };
    for (int i = 0; i < testData.length; i += 3) {
      String actualResult =
          StringUtil.substringBefore((String) testData[i], (Character) testData[i + 1]);
      String expectedResult = (String) testData[i + 2];
      assertEquals(
          expectedResult, actualResult, (i / 3) + " : " + testData[i] + ", " + testData[i + 1]);
    }
  }

  @Test
  public void testSubstringBeforeLast() {
    Object[] testData = {
      "", ' ', "", "", 'X', "", "fox", 'x', "fo", "foo.bar", 'b', "foo.", "aXbXc", 'X', "aXb",
    };
    for (int i = 0; i < testData.length; i += 3) {
      String actualResult =
          StringUtil.substringBeforeLast((String) testData[i], (Character) testData[i + 1]);
      String expectedResult = (String) testData[i + 2];
      assertEquals(
          expectedResult, actualResult, (i / 3) + " : " + testData[i] + ", " + testData[i + 1]);
    }
  }

  @Test
  public void testSubstringAfter() {
    Object[] testData = {
      "", ' ', "", "", 'X', "", "fox", 'f', "ox", "foo.bar", 'b', "ar", "aXbXc", 'X', "bXc",
    };
    for (int i = 0; i < testData.length; i += 3) {
      String actualResult =
          StringUtil.substringAfter((String) testData[i], (Character) testData[i + 1]);
      String expectedResult = (String) testData[i + 2];
      assertEquals(
          expectedResult, actualResult, (i / 3) + " : " + testData[i] + ", " + testData[i + 1]);
    }
  }

  @Test
  public void testSubstringAfterLast() {
    Object[] testData = {
      "", ' ', "", "", 'X', "", "fox", 'f', "ox", "foo.bar", 'b', "ar", "aXbXc", 'X', "c",
    };
    for (int i = 0; i < testData.length; i += 3) {
      String actualResult =
          StringUtil.substringAfterLast((String) testData[i], (Character) testData[i + 1]);
      String expectedResult = (String) testData[i + 2];
      assertEquals(
          expectedResult, actualResult, (i / 3) + " : " + testData[i] + ", " + testData[i + 1]);
    }
  }

  @Test
  public void testToString() {
    assertEquals("[name: 'paul']", StringUtil.toString("name", "paul"));
    assertEquals(
        "[name: 'paul', address: 'a street']",
        StringUtil.toString("name", "paul", "address", "a street"));
    assertEquals("[name: <null>]", StringUtil.toString("name", null));
    assertEquals("[name: 'paul', address: <null>]", StringUtil.toString("name", "paul", "address"));
  }

  @Test
  public void testHashCode() {
    assertEquals(2522795, StringUtil.hashCode("isbn", "1234"));
    assertEquals(3499691, StringUtil.hashCode("ISBN", "1234"));
  }

  @Test
  public void testReplacementForCollapsePathDots() {
    String[] testData = {
      "/foo/bar.html", "/foo/bar.html",
      "/foo/../bar.html", "/bar.html",
      "/foo/moo/../../bar.html", "/bar.html",
      "/foo//bar.html", "/foo/bar.html",
      "/foo/./bar.html", "/foo/bar.html",
      "/a/b/../../c", "/c",
      "/foo/../sub/bar.html", "/sub/bar.html"
    };
    for (int i = 0; i < testData.length; i += 2) {
      String actualResult = StringUtil.collapsePathDots(testData[i]);
      assertEquals(testData[i + 1], actualResult, testData[i]);
    }
  }
}
