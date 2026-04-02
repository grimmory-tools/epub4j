package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;

/**
 * Multi-strategy cover image detection for EPUB files. Tries multiple approaches in order of
 * reliability: 1. Already-set cover image (from OPF metadata or properties) 2. Image resource with
 * "cover" in filename 3. First image referenced in first spine item's HTML 4. Largest image
 * resource (likely a full-page cover)
 */
public class CoverDetector {

  private static final System.Logger log = System.getLogger(CoverDetector.class.getName());

  /** Minimum image size in bytes to consider as a potential cover (10KB). */
  private static final int MIN_COVER_SIZE = 10 * 1024;

  private static final Pattern IMG_SRC_PATTERN =
      Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern SVG_IMAGE_PATTERN =
      Pattern.compile(
          "<image[^>]+(?:href|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  /** Describes how a cover image was detected. */
  public enum DetectionMethod {
    /** Cover was already set on the Book (from OPF metadata or properties="cover-image"). */
    OPF_METADATA,
    /** Matched by resource id containing "cover". */
    RESOURCE_ID,
    /** Matched by filename containing "cover". */
    FILENAME,
    /** Found as first image referenced in the first spine item's XHTML. */
    FIRST_SPINE_IMAGE,
    /** Selected as the largest image resource in the book. */
    LARGEST_IMAGE,
    /** First image in the manifest (last-resort fallback). */
    FIRST_MANIFEST_IMAGE
  }

  /**
   * Result of cover detection, pairing the resource with how it was found. Useful for logging and
   * for callers that want to know the detection confidence.
   */
  public record CoverDetectionResult(Resource resource, DetectionMethod method) {}

  /**
   * Detects the cover image with metadata about which strategy succeeded.
   *
   * @param book the book to detect a cover image for
   * @return an Optional containing the detection result, or empty if no cover found
   */
  public static Optional<CoverDetectionResult> detectCoverImageWithMethod(Book book) {
    if (book.getCoverImage() != null) {
      return Optional.of(
          new CoverDetectionResult(book.getCoverImage(), DetectionMethod.OPF_METADATA));
    }

    Resource byId = findCoverById(book.getResources());
    if (byId != null) {
      log.log(System.Logger.Level.DEBUG, "Cover detected by resource id: " + byId.getHref());
      return Optional.of(new CoverDetectionResult(byId, DetectionMethod.RESOURCE_ID));
    }

    Resource byName = findCoverByName(book.getResources());
    if (byName != null) {
      log.log(System.Logger.Level.DEBUG, "Cover detected by filename: " + byName.getHref());
      return Optional.of(new CoverDetectionResult(byName, DetectionMethod.FILENAME));
    }

    Resource fromSpine = findCoverFromFirstSpineItem(book);
    if (fromSpine != null) {
      log.log(
          System.Logger.Level.DEBUG,
          "Cover detected from first spine item: " + fromSpine.getHref());
      return Optional.of(new CoverDetectionResult(fromSpine, DetectionMethod.FIRST_SPINE_IMAGE));
    }

    Resource largest = findLargestImage(book.getResources());
    if (largest != null) {
      log.log(System.Logger.Level.DEBUG, "Cover detected as largest image: " + largest.getHref());
      return Optional.of(new CoverDetectionResult(largest, DetectionMethod.LARGEST_IMAGE));
    }

    Resource firstImage = findFirstManifestImage(book.getResources());
    if (firstImage != null) {
      log.log(
          System.Logger.Level.DEBUG,
          "Cover detected as first manifest image: " + firstImage.getHref());
      return Optional.of(
          new CoverDetectionResult(firstImage, DetectionMethod.FIRST_MANIFEST_IMAGE));
    }

    return Optional.empty();
  }

  /**
   * Attempts to detect the cover image using multiple strategies. Returns null if no cover image
   * could be determined.
   *
   * @param book the book to detect a cover image for
   * @return the detected cover image resource, or null
   */
  public static Resource detectCoverImage(Book book) {
    return detectCoverImageWithMethod(book).map(CoverDetectionResult::resource).orElse(null);
  }

  /**
   * Find an image resource whose id contains "cover" (case-insensitive). Matches id patterns like
   * "cover-image", "cover", "coverimg" that OPF generators commonly use.
   */
  private static Resource findCoverById(Resources resources) {
    for (Resource resource : resources.getAll()) {
      if (!isImageResource(resource)) continue;
      String id = resource.getId();
      if (id != null && id.toLowerCase().contains("cover")) {
        return resource;
      }
    }
    return null;
  }

  /**
   * Returns the first image resource found in the manifest. Last-resort fallback when all other
   * strategies fail.
   */
  private static Resource findFirstManifestImage(Resources resources) {
    for (Resource resource : resources.getAll()) {
      if (isImageResource(resource) && resource.getSize() >= MIN_COVER_SIZE) {
        return resource;
      }
    }
    return null;
  }

  /**
   * Find an image resource whose filename contains "cover" (case-insensitive). Prioritizes exact
   * matches like "cover.jpg" over partial matches like "discover.png".
   */
  private static Resource findCoverByName(Resources resources) {
    Resource exactMatch = null;
    Resource partialMatch = null;

    for (Resource resource : resources.getAll()) {
      if (!isImageResource(resource)) continue;

      String href = resource.getHref();
      if (href == null) continue;

      String filename = href;
      int lastSlash = filename.lastIndexOf('/');
      if (lastSlash >= 0) filename = filename.substring(lastSlash + 1);
      String filenameLower = filename.toLowerCase();

      int dotPos = filenameLower.lastIndexOf('.');
      String nameWithoutExt = dotPos > 0 ? filenameLower.substring(0, dotPos) : filenameLower;
      if ("cover".equals(nameWithoutExt)) {
        exactMatch = resource;
        break; // Can't do better than an exact match
      }

      if (partialMatch == null && filenameLower.contains("cover")) {
        partialMatch = resource;
      }
    }

    return exactMatch != null ? exactMatch : partialMatch;
  }

  /**
   * Look at the first spine item's XHTML content and find the first referenced image. This works
   * for EPUBs where the first page is a cover page containing an img tag.
   */
  private static Resource findCoverFromFirstSpineItem(Book book) {
    Spine spine = book.getSpine();
    if (spine.isEmpty()) return null;

    Resource firstResource = spine.getResource(0);
    if (firstResource == null || firstResource.getMediaType() != MediaTypes.XHTML) {
      return null;
    }

    try {
      byte[] data = firstResource.getData();
      if (data == null || data.length == 0) return null;

      String content = new String(data, StandardCharsets.UTF_8);
      String firstHref = firstResource.getHref();
      String basePath = "";
      if (firstHref != null) {
        int lastSlash = firstHref.lastIndexOf('/');
        if (lastSlash >= 0) {
          basePath = firstHref.substring(0, lastSlash + 1);
        }
      }

      Matcher imgMatcher = IMG_SRC_PATTERN.matcher(content);
      if (imgMatcher.find()) {
        Resource resolved = resolveImageRef(book.getResources(), basePath, imgMatcher.group(1));
        if (resolved != null) return resolved;
      }

      Matcher svgMatcher = SVG_IMAGE_PATTERN.matcher(content);
      if (svgMatcher.find()) {
        Resource resolved = resolveImageRef(book.getResources(), basePath, svgMatcher.group(1));
        if (resolved != null) return resolved;
      }
    } catch (IOException e) {
      log.log(
          System.Logger.Level.DEBUG,
          "Failed to read first spine item for cover detection: " + e.getMessage());
    }

    return null;
  }

  /** Resolve an image reference relative to a base path and look it up in resources. */
  private static Resource resolveImageRef(Resources resources, String basePath, String imgSrc) {
    if (imgSrc == null || imgSrc.isBlank()) return null;

    int hashPos = imgSrc.indexOf('#');
    if (hashPos >= 0) imgSrc = imgSrc.substring(0, hashPos);

    int queryPos = imgSrc.indexOf('?');
    if (queryPos >= 0) imgSrc = imgSrc.substring(0, queryPos);

    Resource resource = resources.getByHref(imgSrc);
    if (resource != null) return resource;

    String resolved = basePath + imgSrc;
    resource = resources.getByHref(resolved);
    if (resource != null) return resource;

    if (resolved.contains("..") || resolved.contains("./")) {
      resolved = collapsePath(resolved);
      resource = resources.getByHref(resolved);
      return resource;
    }

    return null;
  }

  /**
   * Find the largest image resource by data size. Only considers images above the minimum size
   * threshold.
   */
  private static Resource findLargestImage(Resources resources) {
    Resource largest = null;
    long largestSize = MIN_COVER_SIZE; // reject images below this as unlikely covers

    for (Resource resource : resources.getAll()) {
      if (!isImageResource(resource)) continue;

      long size = resource.getSize();
      if (size > largestSize) {
        largestSize = size;
        largest = resource;
      }
    }

    return largest;
  }

  private static boolean isImageResource(Resource resource) {
    MediaType mt = resource.getMediaType();
    return mt == MediaTypes.JPG
        || mt == MediaTypes.PNG
        || mt == MediaTypes.GIF
        || mt == MediaTypes.SVG;
  }

  /** Collapse ".." and "." segments in a path. */
  private static String collapsePath(String path) {
    String[] parts = path.split("/");
    Deque<String> stack = new ArrayDeque<>();
    for (String part : parts) {
      if ("..".equals(part)) {
        if (!stack.isEmpty()) stack.removeLast();
      } else if (!".".equals(part) && !part.isEmpty()) {
        stack.addLast(part);
      }
    }
    return String.join("/", stack);
  }
}
