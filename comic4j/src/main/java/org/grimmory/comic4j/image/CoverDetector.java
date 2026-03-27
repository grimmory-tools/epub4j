package org.grimmory.comic4j.image;

import java.util.List;
import org.grimmory.comic4j.domain.ComicInfo;
import org.grimmory.comic4j.domain.ComicPage;
import org.grimmory.comic4j.domain.PageType;

/**
 * Detects the cover image in a comic archive. Uses ComicInfo page metadata when available,
 * otherwise falls back to the first image in natural sort order.
 */
public final class CoverDetector {

  private CoverDetector() {}

  /**
   * Determines the cover image index from ComicInfo page metadata.
   *
   * @param comicInfo the comic info (may be null)
   * @return the 0-based image index of the cover, or 0 if not determinable
   */
  public static int detectCoverIndex(ComicInfo comicInfo) {
    if (comicInfo == null || comicInfo.getPages().isEmpty()) {
      return 0;
    }

    for (ComicPage page : comicInfo.getPages()) {
      if (page.pageType() == PageType.FRONT_COVER) {
        return page.imageIndex();
      }
    }

    return 0;
  }

  /**
   * Returns the entry name of the cover image.
   *
   * @param images the naturally sorted list of image entries
   * @param comicInfo the comic info (may be null)
   * @return the entry name of the cover image, or null if no images exist
   */
  public static String detectCoverEntryName(List<ImageEntry> images, ComicInfo comicInfo) {
    if (images == null || images.isEmpty()) {
      return null;
    }

    int coverIndex = detectCoverIndex(comicInfo);
    if (coverIndex >= 0 && coverIndex < images.size()) {
      return images.get(coverIndex).name();
    }

    // Fallback to first image
    return images.getFirst().name();
  }
}
