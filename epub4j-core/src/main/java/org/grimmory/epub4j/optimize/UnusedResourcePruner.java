package org.grimmory.epub4j.optimize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.SpineReference;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Removes resources (images, fonts, stylesheets) that are not referenced by any spine document or
 * CSS file. Mirrors Calibre's {@code remove_unused_images()} algorithm.
 *
 * <p>The algorithm collects all hrefs referenced in spine XHTML via src/href attributes and in CSS
 * via url() values, then removes manifest entries that are not referenced.
 */
public class UnusedResourcePruner implements BookProcessor {

  private static final System.Logger log = System.getLogger(UnusedResourcePruner.class.getName());

  private static final Pattern HREF_SRC_PATTERN =
      Pattern.compile("(?:href|src)\\s*=\\s*[\"']([^\"'#]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CSS_URL_PATTERN =
      Pattern.compile("url\\(\\s*[\"']?([^\"')#]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CSS_IMPORT_PATTERN =
      Pattern.compile("@import\\s+(?:url\\(\\s*)?[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  @Override
  public Book processBook(Book book) {
    Set<String> referencedHrefs = collectReferencedHrefs(book);
    int removed = 0;

    // Build a list of hrefs to remove (avoid concurrent modification)
    Set<String> toRemove = new HashSet<>();
    for (Resource resource : book.getResources().getAll()) {
      MediaType mt = resource.getMediaType();
      // Only prune media resources, not spine documents or the NCX/NAV
      if (!isPrunableMediaType(mt)) {
        continue;
      }
      if (!isReferenced(resource.getHref(), referencedHrefs)) {
        toRemove.add(resource.getHref());
      }
    }

    for (String href : toRemove) {
      book.getResources().remove(href);
      removed++;
      log.log(System.Logger.Level.DEBUG, "Removed unused resource: " + href);
    }

    if (removed > 0) {
      log.log(System.Logger.Level.DEBUG, "Pruned " + removed + " unused resources");
    }

    return book;
  }

  private static Set<String> collectReferencedHrefs(Book book) {
    Set<String> referenced = new HashSet<>();

    // Spine documents and their references
    for (SpineReference spineRef : book.getSpine().getSpineReferences()) {
      Resource resource = spineRef.getResource();
      if (resource == null) {
        continue;
      }
      referenced.add(resource.getHref());
      collectRefsFromContent(resource, referenced);
    }

    // CSS files referenced by spine docs
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() == MediaTypes.CSS) {
        if (referenced.contains(resource.getHref())) {
          collectRefsFromCss(resource, referenced);
        }
      }
    }

    // Cover image is always referenced
    if (book.getCoverImage() != null) {
      referenced.add(book.getCoverImage().getHref());
    }

    return referenced;
  }

  private static void collectRefsFromContent(Resource resource, Set<String> referenced) {
    try {
      byte[] data = resource.getData();
      if (data == null || data.length == 0) {
        return;
      }
      String content = new String(data, StandardCharsets.UTF_8);
      String basePath = getBasePath(resource.getHref());

      Matcher matcher = HREF_SRC_PATTERN.matcher(content);
      while (matcher.find()) {
        String ref = resolveHref(basePath, matcher.group(1).trim());
        referenced.add(ref);
      }

      // Inline CSS url() references
      Matcher cssUrlMatcher = CSS_URL_PATTERN.matcher(content);
      while (cssUrlMatcher.find()) {
        String ref = resolveHref(basePath, cssUrlMatcher.group(1).trim());
        referenced.add(ref);
      }
    } catch (IOException e) {
      log.log(
          System.Logger.Level.DEBUG, "Could not read resource for pruning: " + resource.getHref());
    }
  }

  private static void collectRefsFromCss(Resource resource, Set<String> referenced) {
    try {
      byte[] data = resource.getData();
      if (data == null || data.length == 0) {
        return;
      }
      String content = new String(data, StandardCharsets.UTF_8);
      String basePath = getBasePath(resource.getHref());

      Matcher urlMatcher = CSS_URL_PATTERN.matcher(content);
      while (urlMatcher.find()) {
        referenced.add(resolveHref(basePath, urlMatcher.group(1).trim()));
      }

      Matcher importMatcher = CSS_IMPORT_PATTERN.matcher(content);
      while (importMatcher.find()) {
        referenced.add(resolveHref(basePath, importMatcher.group(1).trim()));
      }
    } catch (IOException e) {
      log.log(
          System.Logger.Level.DEBUG,
          "Could not read CSS resource for pruning: " + resource.getHref());
    }
  }

  private static boolean isReferenced(String href, Set<String> referencedHrefs) {
    if (referencedHrefs.contains(href)) {
      return true;
    }
    // Also check without directory prefix
    int lastSlash = href.lastIndexOf('/');
    if (lastSlash >= 0) {
      String filename = href.substring(lastSlash + 1);
      for (String ref : referencedHrefs) {
        if (ref.endsWith(filename)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isPrunableMediaType(MediaType mt) {
    return mt == MediaTypes.JPG
        || mt == MediaTypes.PNG
        || mt == MediaTypes.GIF
        || mt == MediaTypes.SVG
        || mt == MediaTypes.TTF
        || mt == MediaTypes.OPENTYPE
        || mt == MediaTypes.WOFF
        || mt == MediaTypes.CSS;
  }

  private static String getBasePath(String href) {
    int slash = href.lastIndexOf('/');
    return slash < 0 ? "" : href.substring(0, slash + 1);
  }

  private static String resolveHref(String basePath, String target) {
    if (target.startsWith("/") || target.startsWith("http:") || target.startsWith("https:")) {
      return target;
    }
    return basePath + target;
  }
}
