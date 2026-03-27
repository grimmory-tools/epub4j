package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;

/**
 * Estimates word count and reading time for EPUB books. Strips HTML markup and counts
 * whitespace-separated tokens.
 */
public class WordCountEstimator {

  private static final System.Logger log = System.getLogger(WordCountEstimator.class.getName());

  /** Default words per minute for reading time calculation. */
  public static final int DEFAULT_WPM = 250;

  // Patterns for stripping HTML
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  private static final Pattern HTML_ENTITY =
      Pattern.compile("&[a-zA-Z]+;|&#\\d+;|&#x[0-9a-fA-F]+;");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern STYLE_BLOCK_PATTERN =
      Pattern.compile("(?si)<style[^>]*>.*?</style>");
  private static final Pattern SCRIPT_BLOCK_PATTERN =
      Pattern.compile("(?si)<script[^>]*>.*?</script>");

  /**
   * Result of a word count estimation.
   *
   * @param wordCount estimated total word count
   * @param charCount estimated total character count (excluding markup)
   * @param readingTimeMinutes estimated reading time in minutes at given WPM
   * @param resourcesProcessed number of XHTML resources processed
   */
  public record WordCountResult(
      long wordCount, long charCount, int readingTimeMinutes, int resourcesProcessed) {}

  /**
   * Estimate word count and reading time for the entire book. Processes all XHTML resources in the
   * spine.
   *
   * @param book the book to analyze
   * @return word count result
   */
  public static WordCountResult estimate(Book book) {
    return estimate(book, DEFAULT_WPM);
  }

  /**
   * Estimate word count and reading time for the entire book.
   *
   * @param book the book to analyze
   * @param wordsPerMinute reading speed for time calculation
   * @return word count result
   */
  public static WordCountResult estimate(Book book, int wordsPerMinute) {
    if (wordsPerMinute <= 0) wordsPerMinute = DEFAULT_WPM;

    long totalWords = 0;
    long totalChars = 0;
    int processed = 0;

    Spine spine = book.getSpine();
    for (int i = 0; i < spine.size(); i++) {
      Resource resource = spine.getResource(i);
      if (resource == null || resource.getMediaType() != MediaTypes.XHTML) {
        continue;
      }

      try {
        String plainText = extractPlainText(resource);
        totalChars += plainText.length();
        totalWords += countWords(plainText);
        processed++;
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Skipping unreadable resource: " + resource.getHref());
      }
    }

    int readingTime = (int) Math.ceil((double) totalWords / wordsPerMinute);
    return new WordCountResult(totalWords, totalChars, readingTime, processed);
  }

  /**
   * Estimate word count for a single resource.
   *
   * @param resource the XHTML resource
   * @return word count, or 0 if the resource is not readable
   */
  public static long estimateResource(Resource resource) {
    try {
      return countWords(extractPlainText(resource));
    } catch (IOException e) {
      return 0;
    }
  }

  /** Extract plain text from an XHTML resource by stripping all HTML markup. */
  static String extractPlainText(Resource resource) throws IOException {
    byte[] data = resource.getData();
    if (data == null || data.length == 0) return "";

    String content = new String(data, StandardCharsets.UTF_8);

    // Remove script and style blocks entirely
    content = SCRIPT_BLOCK_PATTERN.matcher(content).replaceAll(" ");
    content = STYLE_BLOCK_PATTERN.matcher(content).replaceAll(" ");

    // Remove all HTML tags
    content = HTML_TAG.matcher(content).replaceAll(" ");

    // Decode common HTML entities
    content = HTML_ENTITY.matcher(content).replaceAll(" ");

    // Collapse whitespace
    content = WHITESPACE.matcher(content.trim()).replaceAll(" ");

    return content;
  }

  /** Count words in plain text (whitespace-separated tokens). */
  static long countWords(String plainText) {
    if (plainText == null || plainText.isBlank()) return 0;
    String[] tokens = WHITESPACE.split(plainText.trim());
    long count = 0;
    for (String token : tokens) {
      // Only count tokens that contain at least one letter or digit
      if (!token.isEmpty() && token.chars().anyMatch(Character::isLetterOrDigit)) {
        count++;
      }
    }
    return count;
  }
}
