package org.grimmory.epub4j.cfi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bidirectional converter between EPUB Canonical Fragment Identifiers (CFI) and KOReader-style
 * XPointer expressions. Operates on a parsed HTML document represented as a simple DOM.
 *
 * <p>CFI format: {@code epubcfi(/6/<spineStep>!<contentPath>)} where spineStep = (spineIndex + 1) *
 * 2 and contentPath uses even-numbered element indices.
 *
 * <p>XPointer format: {@code /body/DocFragment[<spineIndex+1>]/body/<elementPath>/text().<offset>}
 *
 * <p>This class is designed to work without any specific HTML parser dependency. Users provide a
 * {@link DocumentNavigator} to abstract the document structure.
 */
public class CfiConverter {

  private static final Pattern CFI_PATTERN = Pattern.compile("^epubcfi\\((.+)\\)$");
  private static final Pattern CFI_SPINE_PATTERN = Pattern.compile("^/6/(\\d+)!(.*)$");
  private static final Pattern CFI_PATH_STEP_PATTERN =
      Pattern.compile("/(\\d+)(?:\\[(.*?)\\])?(?::(\\d+))?");
  private static final Pattern XPOINTER_DOC_FRAGMENT_PATTERN =
      Pattern.compile("^/body/DocFragment\\[(\\d+)\\]/body(.*)$");
  private static final Pattern XPOINTER_TEXT_OFFSET_PATTERN =
      Pattern.compile("/text\\(\\)\\.(\\d+)$");
  private static final Pattern XPOINTER_SEGMENT_WITH_INDEX_PATTERN =
      Pattern.compile("^(\\w+)\\[(\\d+)\\]$");
  private static final Pattern XPOINTER_SEGMENT_WITHOUT_INDEX_PATTERN = Pattern.compile("^(\\w+)$");
  private static final Pattern DOC_FRAGMENT_INDEX_PATTERN =
      Pattern.compile("DocFragment\\[(\\d+)\\]");
  private static final Pattern TRAILING_TEXT_OFFSET_PATTERN = Pattern.compile("/text\\(\\).*$");
  private static final Pattern SUFFIX_NODE_OFFSET_PATTERN = Pattern.compile("\\.\\d+$");

  private static final Set<String> INLINE_ELEMENTS =
      Set.of("span", "em", "strong", "i", "b", "u", "small", "mark", "sup", "sub");

  private final DocumentNavigator navigator;
  private final int spineItemIndex;

  /**
   * Creates a converter for the given document and spine index.
   *
   * @param navigator abstraction over the HTML document
   * @param spineIndex the zero-based spine item index this document represents
   */
  public CfiConverter(DocumentNavigator navigator, int spineIndex) {
    this.navigator = navigator;
    this.spineItemIndex = spineIndex;
  }

  /**
   * Extracts the zero-based spine index from a CFI or XPointer string.
   *
   * @param cfiOrXPointer the CFI or XPointer expression
   * @return the zero-based spine index
   * @throws IllegalArgumentException if the format is unrecognized
   */
  public static int extractSpineIndex(String cfiOrXPointer) {
    if (cfiOrXPointer == null || cfiOrXPointer.isBlank()) {
      throw new IllegalArgumentException("CFI/XPointer string cannot be null or empty");
    }
    if (cfiOrXPointer.startsWith("epubcfi(")) {
      return extractSpineIndexFromCfi(cfiOrXPointer);
    } else if (cfiOrXPointer.startsWith("/body/DocFragment[")) {
      return extractSpineIndexFromXPointer(cfiOrXPointer);
    }
    throw new IllegalArgumentException(
        "Unsupported format for spine index extraction: " + cfiOrXPointer);
  }

  /**
   * Normalizes an XPointer by removing text offset components. Useful for progress tracking where
   * character-level precision is not needed.
   */
  public static String normalizeProgressXPointer(String xpointer) {
    if (xpointer == null) {
      return null;
    }
    String result = TRAILING_TEXT_OFFSET_PATTERN.matcher(xpointer).replaceAll("");
    return SUFFIX_NODE_OFFSET_PATTERN.matcher(result).replaceAll("");
  }

  /** Converts a CFI expression to an XPointer result. */
  public XPointerResult cfiToXPointer(String cfi) {
    Matcher cfiMatcher = CFI_PATTERN.matcher(cfi);
    if (!cfiMatcher.matches()) {
      throw new IllegalArgumentException("Invalid CFI format: " + cfi);
    }

    String innerCfi = cfiMatcher.group(1);
    Matcher spineMatcher = CFI_SPINE_PATTERN.matcher(innerCfi);
    if (!spineMatcher.matches()) {
      throw new IllegalArgumentException("Cannot parse CFI spine step: " + cfi);
    }

    int spineStep = Integer.parseInt(spineMatcher.group(1));
    int cfiSpineIndex = (spineStep - 2) / 2;
    if (cfiSpineIndex != spineItemIndex) {
      throw new IllegalArgumentException(
          String.format(
              "CFI spine index %d does not match converter spine index %d",
              cfiSpineIndex, spineItemIndex));
    }

    String contentPath = spineMatcher.group(2);
    CfiPathResult pathResult = parseCfiPath(contentPath);
    Object element = resolveElementFromCfiSteps(pathResult.steps());
    if (element == null) {
      throw new IllegalArgumentException("Element not found for CFI: " + cfi);
    }

    String xpointer;
    if (pathResult.textOffset() != null) {
      xpointer = handleTextOffset(element, pathResult.textOffset());
    } else {
      xpointer = buildXPointerPath(element);
    }

    return new XPointerResult(xpointer, xpointer, xpointer);
  }

  /** Converts an XPointer to a CFI expression. */
  public String xPointerToCfi(String xpointer) {
    return xPointerToCfi(xpointer, null);
  }

  /** Converts an XPointer (or range of two XPointers) to a CFI expression. */
  public String xPointerToCfi(String startXPointer, String endXPointer) {
    if (endXPointer != null && !endXPointer.isBlank()) {
      return convertRangeXPointerToCfi(startXPointer, endXPointer);
    }
    return convertPointXPointerToCfi(startXPointer);
  }

  /** Validates whether a CFI can be resolved against this document. */
  public boolean validateCfi(String cfi) {
    try {
      cfiToXPointer(cfi);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** Validates whether an XPointer can be converted to CFI. */
  public boolean validateXPointer(String xpointer) {
    try {
      xPointerToCfi(xpointer, null);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  // -- CFI parsing --

  private static int extractSpineIndexFromCfi(String cfi) {
    Matcher cfiMatcher = CFI_PATTERN.matcher(cfi);
    if (!cfiMatcher.matches()) {
      throw new IllegalArgumentException("Invalid CFI format: " + cfi);
    }
    Matcher spineMatcher = CFI_SPINE_PATTERN.matcher(cfiMatcher.group(1));
    if (!spineMatcher.matches()) {
      throw new IllegalArgumentException("Cannot extract spine index from CFI: " + cfi);
    }
    int spineStep = Integer.parseInt(spineMatcher.group(1));
    return (spineStep - 2) / 2;
  }

  private static int extractSpineIndexFromXPointer(String xpointer) {
    Matcher matcher = DOC_FRAGMENT_INDEX_PATTERN.matcher(xpointer);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1)) - 1;
    }
    throw new IllegalArgumentException("Cannot extract spine index from XPointer: " + xpointer);
  }

  private static CfiPathResult parseCfiPath(String contentPath) {
    List<CfiStep> steps = new ArrayList<>();
    Integer textOffset = null;

    Matcher stepMatcher = CFI_PATH_STEP_PATTERN.matcher(contentPath);
    while (stepMatcher.find()) {
      int stepIndex = Integer.parseInt(stepMatcher.group(1));
      String assertion = stepMatcher.group(2);
      String offsetStr = stepMatcher.group(3);
      if (offsetStr != null) {
        textOffset = Integer.parseInt(offsetStr);
      }
      steps.add(new CfiStep(stepIndex, assertion));
    }

    return new CfiPathResult(steps, textOffset);
  }

  private Object resolveElementFromCfiSteps(List<CfiStep> steps) {
    Object current = navigator.getBody();
    if (current == null) {
      return null;
    }
    for (CfiStep step : steps) {
      int childIndex = (step.index() / 2) - 1;
      List<Object> children = navigator.getChildElements(current);
      if (childIndex < 0 || childIndex >= children.size()) {
        return current;
      }
      current = children.get(childIndex);
    }
    return current;
  }

  // -- XPointer parsing --

  private String convertPointXPointerToCfi(String xpointer) {
    XPointerParseResult parseResult = parseXPointer(xpointer);
    String cfiPath = buildCfiPathFromElement(parseResult.element());
    if (parseResult.textOffset() != null) {
      cfiPath += "/1:" + parseResult.textOffset();
    }
    return buildFullCfi(cfiPath);
  }

  private String convertRangeXPointerToCfi(String startXPointer, String endXPointer) {
    XPointerParseResult startResult = parseXPointer(startXPointer);
    XPointerParseResult endResult = parseXPointer(endXPointer);

    String startPath = buildCfiPathFromElement(startResult.element());
    String endPath = buildCfiPathFromElement(endResult.element());

    if (startResult.textOffset() != null) {
      startPath += "/1:" + startResult.textOffset();
    }
    if (endResult.textOffset() != null) {
      endPath += "/1:" + endResult.textOffset();
    }

    if (!startPath.equals(endPath)) {
      return buildFullCfi(startPath + "," + endPath);
    }
    return buildFullCfi(startPath);
  }

  private XPointerParseResult parseXPointer(String xpointer) {
    Matcher textOffsetMatcher = XPOINTER_TEXT_OFFSET_PATTERN.matcher(xpointer);
    Integer textOffset = null;
    String elementPath = xpointer;

    if (textOffsetMatcher.find()) {
      textOffset = Integer.parseInt(textOffsetMatcher.group(1));
      elementPath = XPOINTER_TEXT_OFFSET_PATTERN.matcher(xpointer).replaceAll("");
    }

    Object element = resolveXPointerPath(elementPath);
    if (element == null) {
      throw new IllegalArgumentException("Cannot resolve XPointer path: " + elementPath);
    }

    return new XPointerParseResult(element, textOffset);
  }

  private Object resolveXPointerPath(String path) {
    Matcher pathMatcher = XPOINTER_DOC_FRAGMENT_PATTERN.matcher(path);
    if (!pathMatcher.matches()) {
      throw new IllegalArgumentException("Invalid XPointer format: " + path);
    }

    String elementPath = pathMatcher.group(2);
    Object body = navigator.getBody();
    if (body == null) {
      throw new IllegalArgumentException("Document has no body element");
    }
    if (elementPath == null || elementPath.isEmpty()) {
      return body;
    }

    // KOReader counts elements globally in the document for indexed segments
    String[] segments = elementPath.split("/");
    String lastSegment = null;
    for (int i = segments.length - 1; i >= 0; i--) {
      if (!segments[i].isEmpty()) {
        lastSegment = segments[i];
        break;
      }
    }
    if (lastSegment == null) {
      return body;
    }

    Matcher withIndexMatcher = XPOINTER_SEGMENT_WITH_INDEX_PATTERN.matcher(lastSegment);
    if (withIndexMatcher.matches()) {
      String tagName = withIndexMatcher.group(1);
      int index = Integer.parseInt(withIndexMatcher.group(2)) - 1;
      List<Object> allElements = navigator.getElementsByTag(body, tagName);
      if (index < allElements.size()) {
        return allElements.get(index);
      }
      throw new IllegalArgumentException(
          String.format(
              "Element index %d out of bounds for tag %s (found %d in document)",
              index, tagName, allElements.size()));
    }

    // Fallback: hierarchical traversal for non-indexed segments
    Object current = body;
    for (String segment : segments) {
      if (segment.isEmpty()) {
        continue;
      }
      Matcher segWithIndex = XPOINTER_SEGMENT_WITH_INDEX_PATTERN.matcher(segment);
      Matcher segWithoutIndex = XPOINTER_SEGMENT_WITHOUT_INDEX_PATTERN.matcher(segment);

      String tagName;
      int index;
      if (segWithIndex.matches()) {
        tagName = segWithIndex.group(1);
        index = Integer.parseInt(segWithIndex.group(2)) - 1;
      } else if (segWithoutIndex.matches()) {
        tagName = segWithoutIndex.group(1);
        index = 0;
      } else {
        throw new IllegalArgumentException("Invalid XPointer segment: " + segment);
      }

      List<Object> matchingChildren = new ArrayList<>();
      for (Object child : navigator.getChildElements(current)) {
        if (navigator.getTagName(child).equalsIgnoreCase(tagName)) {
          matchingChildren.add(child);
        }
      }
      if (index < matchingChildren.size()) {
        current = matchingChildren.get(index);
      } else {
        throw new IllegalArgumentException(
            String.format(
                "Element index %d out of bounds for tag %s (found %d)",
                index, tagName, matchingChildren.size()));
      }
    }
    return current;
  }

  // -- Path building --

  private String buildCfiPathFromElement(Object element) {
    List<String> pathParts = new ArrayList<>();
    Object current = element;

    while (current != null && !"body".equalsIgnoreCase(navigator.getTagName(current))) {
      Object parent = navigator.getParent(current);
      if (parent == null) {
        break;
      }
      int siblingIndex = 0;
      for (Object sibling : navigator.getChildElements(parent)) {
        siblingIndex++;
        if (sibling == current) {
          break;
        }
      }
      pathParts.addFirst("/" + (siblingIndex * 2));
      current = parent;
    }

    pathParts.addFirst("/4");
    return String.join("", pathParts);
  }

  private String buildXPointerPath(Object targetElement) {
    List<String> pathParts = new ArrayList<>();
    Object current = targetElement;
    Object body = navigator.getBody();
    Object root = body != null ? navigator.getParent(body) : null;

    while (current != null && current != root) {
      Object parent = navigator.getParent(current);
      if (parent == null) {
        break;
      }
      String tagName = navigator.getTagName(current).toLowerCase();
      int siblingIndex = 0;
      int totalSameTag = 0;

      for (Object sibling : navigator.getChildElements(parent)) {
        if (navigator.getTagName(sibling).equalsIgnoreCase(tagName)) {
          if (sibling == current) {
            siblingIndex = totalSameTag;
          }
          totalSameTag++;
        }
      }
      if (totalSameTag == 1) {
        pathParts.addFirst(tagName);
      } else {
        pathParts.addFirst(String.format("%s[%d]", tagName, siblingIndex + 1));
      }
      current = parent;
    }

    StringBuilder xpointer =
        new StringBuilder("/body/DocFragment[").append(spineItemIndex + 1).append("]");

    if (!pathParts.isEmpty() && pathParts.getFirst().startsWith("body")) {
      pathParts.removeFirst();
    }
    xpointer.append("/body");
    if (!pathParts.isEmpty()) {
      xpointer.append("/").append(String.join("/", pathParts));
    }
    return xpointer.toString();
  }

  private String handleTextOffset(Object element, int cfiOffset) {
    List<String> textNodes = navigator.collectTextContent(element);

    int totalChars = 0;
    int offsetInNode = 0;
    boolean found = false;

    // Walk text nodes to find which one contains the offset
    for (String nodeText : textNodes) {
      int nodeLength = nodeText.length();
      if (totalChars + nodeLength >= cfiOffset) {
        offsetInNode = cfiOffset - totalChars;
        found = true;
        break;
      }
      totalChars += nodeLength;
    }

    if (!found) {
      return buildXPointerPath(element);
    }

    // Walk up to a significant (non-inline) element
    Object current = element;
    while (current != null
        && INLINE_ELEMENTS.contains(navigator.getTagName(current).toLowerCase())) {
      Object parent = navigator.getParent(current);
      if (parent == null) {
        break;
      }
      current = parent;
    }

    String basePath = buildXPointerPath(current != null ? current : element);
    return basePath + "/text()." + offsetInNode;
  }

  private String buildFullCfi(String contentPath) {
    int spineStep = (spineItemIndex + 1) * 2;
    return String.format("epubcfi(/6/%d!%s)", spineStep, contentPath);
  }

  // -- Records --

  private record CfiStep(int index, String assertion) {}

  private record CfiPathResult(List<CfiStep> steps, Integer textOffset) {}

  private record XPointerParseResult(Object element, Integer textOffset) {}
}
