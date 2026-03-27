package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.grimmory.epub4j.domain.*;

/**
 * Finds and removes duplicate resources in an EPUB by content hash. When duplicates are found, all
 * references are updated to point to a single canonical copy and rewrites all references to the
 * surviving resource.
 */
public class ResourceDeduplicator {

  private static final System.Logger log = System.getLogger(ResourceDeduplicator.class.getName());
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  /**
   * Result of a deduplication operation.
   *
   * @param duplicateGroupCount number of groups of duplicates found
   * @param resourcesRemoved number of duplicate resources removed
   * @param bytesSaved estimated bytes saved by deduplication
   */
  public record DeduplicationResult(
      int duplicateGroupCount, int resourcesRemoved, long bytesSaved) {}

  /**
   * Find and remove duplicate resources in the book. For each group of duplicates, keeps the one
   * that appears first in the manifest (by href sort order) and removes the rest, updating all
   * spine and TOC references.
   *
   * @param book the book to deduplicate
   * @return deduplication result with statistics
   */
  public static DeduplicationResult deduplicate(Book book) {
    // Step 1: Group resources by content hash
    Map<String, List<Resource>> hashGroups = groupByHash(book.getResources());

    int groupCount = 0;
    int removed = 0;
    long bytesSaved = 0;

    // Step 2: For each group with > 1 resource, merge references
    for (Map.Entry<String, List<Resource>> entry : hashGroups.entrySet()) {
      List<Resource> group = entry.getValue();
      if (group.size() < 2) continue;

      groupCount++;
      // Sort by href to pick canonical deterministically
      group.sort(Comparator.comparing(Resource::getHref));
      Resource canonical = group.getFirst();

      for (int i = 1; i < group.size(); i++) {
        Resource duplicate = group.get(i);
        bytesSaved += duplicate.getSize();

        // Update spine references
        replaceInSpine(book.getSpine(), duplicate, canonical);

        // Update TOC references
        replaceInToc(book.getTableOfContents(), duplicate, canonical);

        // Update guide references
        replaceInGuide(book.getGuide(), duplicate, canonical);

        // Remove the duplicate
        book.getResources().remove(duplicate.getHref());
        removed++;

        log.log(
            System.Logger.Level.DEBUG,
            "Removed duplicate: "
                + duplicate.getHref()
                + " (canonical: "
                + canonical.getHref()
                + ")");
      }
    }

    if (groupCount > 0) {
      log.log(
          System.Logger.Level.DEBUG,
          "Deduplication: "
              + groupCount
              + " groups, "
              + removed
              + " removed, "
              + bytesSaved
              + " bytes saved");
    }

    return new DeduplicationResult(groupCount, removed, bytesSaved);
  }

  /**
   * Find duplicate groups without removing them (dry run).
   *
   * @return map of content hash → list of resource hrefs that share that hash
   */
  public static Map<String, List<String>> findDuplicates(Book book) {
    Map<String, List<Resource>> hashGroups = groupByHash(book.getResources());
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (Map.Entry<String, List<Resource>> entry : hashGroups.entrySet()) {
      if (entry.getValue().size() > 1) {
        result.put(entry.getKey(), entry.getValue().stream().map(Resource::getHref).toList());
      }
    }
    return result;
  }

  private static Map<String, List<Resource>> groupByHash(Resources resources) {
    Map<String, List<Resource>> groups = new LinkedHashMap<>();
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }

    for (Resource resource : resources.getAll()) {
      try {
        byte[] data = resource.getData();
        if (data == null || data.length == 0) continue;

        digest.reset();
        String hash = bytesToHex(digest.digest(data));
        groups.computeIfAbsent(hash, k -> new ArrayList<>()).add(resource);
      } catch (IOException e) {
        log.log(System.Logger.Level.DEBUG, "Skipping unreadable resource: " + resource.getHref());
      }
    }
    return groups;
  }

  private static void replaceInSpine(Spine spine, Resource from, Resource to) {
    for (SpineReference ref : spine.getSpineReferences()) {
      if (ref.getResource() == from) {
        ref.setResource(to);
      }
    }
  }

  private static void replaceInToc(TableOfContents toc, Resource from, Resource to) {
    for (TOCReference tocRef : toc.getTocReferences()) {
      replaceInTocRef(tocRef, from, to);
    }
  }

  private static void replaceInTocRef(TOCReference tocRef, Resource from, Resource to) {
    if (tocRef.getResource() == from) {
      tocRef.setResource(to);
    }
    if (tocRef.getChildren() != null) {
      for (TOCReference child : tocRef.getChildren()) {
        replaceInTocRef(child, from, to);
      }
    }
  }

  private static void replaceInGuide(Guide guide, Resource from, Resource to) {
    for (GuideReference ref : guide.getReferences()) {
      if (ref.getResource() == from) {
        ref.setResource(to);
      }
    }
  }

  private static String bytesToHex(byte[] bytes) {
    char[] chars = new char[bytes.length * 2];
    int i = 0;
    for (byte value : bytes) {
      int unsigned = value & 0xff;
      chars[i++] = HEX_DIGITS[unsigned >>> 4];
      chars[i++] = HEX_DIGITS[unsigned & 0x0f];
    }
    return new String(chars);
  }
}
