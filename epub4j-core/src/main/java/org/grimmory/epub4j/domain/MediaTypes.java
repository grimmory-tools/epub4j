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
  public static final MediaType TTF = new MediaType("application/x-truetype-font", ".ttf");
  public static final MediaType OPENTYPE = new MediaType("application/vnd.ms-opentype", ".otf");
  public static final MediaType WOFF = new MediaType("application/font-woff", ".woff");

  // audio
  public static final MediaType MP3 = new MediaType("audio/mpeg", ".mp3");
  public static final MediaType OGG = new MediaType("audio/ogg", ".ogg");

  // video
  public static final MediaType MP4 = new MediaType("video/mp4", ".mp4");

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
    SMIL,
    PLS,
    JAVASCRIPT,
    MP3,
    MP4,
    OGG
  };

  public static final Map<String, MediaType> mediaTypesByName = new HashMap<>();

  static {
    for (MediaType mediaType : mediaTypes) {
      mediaTypesByName.put(mediaType.name(), mediaType);
    }
  }

  public static boolean isBitmapImage(MediaType mediaType) {
    return mediaType == JPG || mediaType == PNG || mediaType == GIF;
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
