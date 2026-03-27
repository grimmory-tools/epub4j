package org.grimmory.comic4j.image.processing;

import java.util.List;
import org.grimmory.comic4j.domain.ReadingDirection;

/**
 * Splits landscape (wide) comic pages into two portrait pages. Respects reading direction:
 * left-to-right for Western comics, right-to-left for manga (right half first).
 *
 * <p>A page is considered landscape if its width is greater than its height. Double-page spreads in
 * scanned comics typically have this aspect ratio.
 */
public final class LandscapeSplitter {

  private LandscapeSplitter() {}

  /** Returns true if the image is landscape orientation (wider than tall). */
  public static boolean isLandscape(NativePixelBuffer buffer) {
    return buffer.width() > buffer.height();
  }

  /**
   * Splits a landscape page into two portrait pages. Returns a list of 1 or 2 pages: - If the page
   * is not landscape, returns a single-element list with a copy. - If landscape, splits at the
   * center and returns pages in reading order.
   *
   * @param buffer the source image
   * @param direction reading direction (controls page order)
   * @return list of 1 or 2 page buffers (caller must close each)
   */
  public static List<NativePixelBuffer> split(
      NativePixelBuffer buffer, ReadingDirection direction) {
    if (!isLandscape(buffer)) {
      return List.of(buffer.subRegion(0, 0, buffer.width(), buffer.height()));
    }

    int halfWidth = buffer.width() / 2;
    int rightWidth = buffer.width() - halfWidth; // Handle odd widths

    NativePixelBuffer left = buffer.subRegion(0, 0, halfWidth, buffer.height());
    NativePixelBuffer right = buffer.subRegion(halfWidth, 0, rightWidth, buffer.height());

    // Manga reads right-to-left: right half is page 1
    if (direction == ReadingDirection.RIGHT_TO_LEFT
        || direction == ReadingDirection.RIGHT_TO_LEFT_MANGA) {
      return List.of(right, left);
    }

    return List.of(left, right);
  }
}
