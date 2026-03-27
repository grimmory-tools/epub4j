package org.grimmory.comic4j.error;

public enum ComicError {

  // Archive errors (ERR_C001-C009)
  ERR_C001("Archive file not found"),
  ERR_C002("Archive format not supported"),
  ERR_C003("Archive is corrupted or unreadable"),
  ERR_C004("Archive is encrypted"),
  ERR_C005("Multi-volume RAR not supported"),
  ERR_C006("No image files found in archive"),
  ERR_C007("Failed to extract entry from archive"),
  ERR_C008("Archive write failed"),
  ERR_C009("Archive format does not support in-place writing"),

  // ComicInfo errors (ERR_C010-C019)
  ERR_C010("ComicInfo.xml not found in archive"),
  ERR_C011("ComicInfo.xml is malformed XML"),
  ERR_C012("ComicInfo.xml contains potential XXE attack"),
  ERR_C013("ComicInfo.xml serialization failed"),

  // Image errors (ERR_C020-C029)
  ERR_C020("Page index out of range"),
  ERR_C021("Image entry not found in archive"),
  ERR_C022("Image file exceeds size limit"),

  // Image processing errors (ERR_C023-C029)
  ERR_C023("Image processing failed"),
  ERR_C024("Invalid processing parameters"),

  // Encoding errors (ERR_C030-C039)
  ERR_C030("Filename encoding detection failed"),

  // Collection errors (ERR_C040-C049)
  ERR_C040("CBC collection file is invalid"),
  ERR_C041("CBC manifest entry not found");

  private final String message;

  ComicError(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }

  public ComicException exception() {
    return new ComicException(this, message);
  }

  public ComicException exception(String detail) {
    return new ComicException(this, message + ": " + detail);
  }

  public ComicException exception(Throwable cause) {
    return new ComicException(this, message, cause);
  }

  public ComicException exception(String detail, Throwable cause) {
    return new ComicException(this, message + ": " + detail, cause);
  }
}
