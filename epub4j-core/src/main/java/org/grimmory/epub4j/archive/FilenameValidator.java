package org.grimmory.epub4j.archive;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and sanitizes archive entry filenames for security and OCF compliance.
 *
 * <p>Provides protection against path traversal attacks and validates filenames per the EPUB Open
 * Container Format specification. Validation rules are derived from the EPUB OCF spec and informed
 * by epubcheck's OCFFilenameChecker.
 *
 * <p>Key checks:
 *
 * <ul>
 *   <li>Path traversal prevention (.. components, absolute paths)
 *   <li>PKG_009: Disallowed characters in OCF filenames
 *   <li>PKG_011: Filename must not end with a period
 *   <li>Windows reserved filenames (CON, PRN, AUX, NUL, COM1-9, LPT1-9)
 * </ul>
 */
public final class FilenameValidator {

  /**
   * Disallowed characters in OCF filenames (EPUB spec). Includes: backslash, colon, asterisk,
   * question mark, double quote, less-than, greater-than, pipe, DEL (0x7F), and C0 controls
   * (0x00-0x1F).
   */
  private static final Pattern DISALLOWED_OCF_CHARS =
      Pattern.compile("[\\\\:*?\"<>|\\x00-\\x1F\\x7F]");

  /** Windows reserved device names that cannot be used as filenames. */
  private static final Set<String> WINDOWS_RESERVED =
      Set.of(
          "CON", "PRN", "AUX", "NUL", "CLOCK$", "COM0", "COM1", "COM2", "COM3", "COM4", "COM5",
          "COM6", "COM7", "COM8", "COM9", "LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6",
          "LPT7", "LPT8", "LPT9");

  private static final Pattern PATH_SEPARATOR_PATTERN = Pattern.compile("[/\\\\]");

  /**
   * Filenames injected by OS tools (macOS Finder, Windows Explorer) that are never genuine EPUB
   * resources.
   */
  private static final Set<String> SYSTEM_FILENAMES =
      Set.of(".DS_Store", "Thumbs.db", "desktop.ini");

  /** Directory prefixes injected by macOS resource forks and similar tooling. */
  private static final Set<String> SYSTEM_DIR_PREFIXES = Set.of("__MACOSX/");

  private FilenameValidator() {}

  /**
   * Checks if the given archive entry name is an OS-injected system file that should be skipped
   * during EPUB processing.
   *
   * <p>Filters out macOS resource fork directories ({@code __MACOSX/}), Apple double files ({@code
   * ._*}), and common OS metadata files ({@code .DS_Store}, {@code Thumbs.db}, {@code
   * desktop.ini}).
   *
   * @param entryName the archive entry name to check
   * @return true if the entry is a system file and should be skipped
   */
  public static boolean isSystemFile(String entryName) {
    if (entryName == null || entryName.isEmpty()) {
      return false;
    }

    for (String prefix : SYSTEM_DIR_PREFIXES) {
      if (entryName.startsWith(prefix)) {
        return true;
      }
    }

    // System files can be nested under valid directories
    int lastSlash = entryName.lastIndexOf('/');
    String filename = lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;

    if (filename.startsWith("._")) {
      return true;
    }

    return SYSTEM_FILENAMES.contains(filename);
  }

  /**
   * Checks if the given archive entry name is safe to extract. Rejects path traversal attempts and
   * absolute paths.
   *
   * @param entryName the archive entry name to check
   * @return true if the entry name is safe, false if it should be rejected
   */
  public static boolean isSafeEntryName(String entryName) {
    if (entryName == null || entryName.isEmpty()) {
      return false;
    }

    // Reject absolute paths
    if (entryName.startsWith("/") || entryName.startsWith("\\")) {
      return false;
    }

    // Check for Windows drive letters (e.g., "C:\...")
    if (entryName.length() >= 2
        && entryName.charAt(1) == ':'
        && Character.isLetter(entryName.charAt(0))) {
      return false;
    }

    // Check each path component for traversal and reserved names
    String[] components = PATH_SEPARATOR_PATTERN.split(entryName);
    for (String component : components) {
      switch (component) {
        case "" -> {
          // skip empty from double separators
        }
        case ".." -> {
          return false; // path traversal
        }
        case "." -> {
          // current dir reference, harmless but skip
        }
      }
    }

    return true;
  }

  /**
   * Sanitizes an archive entry name by removing path traversal components and normalizing
   * separators. Returns null if the name cannot be sanitized.
   *
   * @param entryName the archive entry name to sanitize
   * @return the sanitized name, or null if unsalvageable
   */
  public static String sanitizeEntryName(String entryName) {
    if (entryName == null || entryName.isEmpty()) {
      return null;
    }

    // Normalize backslashes to forward slashes
    String normalized = entryName.replace('\\', '/');

    // Strip leading slashes and drive letters
    if (normalized.length() >= 2
        && normalized.charAt(1) == ':'
        && Character.isLetter(normalized.charAt(0))) {
      normalized = normalized.substring(2);
    }
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }

    // Remove .. and . components
    Deque<String> components = new ArrayDeque<>();
    for (String component : normalized.split("/")) {
      if (component.isEmpty() || ".".equals(component)) {
        continue;
      }
      if ("..".equals(component)) {
        if (!components.isEmpty()) {
          components.removeLast();
        }
        continue;
      }
      components.addLast(component);
    }

    return components.isEmpty() ? null : String.join("/", components);
  }

  /**
   * Validates a filename against the OCF specification rules.
   *
   * @param filename the filename (not full path) to validate
   * @return a validation result describing any issues found
   */
  public static FilenameValidation validateOcfFilename(String filename) {
    if (filename == null || filename.isEmpty()) {
      return new FilenameValidation(true, false, false, false, false);
    }

    // OCF rules apply to the leaf name, not the full path
    int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
    String name = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;

    boolean hasDisallowedChars = DISALLOWED_OCF_CHARS.matcher(name).find();
    boolean endsWithPeriod = name.endsWith(".");
    boolean hasWhitespace = name.chars().anyMatch(Character::isWhitespace);
    boolean isNonAscii = !name.chars().allMatch(c -> c >= 0x20 && c <= 0x7E);
    boolean isWindowsReserved = isWindowsReservedName(name);

    return new FilenameValidation(
        hasDisallowedChars, endsWithPeriod, hasWhitespace, isNonAscii, isWindowsReserved);
  }

  private static boolean isWindowsReservedName(String name) {
    // Windows reserves names regardless of extension (CON.txt is still reserved)
    int dot = name.indexOf('.');
    String baseName = dot >= 0 ? name.substring(0, dot) : name;
    return WINDOWS_RESERVED.contains(baseName.toUpperCase());
  }

  /**
   * Result of OCF filename validation.
   *
   * @param hasDisallowedChars PKG_009: contains characters not allowed in OCF
   * @param endsWithPeriod PKG_011: filename ends with period
   * @param hasWhitespace PKG_010: contains whitespace (warning)
   * @param hasNonAscii PKG_012: contains non-ASCII characters (informational)
   * @param isWindowsReserved filename is a Windows reserved device name
   */
  public record FilenameValidation(
      boolean hasDisallowedChars,
      boolean endsWithPeriod,
      boolean hasWhitespace,
      boolean hasNonAscii,
      boolean isWindowsReserved) {
    public boolean hasErrors() {
      return hasDisallowedChars || endsWithPeriod || isWindowsReserved;
    }

    public boolean hasWarnings() {
      return hasWhitespace || hasNonAscii;
    }
  }
}
