package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;

/**
 * Converts standard EPUB content to KEPUB (Kobo Enhanced Publication) format.
 *
 * <p>KEPUB adds Kobo-specific markup that enables:
 *
 * <ul>
 *   <li>Per-paragraph reading progress tracking
 *   <li>Kobo's proprietary pagination
 *   <li>Enhanced highlighting and annotation support
 *   <li>Kobo store integration features
 * </ul>
 *
 * The conversion modifies XHTML content resources in-place by:
 *
 * <ol>
 *   <li>Wrapping text nodes in koboSpan elements for progress tracking
 *   <li>Adding Kobo container divs for pagination
 *   <li>Ensuring proper XHTML structure for Kobo's renderer
 * </ol>
 */
public class KepubConverter {

  private static final System.Logger log = System.getLogger(KepubConverter.class.getName());

  // Pattern to match text content between HTML tags (not inside script/style)
  private static final Pattern TEXT_NODE_PATTERN = Pattern.compile("(>[^<]+<)", Pattern.DOTALL);

  // Pattern to match the body tag (to inject Kobo wrappers)
  private static final Pattern BODY_START_PATTERN =
      Pattern.compile("(<body[^>]*>)", Pattern.CASE_INSENSITIVE);
  private static final Pattern BODY_END_PATTERN =
      Pattern.compile("(</body>)", Pattern.CASE_INSENSITIVE);

  // Pattern to detect if we are inside a tag that should not be wrapped
  private static final Pattern SKIP_TAG_OPEN =
      Pattern.compile("<(script|style|title|head)\\b[^>]*>", Pattern.CASE_INSENSITIVE);

  /**
   * Result of a KEPUB conversion operation.
   *
   * @param book the converted book
   * @param chaptersProcessed number of XHTML chapters that were processed
   * @param spansInserted total number of koboSpan elements inserted
   */
  public record ConversionResult(Book book, int chaptersProcessed, int spansInserted) {}

  /**
   * Convert the given book to KEPUB format. Modifies XHTML resources in-place.
   *
   * @param book the EPUB book to convert
   * @return conversion result with statistics
   */
  public static ConversionResult convert(Book book) {
    int chaptersProcessed = 0;
    int totalSpans = 0;

    List<SpineReference> spineRefs = book.getSpine().getSpineReferences();
    for (int chapterIdx = 0; chapterIdx < spineRefs.size(); chapterIdx++) {
      Resource resource = spineRefs.get(chapterIdx).getResource();
      if (resource == null || resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }

      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;

        String content = new String(data, StandardCharsets.UTF_8);
        SpanResult result = insertKoboSpans(content, chapterIdx + 1);
        String wrapped = wrapWithKoboContainers(result.content());

        resource.setData(wrapped.getBytes(StandardCharsets.UTF_8));
        chaptersProcessed++;
        totalSpans += result.spanCount();
      } catch (IOException e) {
        log.log(
            System.Logger.Level.ERROR,
            "Failed to convert chapter " + chapterIdx + " to KEPUB: " + e.getMessage());
      }
    }

    log.log(
        System.Logger.Level.DEBUG,
        "KEPUB conversion: " + chaptersProcessed + " chapters, " + totalSpans + " spans");
    return new ConversionResult(book, chaptersProcessed, totalSpans);
  }

  private record SpanResult(String content, int spanCount) {}

  /**
   * Insert koboSpan elements around text nodes in the XHTML content. Each span gets a unique ID in
   * the format "kobo.{chapter}.{span}" for progress tracking. Text inside script, style, title, and
   * head tags is not wrapped.
   *
   * @param xhtml the XHTML content
   * @param chapterNumber the 1-based chapter number
   * @return the content with koboSpan elements and the count of spans inserted
   */
  static SpanResult insertKoboSpans(String xhtml, int chapterNumber) {
    // First, identify regions to skip (script, style, title, head)
    Set<int[]> skipRegions = findSkipRegions(xhtml);

    StringBuilder result = new StringBuilder(xhtml.length() + xhtml.length() / 4);
    int spanCounter = 1;

    Matcher matcher = TEXT_NODE_PATTERN.matcher(xhtml);
    int lastEnd = 0;

    while (matcher.find()) {
      int matchStart = matcher.start();

      // Check if this text node is inside a skip region
      if (isInSkipRegion(matchStart, skipRegions)) {
        result.append(xhtml, lastEnd, matcher.end());
        lastEnd = matcher.end();
        continue;
      }

      result.append(xhtml, lastEnd, matcher.start());
      String textWithBrackets = matcher.group(1);
      String text = textWithBrackets.substring(1, textWithBrackets.length() - 1);

      if (text.isBlank()) {
        result.append(textWithBrackets);
      } else {
        result.append('>');
        result
            .append("<span class=\"koboSpan\" id=\"kobo.")
            .append(chapterNumber)
            .append('.')
            .append(spanCounter++)
            .append("\">")
            .append(text)
            .append("</span>");
        result.append('<');
      }
      lastEnd = matcher.end();
    }

    result.append(xhtml, lastEnd, xhtml.length());
    return new SpanResult(result.toString(), spanCounter - 1);
  }

  /**
   * Find regions of the XHTML that should not have koboSpan wrapping applied. These are the
   * interiors of script, style, title, and head elements.
   */
  private static Set<int[]> findSkipRegions(String xhtml) {
    Set<int[]> regions = new HashSet<>();
    Matcher openMatcher = SKIP_TAG_OPEN.matcher(xhtml);
    while (openMatcher.find()) {
      String tagName = openMatcher.group(1).toLowerCase();
      int regionStart = openMatcher.start();
      // Find matching close tag
      Pattern closePattern =
          Pattern.compile("</" + Pattern.quote(tagName) + ">", Pattern.CASE_INSENSITIVE);
      Matcher closeMatcher = closePattern.matcher(xhtml);
      if (closeMatcher.find(openMatcher.end())) {
        regions.add(new int[] {regionStart, closeMatcher.end()});
      }
    }
    return regions;
  }

  /** Check if a position falls inside any skip region. */
  private static boolean isInSkipRegion(int position, Set<int[]> skipRegions) {
    for (int[] region : skipRegions) {
      if (position >= region[0] && position < region[1]) {
        return true;
      }
    }
    return false;
  }

  /**
   * Wrap body content with Kobo container divs for pagination support. Inserts {@code <div
   * class="book-inner"><div class="book-columns">} after the body tag and closing divs before
   * {@code </body>}.
   *
   * @param xhtml the XHTML content
   * @return the content with Kobo container divs
   */
  static String wrapWithKoboContainers(String xhtml) {
    String result =
        BODY_START_PATTERN
            .matcher(xhtml)
            .replaceFirst("$1<div class=\"book-inner\"><div class=\"book-columns\">");
    result = BODY_END_PATTERN.matcher(result).replaceFirst("</div></div>$1");
    return result;
  }
}
