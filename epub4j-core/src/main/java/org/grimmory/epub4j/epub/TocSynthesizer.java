/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;

/**
 * Synthesizes a table of contents from the spine when NCX/Nav is missing or empty. Extracts titles
 * from XHTML content using multiple strategies: 1. HTML &lt;title&gt; tag 2. First
 * &lt;h1&gt;-&lt;h6&gt; heading 3. Filename-based fallback
 */
public class TocSynthesizer {

  private static final System.Logger log = System.getLogger(TocSynthesizer.class.getName());

  // Regex patterns for title extraction - compiled once
  private static final Pattern TITLE_PATTERN =
      Pattern.compile(
          "<title[^>]*>\\s*([^<]+?)\\s*</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern H1_PATTERN =
      Pattern.compile(
          "<h[1-6][^>]*>\\s*([^<]+?)\\s*</h[1-6]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /**
   * Synthesize a table of contents from the book's spine. Each spine item becomes a TOC entry.
   * Title is extracted from the content using multiple fallback strategies.
   *
   * @param book the book whose spine will be used to generate the TOC
   * @return a new TableOfContents with entries derived from the spine
   */
  public static TableOfContents synthesize(Book book) {
    List<TOCReference> tocRefs = new ArrayList<>();
    Spine spine = book.getSpine();

    for (int i = 0; i < spine.size(); i++) {
      Resource resource = spine.getResource(i);
      if (resource == null) continue;

      String title = extractTitle(resource);
      if (title == null || title.isBlank()) {
        title = titleFromHref(resource.getHref(), i + 1);
      }

      tocRefs.add(new TOCReference(title, resource));
    }

    log.log(
        System.Logger.Level.DEBUG,
        "Synthesized TOC with " + tocRefs.size() + " entries from spine");
    return new TableOfContents(tocRefs);
  }

  /**
   * Extract a title from an XHTML resource using multiple strategies.
   *
   * @param resource the resource to extract a title from
   * @return the extracted title, or null if none found
   */
  static String extractTitle(Resource resource) {
    // Only try for XHTML resources
    if (resource.getMediaType() != MediaTypes.XHTML) {
      return null;
    }

    try {
      byte[] data = resource.getData();
      if (data == null || data.length == 0) return null;

      String content = new String(data, StandardCharsets.UTF_8);

      // Strategy 1: <title> tag
      Matcher titleMatcher = TITLE_PATTERN.matcher(content);
      if (titleMatcher.find()) {
        String title = cleanHtmlEntities(titleMatcher.group(1).trim());
        if (!title.isBlank()) return title;
      }

      // Strategy 2: First heading (h1-h6)
      Matcher hMatcher = H1_PATTERN.matcher(content);
      if (hMatcher.find()) {
        String heading = cleanHtmlEntities(hMatcher.group(1).trim());
        if (!heading.isBlank()) return heading;
      }
    } catch (IOException e) {
      log.log(
          System.Logger.Level.DEBUG,
          "Failed to read resource for title extraction: " + resource.getHref());
    }

    return null;
  }

  /**
   * Generate a title from the resource href as last resort.
   *
   * @param href the resource href
   * @param index the 1-based position in the spine
   * @return a human-readable title derived from the filename
   */
  static String titleFromHref(String href, int index) {
    if (href == null) return "Chapter " + index;

    // Extract filename without extension
    String filename = href;
    int lastSlash = filename.lastIndexOf('/');
    if (lastSlash >= 0) filename = filename.substring(lastSlash + 1);
    int lastDot = filename.lastIndexOf('.');
    if (lastDot > 0) filename = filename.substring(0, lastDot);

    // Clean up common patterns: chapter_01 -> Chapter 1, ch01 -> Chapter 1
    filename = filename.replace('_', ' ').replace('-', ' ');
    if (filename.isBlank()) return "Chapter " + index;

    return filename;
  }

  private static String cleanHtmlEntities(String text) {
    return text.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'");
  }
}
