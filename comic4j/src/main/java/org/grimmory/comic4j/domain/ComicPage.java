package org.grimmory.comic4j.domain;

public record ComicPage(
    int imageIndex,
    PageType pageType,
    String bookmark,
    long imageFileSize,
    int imageWidth,
    int imageHeight,
    String key) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private int imageIndex;
    private PageType pageType;
    private String bookmark;
    private long imageFileSize;
    private int imageWidth;
    private int imageHeight;
    private String key;

    public Builder imageIndex(int imageIndex) {
      this.imageIndex = imageIndex;
      return this;
    }

    public Builder pageType(PageType pageType) {
      this.pageType = pageType;
      return this;
    }

    public Builder bookmark(String bookmark) {
      this.bookmark = bookmark;
      return this;
    }

    public Builder imageFileSize(long imageFileSize) {
      this.imageFileSize = imageFileSize;
      return this;
    }

    public Builder imageWidth(int imageWidth) {
      this.imageWidth = imageWidth;
      return this;
    }

    public Builder imageHeight(int imageHeight) {
      this.imageHeight = imageHeight;
      return this;
    }

    public Builder key(String key) {
      this.key = key;
      return this;
    }

    public ComicPage build() {
      return new ComicPage(
          imageIndex, pageType, bookmark, imageFileSize, imageWidth, imageHeight, key);
    }
  }
}
