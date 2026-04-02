/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;

/**
 * Splits large XHTML documents at heading boundaries and merges small fragments. After split/merge,
 * spine references and internal cross-references are updated.
 */
public class XhtmlSplitMerge {

  private static final System.Logger log = System.getLogger(XhtmlSplitMerge.class.getName());

  // Matches opening heading tags h1-h6, case-insensitive
  private static final Pattern HEADING_PATTERN =
      Pattern.compile("<(h[1-6])\\b[^>]*>", Pattern.CASE_INSENSITIVE);

  // Matches the <body...> opening tag and </body> closing tag
  private static final Pattern BODY_OPEN = Pattern.compile("<body[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern BODY_CLOSE =
      Pattern.compile("</body\\s*>", Pattern.CASE_INSENSITIVE);

  // Matches <title>...</title>
  private static final Pattern TITLE_PATTERN =
      Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

  /**
   * Result of a split operation.
   *
   * @param fragments the new resources created from the split
   * @param originalHref the href of the original resource that was split
   */
  public record SplitResult(List<Resource> fragments, String originalHref) {
    public int fragmentCount() {
      return fragments.size();
    }
  }

  /**
   * Result of a merge operation.
   *
   * @param merged the single merged resource
   * @param mergedHrefs the hrefs of the original resources that were merged
   */
  public record MergeResult(Resource merged, List<String> mergedHrefs) {
    public int sourceCount() {
      return mergedHrefs.size();
    }
  }

  /**
   * Split a spine XHTML resource at heading boundaries (h1-h6). The resource is replaced in the
   * book's resources and spine with the fragments.
   *
   * @param book the book
   * @param resource the XHTML resource to split
   * @param maxHeadingLevel split at headings up to this level (1 = h1 only, 2 = h1+h2, etc.)
   * @return split result, or result with single fragment if no split points found
   */
  public static SplitResult splitAtHeadings(Book book, Resource resource, int maxHeadingLevel) {
    if (maxHeadingLevel < 1 || maxHeadingLevel > 6) {
      throw new IllegalArgumentException("maxHeadingLevel must be 1-6, got " + maxHeadingLevel);
    }

    String content;
    try {
      content = new String(resource.getData(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.log(
          System.Logger.Level.ERROR,
          "Failed to read resource " + resource.getHref() + ": " + e.getMessage());
      return new SplitResult(List.of(resource), resource.getHref());
    }

    // Extract head section (everything before <body>)
    String headSection = extractHead(content);
    String bodyContent = extractBodyContent(content);

    if (bodyContent == null) {
      log.log(
          System.Logger.Level.WARNING,
          "No <body> found in " + resource.getHref() + ", skipping split");
      return new SplitResult(List.of(resource), resource.getHref());
    }

    // Find split points at heading boundaries
    List<Integer> splitPoints = findSplitPoints(bodyContent, maxHeadingLevel);

    if (splitPoints.size() <= 1) {
      // No split points or just one section, so nothing to split
      return new SplitResult(List.of(resource), resource.getHref());
    }

    // Create fragments
    String originalHref = resource.getHref();
    String baseName = hrefBaseName(originalHref);
    String extension = hrefExtension(originalHref);
    String directory = hrefDirectory(originalHref);

    List<Resource> fragments = new ArrayList<>();
    for (int i = 0; i < splitPoints.size(); i++) {
      int start = splitPoints.get(i);
      int end = (i + 1 < splitPoints.size()) ? splitPoints.get(i + 1) : bodyContent.length();

      String fragmentBody = bodyContent.substring(start, end).trim();
      if (fragmentBody.isEmpty()) continue;

      String fragmentTitle = extractFirstHeadingText(fragmentBody);
      String fragmentHref = (i == 0) ? originalHref : directory + baseName + "_" + i + extension;

      String fullXhtml = assembleXhtml(headSection, fragmentBody, fragmentTitle);
      Resource fragment = new Resource(fullXhtml.getBytes(StandardCharsets.UTF_8), fragmentHref);
      fragment.setMediaType(MediaTypes.XHTML);
      fragments.add(fragment);
    }

    if (fragments.isEmpty()) {
      return new SplitResult(List.of(resource), originalHref);
    }

    // Update the book: replace original resource with fragments
    replaceInBook(book, resource, fragments);

    log.log(
        System.Logger.Level.DEBUG,
        "Split " + originalHref + " into " + fragments.size() + " fragments");
    return new SplitResult(List.copyOf(fragments), originalHref);
  }

  /**
   * Merge consecutive spine XHTML resources into a single resource. The body contents are
   * concatenated with an {@code <hr/>} separator. The first resource's href is kept; others are
   * removed.
   *
   * @param book the book
   * @param resources the resources to merge (must be 2+ XHTML resources in spine order)
   * @return merge result
   * @throws IllegalArgumentException if fewer than 2 resources provided
   */
  public static MergeResult merge(Book book, List<Resource> resources) {
    if (resources.size() < 2) {
      throw new IllegalArgumentException(
          "Need at least 2 resources to merge, got " + resources.size());
    }

    // Use first resource as the base
    Resource base = resources.getFirst();
    String baseContent;
    try {
      baseContent = new String(base.getData(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read base resource " + base.getHref(), e);
    }

    String headSection = extractHead(baseContent);
    StringBuilder mergedBody = new StringBuilder();
    List<String> mergedHrefs = new ArrayList<>();

    for (Resource res : resources) {
      mergedHrefs.add(res.getHref());

      String content;
      try {
        content = new String(res.getData(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        log.log(System.Logger.Level.ERROR, "Skipping unreadable resource " + res.getHref());
        continue;
      }

      String body = extractBodyContent(content);
      if (body == null || body.isBlank()) continue;

      if (!mergedBody.isEmpty()) {
        mergedBody.append("\n<hr class=\"epub-merge-separator\"/>\n");
      }
      mergedBody.append(body.trim());
    }

    String fullXhtml = assembleXhtml(headSection, mergedBody.toString(), null);
    Resource merged = new Resource(fullXhtml.getBytes(StandardCharsets.UTF_8), base.getHref());
    merged.setMediaType(MediaTypes.XHTML);
    merged.setId(base.getId());

    // Remove all source resources from spine and resources, then add merged
    Spine spine = book.getSpine();
    int insertIndex = -1;
    for (Resource res : resources) {
      int spineIdx = spine.getResourceIndex(res);
      if (spineIdx >= 0) {
        if (insertIndex < 0) insertIndex = spineIdx;
        spine.getSpineReferences().remove(spineIdx);
      }
      book.getResources().remove(res.getHref());
    }

    // Add merged resource
    book.getResources().add(merged);
    if (insertIndex >= 0) {
      spine.getSpineReferences().add(insertIndex, new SpineReference(merged));
    } else {
      spine.addSpineReference(new SpineReference(merged));
    }

    log.log(
        System.Logger.Level.DEBUG,
        "Merged " + mergedHrefs.size() + " resources into " + merged.getHref());
    return new MergeResult(merged, List.copyOf(mergedHrefs));
  }

  private static String extractHead(String xhtml) {
    // Everything from start through </head> (inclusive), or a default head
    int headClose = xhtml.toLowerCase().indexOf("</head>");
    if (headClose < 0) {
      return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                    <title></title>
                    </head>""";
    }
    return xhtml.substring(0, headClose + "</head>".length());
  }

  private static String extractBodyContent(String xhtml) {
    Matcher openMatcher = BODY_OPEN.matcher(xhtml);
    if (!openMatcher.find()) return null;
    int bodyStart = openMatcher.end();

    Matcher closeMatcher = BODY_CLOSE.matcher(xhtml);
    if (!closeMatcher.find(bodyStart)) return null;
    int bodyEnd = closeMatcher.start();

    return xhtml.substring(bodyStart, bodyEnd);
  }

  private static List<Integer> findSplitPoints(String bodyContent, int maxLevel) {
    String headingRegex = "<(h[1-" + maxLevel + "])\\b[^>]*>";
    Pattern pattern = Pattern.compile(headingRegex, Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(bodyContent);

    List<Integer> points = new ArrayList<>();
    // Always include position 0 if there's content before first heading
    boolean firstHeadingFound = false;

    while (matcher.find()) {
      if (!firstHeadingFound && matcher.start() > 0) {
        // There's content before the first heading, so include it as a section
        points.add(0);
      }
      firstHeadingFound = true;
      points.add(matcher.start());
    }

    if (!firstHeadingFound) {
      // No headings found, return single section
      points.add(0);
    }

    return points;
  }

  private static String extractFirstHeadingText(String body) {
    Matcher m = HEADING_PATTERN.matcher(body);
    if (!m.find()) return null;
    int start = m.end();
    String closeTag = "</" + m.group(1);
    int end = body.toLowerCase().indexOf(closeTag.toLowerCase(), start);
    if (end < 0) return null;
    return HTML_TAG_PATTERN.matcher(body.substring(start, end)).replaceAll("").trim();
  }

  private static String assembleXhtml(String headSection, String bodyContent, String title) {
    String head = headSection;
    if (title != null && !title.isEmpty()) {
      // Update <title> in head if possible
      Matcher tm = TITLE_PATTERN.matcher(head);
      if (tm.find()) {
        head = head.substring(0, tm.start(1)) + title + head.substring(tm.end(1));
      }
    }
    return head + "\n<body>\n" + bodyContent + "\n</body>\n</html>";
  }

  private static void replaceInBook(Book book, Resource original, List<Resource> fragments) {
    Spine spine = book.getSpine();
    int spineIndex = spine.getResourceIndex(original);

    // Remove original from resources and spine
    book.getResources().remove(original.getHref());
    if (spineIndex >= 0) {
      spine.getSpineReferences().remove(spineIndex);
    }

    // Add fragments in order
    int insertAt = Math.max(spineIndex, 0);
    for (int i = 0; i < fragments.size(); i++) {
      Resource frag = fragments.get(i);
      book.getResources().add(frag);
      spine.getSpineReferences().add(insertAt + i, new SpineReference(frag));
    }
  }

  private static String hrefBaseName(String href) {
    String name = href;
    int slashIdx = name.lastIndexOf('/');
    if (slashIdx >= 0) name = name.substring(slashIdx + 1);
    int dotIdx = name.lastIndexOf('.');
    if (dotIdx >= 0) name = name.substring(0, dotIdx);
    return name;
  }

  private static String hrefExtension(String href) {
    int dotIdx = href.lastIndexOf('.');
    return (dotIdx >= 0) ? href.substring(dotIdx) : ".xhtml";
  }

  private static String hrefDirectory(String href) {
    int slashIdx = href.lastIndexOf('/');
    return (slashIdx >= 0) ? href.substring(0, slashIdx + 1) : "";
  }
}
