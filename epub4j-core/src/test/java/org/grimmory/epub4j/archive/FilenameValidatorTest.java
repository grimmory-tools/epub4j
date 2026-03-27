package org.grimmory.epub4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class FilenameValidatorTest {

  @Test
  void testNormalPathIsSafe() {
    assertTrue(FilenameValidator.isSafeEntryName("OEBPS/chapter1.html"));
    assertTrue(FilenameValidator.isSafeEntryName("META-INF/container.xml"));
    assertTrue(FilenameValidator.isSafeEntryName("mimetype"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../etc/passwd",
        "OEBPS/../../etc/shadow",
        "..\\windows\\system32",
        "OEBPS/images/../../secret.txt"
      })
  void testPathTraversalRejected(String path) {
    assertFalse(FilenameValidator.isSafeEntryName(path));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/etc/passwd",
        "\\windows\\system32",
        "C:\\Users\\victim\\file.txt",
        "D:\\secret.txt"
      })
  void testAbsolutePathRejected(String path) {
    assertFalse(FilenameValidator.isSafeEntryName(path));
  }

  @Test
  void testNullAndEmptyRejected() {
    assertFalse(FilenameValidator.isSafeEntryName(null));
    assertFalse(FilenameValidator.isSafeEntryName(""));
  }

  @Test
  void testSanitizeRemovesTraversal() {
    assertEquals("etc/passwd", FilenameValidator.sanitizeEntryName("../etc/passwd"));
    assertEquals("file.txt", FilenameValidator.sanitizeEntryName("../../file.txt"));
  }

  @Test
  void testSanitizeStripsAbsolutePath() {
    assertEquals("etc/passwd", FilenameValidator.sanitizeEntryName("/etc/passwd"));
  }

  @Test
  void testSanitizeStripsDriveLetter() {
    assertEquals("Users/file.txt", FilenameValidator.sanitizeEntryName("C:\\Users\\file.txt"));
  }

  @Test
  void testSanitizeNormalizesBackslashes() {
    assertEquals(
        "OEBPS/images/cover.png", FilenameValidator.sanitizeEntryName("OEBPS\\images\\cover.png"));
  }

  @Test
  void testSanitizeReturnsNullForUnsalvageable() {
    assertNull(FilenameValidator.sanitizeEntryName(""));
    assertNull(FilenameValidator.sanitizeEntryName(null));
    assertNull(FilenameValidator.sanitizeEntryName("../.."));
  }

  @Test
  void testValidOcfFilename() {
    var result = FilenameValidator.validateOcfFilename("chapter1.html");
    assertFalse(result.hasErrors());
    assertFalse(result.hasWarnings());
  }

  @Test
  void testDisallowedCharacters() {
    var result = FilenameValidator.validateOcfFilename("file:name.html");
    assertTrue(result.hasDisallowedChars());
    assertTrue(result.hasErrors());
  }

  @Test
  void testFilenameEndingWithPeriod() {
    var result = FilenameValidator.validateOcfFilename("chapter.");
    assertTrue(result.endsWithPeriod());
    assertTrue(result.hasErrors());
  }

  @Test
  void testFilenameWithWhitespace() {
    var result = FilenameValidator.validateOcfFilename("my chapter.html");
    assertTrue(result.hasWhitespace());
    assertTrue(result.hasWarnings());
  }

  @Test
  void testNonAsciiFilename() {
    var result = FilenameValidator.validateOcfFilename("日本語.html");
    assertTrue(result.hasNonAscii());
    assertTrue(result.hasWarnings());
  }

  @Test
  void testWindowsReservedName() {
    var result = FilenameValidator.validateOcfFilename("CON.txt");
    assertTrue(result.isWindowsReserved());
    assertTrue(result.hasErrors());
  }

  @Test
  void testWindowsReservedNameCaseInsensitive() {
    var result = FilenameValidator.validateOcfFilename("con.txt");
    assertTrue(result.isWindowsReserved());
  }

  @Test
  void testPathWithFilenameValidation() {
    // Should validate just the filename part, not the full path
    var result = FilenameValidator.validateOcfFilename("OEBPS/chapter1.html");
    assertFalse(result.hasErrors());
  }

  @Test
  void testControlCharacterRejected() {
    var result = FilenameValidator.validateOcfFilename("file\u0001name.html");
    assertTrue(result.hasDisallowedChars());
  }

  @Test
  void testDelCharacterRejected() {
    var result = FilenameValidator.validateOcfFilename("file\u007Fname.html");
    assertTrue(result.hasDisallowedChars());
  }
}
