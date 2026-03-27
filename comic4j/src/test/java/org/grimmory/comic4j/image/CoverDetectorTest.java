package org.grimmory.comic4j.image;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.grimmory.comic4j.domain.ComicInfo;
import org.grimmory.comic4j.domain.ComicPage;
import org.grimmory.comic4j.domain.ImageFormat;
import org.grimmory.comic4j.domain.PageType;
import org.junit.jupiter.api.Test;

class CoverDetectorTest {

  private static final List<ImageEntry> SAMPLE_IMAGES =
      List.of(
          new ImageEntry("page01.jpg", "page01", 1000, 0, ImageFormat.JPEG),
          new ImageEntry("page02.jpg", "page02", 1000, 1, ImageFormat.JPEG),
          new ImageEntry("page03.jpg", "page03", 1000, 2, ImageFormat.JPEG));

  @Test
  void detectCoverFromFrontCoverPageType() {
    ComicInfo info = new ComicInfo();
    info.setPages(
        List.of(
            ComicPage.builder().imageIndex(0).pageType(PageType.STORY).build(),
            ComicPage.builder().imageIndex(1).pageType(PageType.FRONT_COVER).build(),
            ComicPage.builder().imageIndex(2).pageType(PageType.STORY).build()));

    assertEquals(1, CoverDetector.detectCoverIndex(info));
    assertEquals("page02.jpg", CoverDetector.detectCoverEntryName(SAMPLE_IMAGES, info));
  }

  @Test
  void detectCoverFallbackToFirstImage() {
    ComicInfo info = new ComicInfo();
    // No pages metadata
    assertEquals(0, CoverDetector.detectCoverIndex(info));
    assertEquals("page01.jpg", CoverDetector.detectCoverEntryName(SAMPLE_IMAGES, info));
  }

  @Test
  void detectCoverNullComicInfo() {
    assertEquals(0, CoverDetector.detectCoverIndex(null));
    assertEquals("page01.jpg", CoverDetector.detectCoverEntryName(SAMPLE_IMAGES, null));
  }

  @Test
  void detectCoverEmptyImages() {
    assertNull(CoverDetector.detectCoverEntryName(List.of(), null));
    assertNull(CoverDetector.detectCoverEntryName(null, null));
  }

  @Test
  void detectCoverPagesWithoutFrontCover() {
    ComicInfo info = new ComicInfo();
    info.setPages(
        List.of(
            ComicPage.builder().imageIndex(0).pageType(PageType.STORY).build(),
            ComicPage.builder().imageIndex(1).pageType(PageType.STORY).build()));

    // Should fallback to index 0
    assertEquals(0, CoverDetector.detectCoverIndex(info));
    assertEquals("page01.jpg", CoverDetector.detectCoverEntryName(SAMPLE_IMAGES, info));
  }
}
