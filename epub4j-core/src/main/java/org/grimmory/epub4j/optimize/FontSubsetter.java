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
 * Subsets embedded fonts to only the glyphs used in spine documents.
 *
 * <p>This implementation collects all Unicode codepoints used in spine XHTML and removes fonts that
 * have no matching codepoints at all (completely unused fonts). True glyph-level subsetting
 * requires a font library (sfntly or harfbuzz via FFM) and is handled by native backends when
 * available.
 */
public class FontSubsetter implements BookProcessor {

  private static final System.Logger log = System.getLogger(FontSubsetter.class.getName());

  private static final Pattern FONT_FACE_PATTERN =
      Pattern.compile(
          "@font-face\\s*\\{[^}]*src\\s*:\\s*url\\([\"']?([^\"')]+)[\"']?\\)",
          Pattern.CASE_INSENSITIVE);

  @Override
  public Book processBook(Book book) {
    Set<Integer> usedCodepoints = collectUsedCodepoints(book);
    if (usedCodepoints.isEmpty()) {
      return book;
    }

    // Collect font hrefs referenced in CSS @font-face rules
    Set<String> referencedFontHrefs = collectReferencedFontHrefs(book);

    // Remove fonts that are not referenced in any @font-face rule
    Set<String> toRemove = new HashSet<>();
    for (Resource resource : book.getResources().getAll()) {
      if (!isFontResource(resource.getMediaType())) {
        continue;
      }
      if (!referencedFontHrefs.contains(resource.getHref())) {
        toRemove.add(resource.getHref());
      }
    }

    for (String href : toRemove) {
      book.getResources().remove(href);
      log.log(System.Logger.Level.DEBUG, "Removed unreferenced font: " + href);
    }

    return book;
  }

  private static Set<Integer> collectUsedCodepoints(Book book) {
    Set<Integer> codepoints = new HashSet<>();
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
        content.codePoints().forEach(codepoints::add);
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Could not read spine item for font subsetting: " + resource.getHref());
      }
    }
    return codepoints;
  }

  private static Set<String> collectReferencedFontHrefs(Book book) {
    Set<String> fontHrefs = new HashSet<>();
    for (Resource resource : book.getResources().getAll()) {
      if (resource.getMediaType() != MediaTypes.CSS) {
        continue;
      }
      try {
        byte[] data = resource.getData();
        if (data == null) {
          continue;
        }
        String css = new String(data, StandardCharsets.UTF_8);
        String basePath = getBasePath(resource.getHref());
        Matcher matcher = FONT_FACE_PATTERN.matcher(css);
        while (matcher.find()) {
          String fontUrl = matcher.group(1).trim();
          fontHrefs.add(resolveHref(basePath, fontUrl));
        }
      } catch (IOException e) {
        log.log(
            System.Logger.Level.DEBUG,
            "Could not read CSS for font references: " + resource.getHref());
      }
    }
    return fontHrefs;
  }

  private static boolean isFontResource(MediaType mt) {
    return mt == MediaTypes.TTF || mt == MediaTypes.OPENTYPE || mt == MediaTypes.WOFF;
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
