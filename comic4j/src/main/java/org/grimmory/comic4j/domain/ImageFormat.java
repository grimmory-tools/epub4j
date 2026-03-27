package org.grimmory.comic4j.domain;

import java.util.Locale;
import java.util.Set;

public enum ImageFormat {
  JPEG(Set.of("jpg", "jpeg"), "image/jpeg"),
  PNG(Set.of("png"), "image/png"),
  WEBP(Set.of("webp"), "image/webp"),
  AVIF(Set.of("avif"), "image/avif"),
  HEIC(Set.of("heic", "heif"), "image/heic"),
  GIF(Set.of("gif"), "image/gif"),
  BMP(Set.of("bmp"), "image/bmp");

  private final Set<String> extensions;
  private final String mimeType;

  ImageFormat(Set<String> extensions, String mimeType) {
    this.extensions = Set.copyOf(extensions);
    this.mimeType = mimeType;
  }

  public Set<String> extensions() {
    return Set.copyOf(extensions);
  }

  public String mimeType() {
    return mimeType;
  }

  public static ImageFormat fromFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return null;
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return null;
    }
    return switch (fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
      case "jpg", "jpeg" -> JPEG;
      case "png" -> PNG;
      case "webp" -> WEBP;
      case "avif" -> AVIF;
      case "heic", "heif" -> HEIC;
      case "gif" -> GIF;
      case "bmp" -> BMP;
      default -> null;
    };
  }

  public static boolean isImageFileName(String fileName) {
    return fromFileName(fileName) != null;
  }
}
