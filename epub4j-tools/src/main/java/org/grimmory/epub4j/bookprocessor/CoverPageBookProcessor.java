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
package org.grimmory.epub4j.bookprocessor;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Metadata;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.grimmory.epub4j.epub.BookProcessor;
import org.grimmory.epub4j.util.CollectionUtil;
import org.grimmory.epub4j.util.ResourceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * If the book contains a cover image then this will add a cover page to the book. If the book
 * contains a cover html page it will set that page's first image as the book's cover image.
 *
 * <p>Note: will overwrite any "cover.jpg" or "cover.html" that are already there.
 *
 * @author paul
 */
public class CoverPageBookProcessor implements BookProcessor {

  public static final int MAX_COVER_IMAGE_SIZE = 999;
  private static final System.Logger log = System.getLogger(CoverPageBookProcessor.class.getName());
  public static final String DEFAULT_COVER_PAGE_ID = "cover";
  public static final String DEFAULT_COVER_PAGE_HREF = "cover.html";
  public static final String DEFAULT_COVER_IMAGE_ID = "cover-image";
  public static final String DEFAULT_COVER_IMAGE_HREF = "images/cover.png";

  @Override
  public Book processBook(Book book) {
    Metadata metadata = book.getMetadata();
    if (book.getCoverPage() == null && book.getCoverImage() == null) {
      return book;
    }
    Resource coverPage = book.getCoverPage();
    if (coverPage == null) {
      coverPage = findCoverPage(book);
      book.setCoverPage(coverPage);
    }
    Resource coverImage = book.getCoverImage();
    if (coverPage == null) {
      if (coverImage == null) {
        return book;
      } else { // coverImage != null
        if (StringUtils.isBlank(coverImage.getHref())) {
          coverImage.setHref(DEFAULT_COVER_IMAGE_HREF);
        }
        String coverPageHtml =
            createCoverpageHtml(CollectionUtil.first(metadata.getTitles()), coverImage.getHref());
        coverPage =
            new Resource(
                null,
                coverPageHtml.getBytes(StandardCharsets.UTF_8),
                DEFAULT_COVER_PAGE_HREF,
                MediaTypes.XHTML);
        fixCoverResourceId(book, coverPage, DEFAULT_COVER_PAGE_ID);
      }
    } else { // coverPage != null
      if (book.getCoverImage() == null) {
        coverImage = getFirstImageSource(coverPage, book.getResources());
        book.setCoverImage(coverImage);
        if (coverImage != null) {
          book.getResources().remove(coverImage.getHref());
        }
      }
    }

    book.setCoverImage(coverImage);
    book.setCoverPage(coverPage);
    setCoverResourceIds(book);
    return book;
  }

  //	private String getCoverImageHref(Resource coverImageResource) {
  //		return "cover" + coverImageResource.getMediaType().getDefaultExtension();
  //	}

  private static Resource findCoverPage(Book book) {
    if (book.getCoverPage() != null) {
      return book.getCoverPage();
    }
    if (!(book.getSpine().isEmpty())) {
      return book.getSpine().getResource(0);
    }
    return null;
  }

  private static void setCoverResourceIds(Book book) {
    if (book.getCoverImage() != null) {
      fixCoverResourceId(book, book.getCoverImage(), DEFAULT_COVER_IMAGE_ID);
    }
    if (book.getCoverPage() != null) {
      fixCoverResourceId(book, book.getCoverPage(), DEFAULT_COVER_PAGE_ID);
    }
  }

  private static void fixCoverResourceId(Book book, Resource resource, String defaultId) {
    if (StringUtils.isBlank(resource.getId())) {
      resource.setId(defaultId);
    }
    book.getResources().fixResourceId(resource);
  }

  private static String getCoverPageHref() {
    return DEFAULT_COVER_PAGE_HREF;
  }

  private static String getCoverImageHref() {
    return DEFAULT_COVER_IMAGE_HREF;
  }

  private static Resource getFirstImageSource(Resource titlePageResource, Resources resources) {
    try {
      Document titlePageDocument = ResourceUtil.getAsDocument(titlePageResource);
      NodeList imageElements = titlePageDocument.getElementsByTagName("img");
      for (int i = 0; i < imageElements.getLength(); i++) {
        String relativeImageHref = ((Element) imageElements.item(i)).getAttribute("src");
        String absoluteImageHref =
            calculateAbsoluteImageHref(relativeImageHref, titlePageResource.getHref());
        Resource imageResource = resources.getByHref(absoluteImageHref);
        if (imageResource != null) {
          return imageResource;
        }
      }
    } catch (Exception e) {
      log.log(System.Logger.Level.ERROR, e.getMessage(), e);
    }
    return null;
  }

  // package
  static String calculateAbsoluteImageHref(String relativeImageHref, String baseHref) {
    if (relativeImageHref.startsWith("/")) {
      return relativeImageHref;
    }
    return FilenameUtils.normalize(
        baseHref.substring(0, baseHref.lastIndexOf('/') + 1) + relativeImageHref, true);
  }

  private static String createCoverpageHtml(String title, String imageHref) {
    var escapedImageHref = StringEscapeUtils.escapeHtml4(StringUtils.defaultString(imageHref));
    var escapedTitle = StringEscapeUtils.escapeHtml4(StringUtils.defaultString(title));
    return """
      <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
      <html xmlns="http://www.w3.org/1999/xhtml">
      	<head>
      		<title>Cover</title>
      		<style type="text/css"> img { max-width: 100%; } </style>
      	</head>
      	<body>
      		<div id="cover-image">
      			<img src="%s" alt="%s"/>
      		</div>
      	</body>
      </html>
      """
        .formatted(escapedImageHref, escapedTitle);
  }

  private static Dimension calculateResizeSize(BufferedImage image) {
    Dimension result;
    if (image.getWidth() > image.getHeight()) {
      result =
          new Dimension(
              MAX_COVER_IMAGE_SIZE,
              (int)
                  (((double) MAX_COVER_IMAGE_SIZE / (double) image.getWidth())
                      * (double) image.getHeight()));
    } else {
      result =
          new Dimension(
              (int)
                  (((double) MAX_COVER_IMAGE_SIZE / (double) image.getHeight())
                      * (double) image.getWidth()),
              MAX_COVER_IMAGE_SIZE);
    }
    return result;
  }

  private static BufferedImage createResizedCopy(
      Image originalImage, int scaledWidth, int scaledHeight, boolean preserveAlpha) {
    int imageType = preserveAlpha ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    BufferedImage scaledBI = new BufferedImage(scaledWidth, scaledHeight, imageType);
    Graphics2D g = scaledBI.createGraphics();
    if (preserveAlpha) {
      g.setComposite(AlphaComposite.Src);
    }
    g.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
    g.dispose();
    return scaledBI;
  }
}
