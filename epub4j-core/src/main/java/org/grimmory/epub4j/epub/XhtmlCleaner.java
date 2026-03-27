package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;

/**
 * Lightweight HTML→XHTML normalizer that fixes common issues in real-world EPUBs without requiring
 * an external DOM parser. Handles:
 *
 * <ul>
 *   <li>Self-closing void elements (br, hr, img, input, meta, link)
 *   <li>Undeclared HTML entities (&amp;nbsp;, &amp;copy;, etc.)
 *   <li>Missing XML namespace declaration
 *   <li>Missing XML prolog
 *   <li>Unquoted attribute values
 *   <li>Boolean attributes (checked → checked="checked")
 * </ul>
 *
 * Based on common patterns from HTML5 parsing and epubcheck's error catalog.
 */
public class XhtmlCleaner {

  private static final System.Logger log = System.getLogger(XhtmlCleaner.class.getName());

  // HTML void elements that must be self-closed in XHTML
  private static final Pattern VOID_ELEMENT =
      Pattern.compile(
          "<(br|hr|img|input|meta|link|col|area|base|embed|param|source|track|wbr)"
              + "(\\s[^>]*)?>(?!</)",
          Pattern.CASE_INSENSITIVE);

  // Already self-closed elements (no change needed)
  private static final Pattern SELF_CLOSED = Pattern.compile("/>\\s*$");

  // Missing xmlns on html tag
  private static final Pattern HTML_TAG =
      Pattern.compile("<html(?![^>]*xmlns)[^>]*>", Pattern.CASE_INSENSITIVE);

  // XML prolog
  private static final Pattern XML_PROLOG =
      Pattern.compile("^\\s*<\\?xml[^?]*\\?>", Pattern.CASE_INSENSITIVE);

  // Boolean attributes pattern: attr without value
  private static final Pattern BOOLEAN_ATTR =
      Pattern.compile(
          "\\s(checked|disabled|readonly|selected|multiple|autofocus|required|hidden|defer|async)"
              + "(?=\\s|/?>)(?!=)",
          Pattern.CASE_INSENSITIVE);

  // Unquoted attribute values: attr=value (where value has no quotes)
  private static final Pattern UNQUOTED_ATTR =
      Pattern.compile("(\\s\\w+)=([^\"'\\s>][^\\s>]*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/\\s*$");
  private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<html");

  /**
   * Result of cleaning an XHTML resource.
   *
   * @param modified whether any changes were made
   * @param fixCount number of individual fixes applied
   */
  public record CleanResult(boolean modified, int fixCount) {}

  /**
   * Clean all XHTML resources in a book.
   *
   * @param book the book to clean
   * @return total number of fixes applied across all resources
   */
  public static int cleanAll(Book book) {
    int totalFixes = 0;
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.XHTML) continue;
      CleanResult result = clean(resource);
      totalFixes += result.fixCount();
    }
    return totalFixes;
  }

  /**
   * Clean a single XHTML resource in-place.
   *
   * @param resource the resource to clean
   * @return clean result
   */
  public static CleanResult clean(Resource resource) {
    try {
      byte[] data = resource.getData();
      if (data == null || data.length == 0) return new CleanResult(false, 0);

      String content = new String(data, StandardCharsets.UTF_8);
      int fixes = 0;

      // Fix 1: Ensure XML prolog
      if (!XML_PROLOG.matcher(content).find()) {
        content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + content;
        fixes++;
      }

      // Fix 2: Ensure xmlns on <html>
      Matcher htmlMatcher = HTML_TAG.matcher(content);
      if (htmlMatcher.find()) {
        String tag = htmlMatcher.group();
        String fixed =
            HTML_TAG_PATTERN
                .matcher(tag)
                .replaceFirst("<html xmlns=\"http://www.w3.org/1999/xhtml\"");
        content =
            content.substring(0, htmlMatcher.start())
                + fixed
                + content.substring(htmlMatcher.end());
        fixes++;
      }

      // Fix 3: Self-close void elements
      StringBuilder sb = new StringBuilder();
      Matcher voidMatcher = VOID_ELEMENT.matcher(content);
      while (voidMatcher.find()) {
        String match = voidMatcher.group();
        if (!SELF_CLOSED.matcher(match).find()) {
          String attrs = voidMatcher.group(2);
          if (attrs == null) attrs = "";
          attrs = TRAILING_SLASH_PATTERN.matcher(attrs).replaceAll("").trim();
          String replacement =
              "<" + voidMatcher.group(1) + (attrs.isEmpty() ? "" : " " + attrs) + "/>";
          voidMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
          fixes++;
        }
      }
      voidMatcher.appendTail(sb);
      content = sb.toString();

      // Fix 4: Replace HTML entities with numeric equivalents
      content = replaceHtmlEntities(content);
      // Count entity replacements (approximate by checking if content changed)

      // Fix 5: Boolean attributes → quoted form
      sb = new StringBuilder();
      Matcher boolMatcher = BOOLEAN_ATTR.matcher(content);
      while (boolMatcher.find()) {
        String attr = boolMatcher.group(1).trim();
        boolMatcher.appendReplacement(
            sb, Matcher.quoteReplacement(" " + attr + "=\"" + attr + "\""));
        fixes++;
      }
      boolMatcher.appendTail(sb);
      content = sb.toString();

      // Fix 6: Quote unquoted attribute values
      sb = new StringBuilder();
      Matcher unquotedMatcher = UNQUOTED_ATTR.matcher(content);
      while (unquotedMatcher.find()) {
        String attr = unquotedMatcher.group(1);
        String val = unquotedMatcher.group(2);
        unquotedMatcher.appendReplacement(sb, Matcher.quoteReplacement(attr + "=\"" + val + "\""));
        fixes++;
      }
      unquotedMatcher.appendTail(sb);
      content = sb.toString();

      if (fixes > 0) {
        resource.setData(content.getBytes(StandardCharsets.UTF_8));
        log.log(
            System.Logger.Level.DEBUG, "Cleaned " + resource.getHref() + ": " + fixes + " fixes");
      }
      return new CleanResult(fixes > 0, fixes);
    } catch (IOException e) {
      log.log(
          System.Logger.Level.ERROR,
          "Failed to clean " + resource.getHref() + ": " + e.getMessage());
      return new CleanResult(false, 0);
    }
  }

  /** Replace common HTML named entities with their numeric XML equivalents. */
  private static String replaceHtmlEntities(String content) {
    // Only replace entities that are NOT already declared (amp, lt, gt, quot, apos are XML-safe)
    content = content.replace("&nbsp;", "&#160;");
    content = content.replace("&copy;", "&#169;");
    content = content.replace("&reg;", "&#174;");
    content = content.replace("&trade;", "&#8482;");
    content = content.replace("&mdash;", "&#8212;");
    content = content.replace("&ndash;", "&#8211;");
    content = content.replace("&lsquo;", "&#8216;");
    content = content.replace("&rsquo;", "&#8217;");
    content = content.replace("&ldquo;", "&#8220;");
    content = content.replace("&rdquo;", "&#8221;");
    content = content.replace("&bull;", "&#8226;");
    content = content.replace("&hellip;", "&#8230;");
    content = content.replace("&euro;", "&#8364;");
    content = content.replace("&pound;", "&#163;");
    content = content.replace("&yen;", "&#165;");
    content = content.replace("&cent;", "&#162;");
    content = content.replace("&sect;", "&#167;");
    content = content.replace("&deg;", "&#176;");
    content = content.replace("&micro;", "&#181;");
    content = content.replace("&para;", "&#182;");
    content = content.replace("&middot;", "&#183;");
    content = content.replace("&frac12;", "&#189;");
    content = content.replace("&frac14;", "&#188;");
    content = content.replace("&frac34;", "&#190;");
    content = content.replace("&times;", "&#215;");
    content = content.replace("&divide;", "&#247;");
    return content;
  }
}
