package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.util.StringUtil;

class LinkGraphRepair {

  private static final System.Logger log = System.getLogger(LinkGraphRepair.class.getName());

  static final Pattern HREF_SRC_TARGET_PATTERN =
      Pattern.compile("(?is)(?:href|src)\\s*=\\s*[\"']([^\"'#]+)[\"']");
  static final Pattern XHTML_LINK_ATTR_PATTERN =
      Pattern.compile("(?is)\\b(href|src)\\s*=\\s*([\"'])([^\"']+)\\2");
  static final Pattern CSS_URL_PATTERN = Pattern.compile("(?is)url\\(\\s*[\"']?([^\"'\\)#]+)");
  static final Pattern CSS_IMPORT_PATTERN =
      Pattern.compile("(?is)@import\\s+(?:url\\(\\s*)?[\"']([^\"']+)[\"']");

  private record ResourceAliasIndex(
      Map<String, Resource> canonicalIndex,
      Map<String, Resource> canonicalLowerIndex,
      Map<String, List<Resource>> basenameIndex) {}

  static void repairInternalLinkGraph(Book book, List<BookRepair.RepairAction> actions) {
    Resources resources = book.getResources();
    ResourceAliasIndex aliasIndex = buildAliasIndex(resources);
    for (Resource resource : resources.getAll()) {
      MediaType mediaType = resource.getMediaType();
      if (mediaType != MediaTypes.XHTML && mediaType != MediaTypes.CSS) {
        continue;
      }

      try {
        String content = new String(resource.getData(), StandardCharsets.UTF_8);
        String basePath = getBasePath(resource.getHref());
        RewriteResult result =
            mediaType == MediaTypes.XHTML
                ? rewriteXhtmlLinks(
                    content, basePath, resources, aliasIndex, resource.getHref(), actions)
                : rewriteCssLinks(
                    content, basePath, resources, aliasIndex, resource.getHref(), actions);

        if (result.changed()) {
          content = result.content();
          resource.setData(content.getBytes(StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG, "Failed to repair internal links in: " + resource.getHref());
      }
    }
  }

  private static RewriteResult rewriteXhtmlLinks(
      String content,
      String basePath,
      Resources resources,
      ResourceAliasIndex aliasIndex,
      String sourceHref,
      List<BookRepair.RepairAction> actions) {
    Matcher matcher = XHTML_LINK_ATTR_PATTERN.matcher(content);
    StringBuilder out = new StringBuilder(content.length());
    boolean changed = false;

    while (matcher.find()) {
      String attribute = matcher.group(1);
      String quote = matcher.group(2);
      String rawTarget = matcher.group(3);

      String rewritten =
          maybeRewriteInternalTarget(
              rawTarget, basePath, resources, aliasIndex, sourceHref, actions);
      if (rewritten != null && !rewritten.equals(rawTarget)) {
        String replacement = attribute + "=" + quote + rewritten + quote;
        matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        changed = true;
      } else {
        matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
      }
    }
    matcher.appendTail(out);
    return new RewriteResult(out.toString(), changed);
  }

  private static RewriteResult rewriteCssLinks(
      String content,
      String basePath,
      Resources resources,
      ResourceAliasIndex aliasIndex,
      String sourceHref,
      List<BookRepair.RepairAction> actions) {
    String rewritten =
        rewriteCssPattern(content, basePath, resources, aliasIndex, sourceHref, actions);
    return rewriteCssPatternResult(
        rewritten, CSS_IMPORT_PATTERN, basePath, resources, aliasIndex, sourceHref, actions);
  }

  private static String rewriteCssPattern(
      String content,
      String basePath,
      Resources resources,
      ResourceAliasIndex aliasIndex,
      String sourceHref,
      List<BookRepair.RepairAction> actions) {
    return rewriteCssPatternResult(
            content,
            LinkGraphRepair.CSS_URL_PATTERN,
            basePath,
            resources,
            aliasIndex,
            sourceHref,
            actions)
        .content();
  }

  private static RewriteResult rewriteCssPatternResult(
      String content,
      Pattern pattern,
      String basePath,
      Resources resources,
      ResourceAliasIndex aliasIndex,
      String sourceHref,
      List<BookRepair.RepairAction> actions) {
    Matcher matcher = pattern.matcher(content);
    StringBuilder out = new StringBuilder(content.length());
    boolean changed = false;

    while (matcher.find()) {
      String rawTarget = matcher.group(1);
      String rewritten =
          maybeRewriteInternalTarget(
              rawTarget, basePath, resources, aliasIndex, sourceHref, actions);
      if (rewritten != null && !rewritten.equals(rawTarget)) {
        String replacement = matcher.group(0).replace(rawTarget, rewritten);
        matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        changed = true;
      } else {
        matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
      }
    }
    matcher.appendTail(out);
    return new RewriteResult(out.toString(), changed);
  }

  private static String maybeRewriteInternalTarget(
      String rawTarget,
      String basePath,
      Resources resources,
      ResourceAliasIndex aliasIndex,
      String sourceHref,
      List<BookRepair.RepairAction> actions) {
    if (StringUtil.isBlank(rawTarget)) {
      return null;
    }

    String target = rawTarget.trim();
    if (target.startsWith("#")
        || target.startsWith("http://")
        || target.startsWith("https://")
        || target.startsWith("mailto:")
        || target.startsWith("data:")
        || target.startsWith("javascript:")) {
      return null;
    }

    int splitPos = indexOfQueryOrFragment(target);
    String pathPart = splitPos >= 0 ? target.substring(0, splitPos) : target;
    String suffix = splitPos >= 0 ? target.substring(splitPos) : "";
    if (StringUtil.isBlank(pathPart)) {
      return null;
    }

    String resolved = normalizeRelativeHref(basePath, pathPart);
    if (resources.containsByHref(resolved) || resources.containsByHref(pathPart)) {
      return null;
    }

    Resource alias = resolveResourceAlias(pathPart, basePath, aliasIndex);
    if (alias == null) {
      return null;
    }

    String rewrittenPath = relativizeHref(basePath, alias.getHref());
    String rewritten = rewrittenPath + suffix;

    actions.add(
        new BookRepair.RepairAction(
            BookRepair.RepairCode.LINK_REWRITTEN,
            "Rewrote broken internal link in "
                + sourceHref
                + ": '"
                + rawTarget
                + "' -> '"
                + rewritten
                + "'",
            BookRepair.RepairAction.Severity.FIX));
    return rewritten;
  }

  static Resource resolveResourceAlias(Book book, String target, String basePath) {
    return resolveResourceAlias(target, basePath, buildAliasIndex(book.getResources()));
  }

  private static ResourceAliasIndex buildAliasIndex(Resources resources) {
    Collection<Resource> allResources = resources.getAll();
    int expected = Math.max(16, allResources.size() * 2);
    Map<String, Resource> canonicalIndex = new HashMap<>(expected);
    Map<String, Resource> canonicalLowerIndex = new HashMap<>(expected);
    Map<String, List<Resource>> basenameIndex = new HashMap<>(expected);

    for (Resource resource : allResources) {
      if (resource == null || StringUtil.isBlank(resource.getHref())) {
        continue;
      }
      String canonical = canonicalizeHref(resource.getHref());
      canonicalIndex.putIfAbsent(canonical, resource);
      canonicalLowerIndex.putIfAbsent(canonical.toLowerCase(Locale.ROOT), resource);

      String basename =
          StringUtil.substringAfterLast(resource.getHref(), '/').toLowerCase(Locale.ROOT);
      basenameIndex.computeIfAbsent(basename, k -> new ArrayList<>(1)).add(resource);
    }

    return new ResourceAliasIndex(canonicalIndex, canonicalLowerIndex, basenameIndex);
  }

  private static Resource resolveResourceAlias(
      String target, String basePath, ResourceAliasIndex aliasIndex) {
    if (StringUtil.isBlank(target)) {
      return null;
    }

    List<String> candidates = new ArrayList<>(4);
    candidates.add(target);
    candidates.add(normalizeRelativeHref(basePath, target));
    String decoded = safeDecode(target);
    if (!decoded.equals(target)) {
      candidates.add(decoded);
      candidates.add(normalizeRelativeHref(basePath, decoded));
    }

    for (String candidate : candidates) {
      String canonical = canonicalizeHref(candidate);
      Resource direct = aliasIndex.canonicalIndex().get(canonical);
      if (direct != null) {
        return direct;
      }
      Resource lower = aliasIndex.canonicalLowerIndex().get(canonical.toLowerCase(Locale.ROOT));
      if (lower != null) {
        return lower;
      }
    }

    String basename = StringUtil.substringAfterLast(target, '/').toLowerCase(Locale.ROOT);
    List<Resource> basenameMatches = aliasIndex.basenameIndex().getOrDefault(basename, List.of());
    if (basenameMatches.size() == 1) {
      return basenameMatches.getFirst();
    }

    return null;
  }

  private static int indexOfQueryOrFragment(String target) {
    int q = target.indexOf('?');
    int h = target.indexOf('#');
    if (q < 0) {
      return h;
    }
    if (h < 0) {
      return q;
    }
    return Math.min(q, h);
  }

  private static String canonicalizeHref(String href) {
    if (href == null) {
      return "";
    }
    String normalized = safeDecode(href);
    normalized = normalized.replace('\\', '/');
    if (normalized.contains("..") || normalized.contains("./")) {
      normalized = StringUtil.collapsePathDots(normalized);
    }
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return StringUtil.substringBefore(normalized, '#');
  }

  private static String safeDecode(String href) {
    try {
      return URLDecoder.decode(href, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return href;
    }
  }

  private static String relativizeHref(String basePath, String targetHref) {
    if (StringUtil.isBlank(targetHref)) {
      return targetHref;
    }
    List<String> baseSegments = splitSegments(basePath);
    List<String> targetSegments = splitSegments(targetHref);
    if (targetSegments.isEmpty()) {
      return targetHref;
    }

    int common = 0;
    while (common < baseSegments.size()
        && common < targetSegments.size() - 1
        && baseSegments.get(common).equalsIgnoreCase(targetSegments.get(common))) {
      common++;
    }

    int capacity = Math.max(16, targetHref.length() + ((baseSegments.size() - common) * 3));
    StringBuilder result = new StringBuilder(capacity);
    result.repeat("../", Math.max(0, baseSegments.size() - common));
    for (int i = common; i < targetSegments.size(); i++) {
      result.append(targetSegments.get(i));
      if (i < targetSegments.size() - 1) {
        result.append('/');
      }
    }

    String rel = result.toString();
    return rel.isEmpty() ? StringUtil.substringAfterLast(targetHref, '/') : rel;
  }

  private static List<String> splitSegments(String path) {
    int estimatedSegments = 1;
    for (int i = 0; i < path.length(); i++) {
      if (path.charAt(i) == '/') {
        estimatedSegments++;
      }
    }
    List<String> segments = new ArrayList<>(Math.max(4, estimatedSegments));
    if (StringUtil.isBlank(path)) {
      return segments;
    }
    String normalized = canonicalizeHref(path);
    for (String segment : normalized.split("/")) {
      if (StringUtil.isNotBlank(segment)) {
        segments.add(segment);
      }
    }
    return segments;
  }

  private static String getBasePath(String href) {
    int slash = href.lastIndexOf('/');
    if (slash < 0) {
      return "";
    }
    return href.substring(0, slash + 1);
  }

  private static String normalizeRelativeHref(String basePath, String target) {
    String candidate = target;
    if (!target.startsWith("/")) {
      candidate = basePath + target;
    }
    candidate = candidate.replace('\\', '/');
    candidate = StringUtil.collapsePathDots(candidate);
    while (candidate.startsWith("/")) {
      candidate = candidate.substring(1);
    }
    return candidate;
  }

  record RewriteResult(String content, boolean changed) {}
}
