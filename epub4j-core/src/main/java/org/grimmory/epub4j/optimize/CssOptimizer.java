package org.grimmory.epub4j.optimize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.SpineReference;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Removes unused CSS rules from embedded stylesheets by matching selectors against the set of
 * element names, classes, and IDs found in spine XHTML documents.
 *
 * <p>This is a conservative approach: rules whose selectors reference names not found in any spine
 * document are removed. Complex selectors (pseudo-classes, attribute selectors) are kept.
 */
public class CssOptimizer implements BookProcessor {

  private static final System.Logger log = System.getLogger(CssOptimizer.class.getName());

  // Matches class="..." and id="..." in HTML
  private static final Pattern CLASS_PATTERN =
      Pattern.compile("class\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern ID_PATTERN =
      Pattern.compile("id\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  // Matches opening HTML tags to collect element names
  private static final Pattern TAG_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)\\b");

  // Matches a CSS rule block: selector { ... }
  private static final Pattern CSS_RULE_PATTERN =
      Pattern.compile("([^{}@]+)\\{([^{}]*)}", Pattern.DOTALL);

  // Matches simple class, ID, or element selectors in a selector string
  private static final Pattern SELECTOR_CLASS = Pattern.compile("\\.([a-zA-Z_][a-zA-Z0-9_-]*)");
  private static final Pattern SELECTOR_ID = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_-]*)");
  private static final Pattern SELECTOR_ELEMENT =
      Pattern.compile("(?:^|[\\s,>+~])([a-zA-Z][a-zA-Z0-9]*)");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Override
  public Book processBook(Book book) {
    Set<String> usedElements = new HashSet<>();
    Set<String> usedClasses = new HashSet<>();
    Set<String> usedIds = new HashSet<>();

    collectUsedSelectors(book, usedElements, usedClasses, usedIds);

    if (usedElements.isEmpty() && usedClasses.isEmpty() && usedIds.isEmpty()) {
      return book;
    }

    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.CSS) {
        continue;
      }
      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) {
          continue;
        }
        String css = new String(data, StandardCharsets.UTF_8);
        String optimized = removeUnusedRules(css, usedElements, usedClasses, usedIds);
        if (optimized.length() < css.length()) {
          resource.setData(optimized.getBytes(StandardCharsets.UTF_8));
          log.log(
              System.Logger.Level.DEBUG,
              "Optimized CSS "
                  + resource.getHref()
                  + ": "
                  + css.length()
                  + " -> "
                  + optimized.length()
                  + " chars");
        }
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Could not read CSS for optimization: " + resource.getHref());
      }
    }

    return book;
  }

  private static void collectUsedSelectors(
      Book book, Set<String> elements, Set<String> classes, Set<String> ids) {
    for (SpineReference ref : book.getSpine().getSpineReferences()) {
      Resource resource = ref.getResource();
      if (resource == null) {
        continue;
      }
      try {
        byte[] data = resource.getData();
        if (data == null) {
          continue;
        }
        String content = new String(data, StandardCharsets.UTF_8);

        Matcher tagMatcher = TAG_PATTERN.matcher(content);
        while (tagMatcher.find()) {
          elements.add(tagMatcher.group(1).toLowerCase());
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
          for (String cls : WHITESPACE.split(classMatcher.group(1))) {
            if (!cls.isEmpty()) {
              classes.add(cls);
            }
          }
        }

        Matcher idMatcher = ID_PATTERN.matcher(content);
        while (idMatcher.find()) {
          ids.add(idMatcher.group(1));
        }
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Could not read spine item for CSS optimization: " + resource.getHref());
      }
    }
  }

  private static String removeUnusedRules(
      String css, Set<String> usedElements, Set<String> usedClasses, Set<String> usedIds) {
    StringBuilder result = new StringBuilder(css.length());
    Matcher ruleMatcher = CSS_RULE_PATTERN.matcher(css);
    int lastEnd = 0;

    while (ruleMatcher.find()) {
      // Preserve text before this rule (comments, @-rules, whitespace)
      result.append(css, lastEnd, ruleMatcher.start());

      String selector = ruleMatcher.group(1).trim();
      String body = ruleMatcher.group(2);

      if (isSelectorUsed(selector, usedElements, usedClasses, usedIds)) {
        result.append(selector).append(" {").append(body).append('}');
      }

      lastEnd = ruleMatcher.end();
    }

    // Preserve trailing content
    result.append(css, lastEnd, css.length());
    return result.toString();
  }

  private static boolean isSelectorUsed(
      String selector, Set<String> usedElements, Set<String> usedClasses, Set<String> usedIds) {
    // Keep @-rules, pseudo-elements, and complex selectors unconditionally
    if (selector.startsWith("@") || selector.contains("::") || selector.contains("[")) {
      return true;
    }

    // Check if any referenced class is used
    Matcher classMatcher = SELECTOR_CLASS.matcher(selector);
    while (classMatcher.find()) {
      if (!usedClasses.contains(classMatcher.group(1))) {
        return false;
      }
    }

    // Check if any referenced ID is used
    Matcher idMatcher = SELECTOR_ID.matcher(selector);
    while (idMatcher.find()) {
      if (!usedIds.contains(idMatcher.group(1))) {
        return false;
      }
    }

    // Check element selectors
    Matcher elementMatcher = SELECTOR_ELEMENT.matcher(selector);
    while (elementMatcher.find()) {
      String element = elementMatcher.group(1).toLowerCase();
      // Skip CSS pseudo-classes and universal selectors
      if (element.equals("not") || element.equals("nth") || element.equals("has")) {
        continue;
      }
      if (!usedElements.contains(element)) {
        return false;
      }
    }

    return true;
  }
}
