package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;

/**
 * Search and replace across all XHTML resources in an EPUB book. Supports plain text and regex
 * patterns with match reporting.
 */
public class SearchReplace {

  private static final System.Logger log = System.getLogger(SearchReplace.class.getName());

  /**
   * A single match found during a search.
   *
   * @param resourceHref the resource containing the match
   * @param lineNumber approximate line number (1-based)
   * @param matchText the matched text
   * @param contextBefore text before the match (up to 30 chars)
   * @param contextAfter text after the match (up to 30 chars)
   */
  public record SearchMatch(
      String resourceHref,
      int lineNumber,
      String matchText,
      String contextBefore,
      String contextAfter) {}

  /**
   * Result of a search or replace operation.
   *
   * @param matches the list of matches found
   * @param resourcesAffected number of resources that contained matches
   * @param totalReplacements number of replacements made (0 for search-only)
   */
  public record SearchResult(
      List<SearchMatch> matches, int resourcesAffected, int totalReplacements) {
    public int matchCount() {
      return matches.size();
    }
  }

  /**
   * Search all XHTML resources for a plain text string.
   *
   * @param book the book to search
   * @param searchText the text to find
   * @param caseSensitive whether the search is case-sensitive
   * @return search result with all matches
   */
  public static SearchResult search(Book book, String searchText, boolean caseSensitive) {
    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
    Pattern pattern = Pattern.compile(Pattern.quote(searchText), flags);
    return searchWithPattern(book, pattern);
  }

  /**
   * Search all XHTML resources using a regex pattern.
   *
   * @param book the book to search
   * @param regex the regex pattern
   * @param flags regex flags (e.g., Pattern.CASE_INSENSITIVE)
   * @return search result with all matches
   */
  public static SearchResult searchRegex(Book book, String regex, int flags) {
    Pattern pattern = Pattern.compile(regex, flags);
    return searchWithPattern(book, pattern);
  }

  /**
   * Replace all occurrences of a plain text string in all XHTML resources.
   *
   * @param book the book to modify
   * @param searchText the text to find
   * @param replacement the replacement text
   * @param caseSensitive whether the search is case-sensitive
   * @return result with match details and replacement count
   */
  public static SearchResult replaceAll(
      Book book, String searchText, String replacement, boolean caseSensitive) {
    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
    Pattern pattern = Pattern.compile(Pattern.quote(searchText), flags);
    return replaceWithPattern(book, pattern, replacement);
  }

  /**
   * Replace all occurrences matching a regex in all XHTML resources.
   *
   * @param book the book to modify
   * @param regex the regex pattern
   * @param replacement the replacement string (supports $1, $2 backreferences)
   * @param flags regex flags
   */
  public static void replaceAllRegex(Book book, String regex, String replacement, int flags) {
    Pattern pattern = Pattern.compile(regex, flags);
    replaceWithPattern(book, pattern, replacement);
  }

  /**
   * Replace in a single resource.
   *
   * @param resource the resource to modify
   * @param searchText the text to find
   * @param replacement the replacement text
   * @param caseSensitive case sensitivity
   * @return number of replacements made
   */
  public static int replaceInResource(
      Resource resource, String searchText, String replacement, boolean caseSensitive) {
    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
    Pattern pattern = Pattern.compile(Pattern.quote(searchText), flags);
    return replaceInSingleResource(resource, pattern, replacement);
  }

  private static SearchResult searchWithPattern(Book book, Pattern pattern) {
    List<SearchMatch> allMatches = new ArrayList<>();
    Set<String> affectedResources = new HashSet<>();

    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.XHTML) continue;

      try {
        String content = new String(resource.getData(), StandardCharsets.UTF_8);
        List<SearchMatch> resourceMatches = findMatches(resource.getHref(), content, pattern);
        if (!resourceMatches.isEmpty()) {
          allMatches.addAll(resourceMatches);
          affectedResources.add(resource.getHref());
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Skipping unreadable resource: " + resource.getHref());
      }
    }

    return new SearchResult(List.copyOf(allMatches), affectedResources.size(), 0);
  }

  private static SearchResult replaceWithPattern(Book book, Pattern pattern, String replacement) {
    List<SearchMatch> allMatches = new ArrayList<>();
    Set<String> affectedResources = new HashSet<>();
    int totalReplacements = 0;

    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.XHTML) continue;

      try {
        String content = new String(resource.getData(), StandardCharsets.UTF_8);
        List<SearchMatch> resourceMatches = findMatches(resource.getHref(), content, pattern);

        if (!resourceMatches.isEmpty()) {
          allMatches.addAll(resourceMatches);
          affectedResources.add(resource.getHref());
          totalReplacements += resourceMatches.size();

          String replaced = pattern.matcher(content).replaceAll(replacement);
          resource.setData(replaced.getBytes(StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Skipping unreadable resource: " + resource.getHref());
      }
    }

    return new SearchResult(List.copyOf(allMatches), affectedResources.size(), totalReplacements);
  }

  private static int replaceInSingleResource(
      Resource resource, Pattern pattern, String replacement) {
    try {
      String content = new String(resource.getData(), StandardCharsets.UTF_8);
      Matcher matcher = pattern.matcher(content);
      int count = 0;
      while (matcher.find()) count++;
      if (count > 0) {
        String replaced = pattern.matcher(content).replaceAll(replacement);
        resource.setData(replaced.getBytes(StandardCharsets.UTF_8));
      }
      return count;
    } catch (IOException e) {
      return 0;
    }
  }

  private static List<SearchMatch> findMatches(String href, String content, Pattern pattern) {
    List<SearchMatch> matches = new ArrayList<>();
    Matcher matcher = pattern.matcher(content);
    while (matcher.find()) {
      int lineNum = countLines(content, matcher.start());
      String matchText = matcher.group();
      String before = content.substring(Math.max(0, matcher.start() - 30), matcher.start());
      String after =
          content.substring(matcher.end(), Math.min(content.length(), matcher.end() + 30));
      matches.add(new SearchMatch(href, lineNum, matchText, before, after));
    }
    return matches;
  }

  private static int countLines(String content, int position) {
    int lines = 1;
    for (int i = 0; i < position && i < content.length(); i++) {
      if (content.charAt(i) == '\n') lines++;
    }
    return lines;
  }
}
