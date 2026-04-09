/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Originally from epub4j (https://github.com/documentnode/epub4j)
 * Copyright (C) Paul Siegmund and epub4j contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grimmory.epub4j.domain;

import java.util.HashMap;
import java.util.Map;
import org.grimmory.epub4j.util.StringUtil;

/**
 * Manages mediatypes that are used by epubs
 *
 * @author paul
 */
public class MediaTypes {

  public static final MediaType XHTML =
      new MediaType("application/xhtml+xml", ".xhtml", new String[] {".htm", ".html", ".xhtml"});
  public static final MediaType EPUB = new MediaType("application/epub+zip", ".epub");
  public static final MediaType NCX = new MediaType("application/x-dtbncx+xml", ".ncx");

  public static final MediaType JAVASCRIPT = new MediaType("text/javascript", ".js");
  public static final MediaType CSS = new MediaType("text/css", ".css");

  // images
  public static final MediaType JPG =
      new MediaType("image/jpeg", ".jpg", new String[] {".jpg", ".jpeg"});
  public static final MediaType PNG = new MediaType("image/png", ".png");
  public static final MediaType GIF = new MediaType("image/gif", ".gif");

  public static final MediaType SVG = new MediaType("image/svg+xml", ".svg");

  // fonts
  public static final MediaType TTF = new MediaType("font/ttf", ".ttf");
  public static final MediaType OPENTYPE = new MediaType("font/otf", ".otf");
  public static final MediaType WOFF = new MediaType("font/woff", ".woff");
  public static final MediaType WOFF2 = new MediaType("font/woff2", ".woff2");

  // audio
  public static final MediaType MP3 = new MediaType("audio/mpeg", ".mp3");
  public static final MediaType OGG = new MediaType("audio/ogg", ".ogg");
  public static final MediaType AAC = new MediaType("audio/aac", ".aac");
  public static final MediaType M4A =
      new MediaType("audio/mp4", ".m4a", new String[] {".m4a", ".m4b"});
  public static final MediaType WAV = new MediaType("audio/wav", ".wav");
  public static final MediaType FLAC = new MediaType("audio/flac", ".flac");
  public static final MediaType WEBM_AUDIO = new MediaType("audio/webm", ".weba");

  // video
  public static final MediaType MP4 = new MediaType("video/mp4", ".mp4");
  public static final MediaType WEBM = new MediaType("video/webm", ".webm");

  // images (additional)
  public static final MediaType WEBP = new MediaType("image/webp", ".webp");
  public static final MediaType AVIF = new MediaType("image/avif", ".avif");

  public static final MediaType SMIL = new MediaType("application/smil+xml", ".smil");
  public static final MediaType XPGT =
      new MediaType("application/adobe-page-template+xml", ".xpgt");
  public static final MediaType PLS = new MediaType("application/pls+xml", ".pls");

  public static final MediaType[] mediaTypes = {
    XHTML,
    EPUB,
    JPG,
    PNG,
    GIF,
    CSS,
    SVG,
    TTF,
    NCX,
    XPGT,
    OPENTYPE,
    WOFF,
    WOFF2,
    SMIL,
    PLS,
    JAVASCRIPT,
    MP3,
    MP4,
    OGG,
    AAC,
    M4A,
    WAV,
    FLAC,
    WEBM_AUDIO,
    WEBM,
    WEBP,
    AVIF
  };

  public static final Map<String, MediaType> mediaTypesByName = new HashMap<>();

  static {
    for (MediaType mediaType : mediaTypes) {
      mediaTypesByName.put(mediaType.name(), mediaType);
    }
    // Legacy/alias MIME types mapping to the EPUB3 canonical types
    mediaTypesByName.put("application/x-truetype-font", TTF);
    mediaTypesByName.put("application/vnd.ms-opentype", OPENTYPE);
    mediaTypesByName.put("application/font-woff", WOFF);
    mediaTypesByName.put("application/font-woff2", WOFF2);
  }

  public static boolean isBitmapImage(MediaType mediaType) {
    return mediaType == JPG
        || mediaType == PNG
        || mediaType == GIF
        || mediaType == WEBP
        || mediaType == AVIF;
  }

  /**
   * Gets the MediaType based on the file extension. Null of no matching extension found.
   *
   * @param filename
   * @return the MediaType based on the file extension.
   */
  public static MediaType determineMediaType(String filename) {
    for (MediaType mediaType : mediaTypesByName.values()) {
      for (String extension : mediaType.extensions()) {
        if (StringUtil.endsWithIgnoreCase(filename, extension)) {
          return mediaType;
        }
      }
    }
    return null;
  }

  public static MediaType getMediaTypeByName(String mediaTypeName) {
    return mediaTypesByName.get(mediaTypeName);
  }
}
