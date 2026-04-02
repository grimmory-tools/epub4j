/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.util;

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * Utility class for converting between file names, paths, and hrefs in EPUBs.
 *
 * <p>EPUBs use POSIX-style paths (forward slashes) while some systems use native path separators.
 * This class provides utilities for converting between different path representations.
 *
 * <p>Key conversions:
 *
 * <ul>
 *   <li>{@code name} - Internal EPUB path (e.g., "OEBPS/chapter1.xhtml")
 *   <li>{@code href} - URL-encoded reference (e.g., "OEBPS/chapter1.xhtml")
 *   <li>{@code abspath} - Absolute filesystem path
 * </ul>
 *
 * @author Grimmory
 */
public final class PathUtils {

  private static final Pattern MULTIPLE_SLASH_PATTERN = Pattern.compile("/+");

  private PathUtils() {
    // Utility class
  }

  /**
   * Convert a name (EPUB internal path) to an absolute filesystem path.
   *
   * @param name the EPUB internal name (e.g., "OEBPS/chapter1.xhtml")
   * @param root the root directory of the EPUB
   * @return the absolute filesystem path
   */
  public static Path nameToAbsPath(String name, Path root) {
    if (name == null || name.isEmpty()) {
      return root;
    }

    // Split name by forward slashes (EPUB standard)
    String[] parts = name.split("/");
    Path result = root;
    for (String part : parts) {
      if (!part.isEmpty() && !".".equals(part) && !"..".equals(part)) {
        result = result.resolve(part);
      }
    }

    return result.normalize().toAbsolutePath();
  }

  /**
   * Convert an absolute filesystem path to an EPUB internal name.
   *
   * @param absPath the absolute filesystem path
   * @param root the root directory of the EPUB
   * @return the EPUB internal name
   */
  public static Path absPathToName(Path absPath, Path root) {
    Path normalizedRoot = root.normalize().toAbsolutePath();
    Path normalizedPath = absPath.normalize().toAbsolutePath();

    if (!normalizedPath.startsWith(normalizedRoot)) {
      throw new IllegalArgumentException("Path " + absPath + " is not under root " + root);
    }

    Path relative = normalizedRoot.relativize(normalizedPath);
    // Convert to forward slashes (EPUB standard)
    return Path.of(relative.toString().replace('\\', '/'));
  }

  /**
   * Convert a name to an href (URL-encoded path for use in EPUB references).
   *
   * @param name the EPUB internal name
   * @param root the root directory of the EPUB
   * @param base the base name for relative path calculation, or null
   * @return the URL-encoded href
   */
  public static String nameToHref(String name, Path root, String base) {
    Path fullPath = nameToAbsPath(name, root);
    Path basePath = (base == null) ? root : nameToAbsPath(base, root).getParent();

    if (basePath == null) {
      basePath = root;
    }

    Path relative = basePath.relativize(fullPath);
    String href = relative.toString().replace('\\', '/');

    // URL-encode special characters
    return urlEncode(href);
  }

  /**
   * Convert an href to an EPUB internal name.
   *
   * @param href the URL-encoded href
   * @param root the root directory of the EPUB
   * @param base the base name for relative path calculation, or null
   * @return the EPUB internal name, or null if href is invalid
   */
  public static String hrefToName(String href, Path root, String base) {
    if (href == null || href.isEmpty()) {
      return null;
    }

    try {
      // Parse as URL to check for schemes
      if (href.contains("://") || href.startsWith("mailto:") || href.startsWith("javascript:")) {
        return null; // External URL
      }

      // URL-decode
      String decoded = urlDecode(href);

      // Remove fragment
      int hashPos = decoded.indexOf('#');
      if (hashPos >= 0) {
        decoded = decoded.substring(0, hashPos);
      }

      // Remove query string
      int queryPos = decoded.indexOf('?');
      if (queryPos >= 0) {
        decoded = decoded.substring(0, queryPos);
      }

      if (decoded.isEmpty()) {
        return null;
      }

      // Convert to path
      Path basePath = (base == null) ? root : nameToAbsPath(base, root).getParent();
      if (basePath == null) {
        basePath = root;
      }

      // Handle absolute paths
      Path hrefPath = Path.of(decoded.replace('/', File.separatorChar));
      if (hrefPath.isAbsolute()) {
        return null; // Absolute path not allowed
      }

      Path fullPath = basePath.resolve(hrefPath).normalize();
      return absPathToName(fullPath, root).toString();

    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Collapse path dots (.. and .) in a path string.
   *
   * @param path the path to collapse
   * @return the collapsed path
   */
  public static String collapsePathDots(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }

    String[] parts = path.split("/");
    Deque<String> stack = new ArrayDeque<>();

    for (String part : parts) {
      if ("..".equals(part)) {
        if (!stack.isEmpty()) {
          stack.removeLast();
        }
      } else if (!".".equals(part) && !part.isEmpty()) {
        stack.addLast(part);
      }
    }

    return String.join("/", stack);
  }

  /**
   * Normalize a path by removing redundant separators and dots.
   *
   * @param path the path to normalize
   * @return the normalized path
   */
  public static String normalizePath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }

    // Replace backslashes with forward slashes
    String normalized = path.replace('\\', '/');

    // Collapse multiple slashes
    normalized = MULTIPLE_SLASH_PATTERN.matcher(normalized).replaceAll("/");

    // Remove leading slash for relative paths
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }

    // Collapse path dots
    normalized = collapsePathDots(normalized);

    return normalized;
  }

  /**
   * Get the directory portion of a path.
   *
   * @param path the path
   * @return the directory portion, or empty string if no directory
   */
  public static String getDirectory(String path) {
    if (path == null || path.isEmpty()) {
      return "";
    }

    int lastSlash = path.replace('\\', '/').lastIndexOf('/');
    if (lastSlash < 0) {
      return "";
    }

    return path.substring(0, lastSlash);
  }

  /**
   * Get the filename portion of a path.
   *
   * @param path the path
   * @return the filename portion
   */
  public static String getFilename(String path) {
    if (path == null || path.isEmpty()) {
      return "";
    }

    String normalized = path.replace('\\', '/');
    int lastSlash = normalized.lastIndexOf('/');
    if (lastSlash < 0) {
      return path;
    }

    return path.substring(lastSlash + 1);
  }

  /**
   * Get the file extension (including the dot).
   *
   * @param path the path
   * @return the extension, or empty string if no extension
   */
  public static String getExtension(String path) {
    String filename = getFilename(path);
    int lastDot = filename.lastIndexOf('.');
    if (lastDot < 0) {
      return "";
    }
    return filename.substring(lastDot);
  }

  /**
   * Get the filename without extension.
   *
   * @param path the path
   * @return the filename without extension
   */
  public static String getFilenameWithoutExtension(String path) {
    String filename = getFilename(path);
    int lastDot = filename.lastIndexOf('.');
    if (lastDot < 0) {
      return filename;
    }
    return filename.substring(0, lastDot);
  }

  /**
   * Join path components into a single path.
   *
   * @param parts the path components
   * @return the joined path
   */
  public static String join(String... parts) {
    if (parts == null || parts.length == 0) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (part == null || part.isEmpty()) {
        continue;
      }

      if (!sb.isEmpty() && !sb.toString().endsWith("/")) {
        sb.append('/');
      }

      // Remove leading slash from component (except first)
      if (i > 0 && part.startsWith("/")) {
        part = part.substring(1);
      }

      // Remove trailing slash from component (except last)
      if (i < parts.length - 1 && part.endsWith("/")) {
        part = part.substring(0, part.length() - 1);
      }

      sb.append(part);
    }

    return sb.toString();
  }

  /**
   * Check if a filename is valid for EPUB. Valid filenames contain only ASCII letters, digits,
   * hyphens, underscores, periods, and forward slashes.
   *
   * @param filename the filename to check
   * @return true if valid
   */
  public static boolean isValidFilename(String filename) {
    if (filename == null || filename.isEmpty()) {
      return false;
    }

    for (char c : filename.toCharArray()) {
      if (!isValidPathChar(c)) {
        return false;
      }
    }

    return true;
  }

  /** Check if a character is valid in an EPUB path. */
  private static boolean isValidPathChar(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '_'
        || c == '.'
        || c == '/'
        || c == '\\'; // Allow but will be normalized
  }

  /** URL-encode a string for use in hrefs. */
  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("%2F", "/"); // Keep slashes unencoded
  }

  /** URL-decode a string. */
  private static String urlDecode(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }
}
