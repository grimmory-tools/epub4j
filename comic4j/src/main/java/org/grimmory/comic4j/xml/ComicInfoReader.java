/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;
import org.grimmory.comic4j.domain.*;
import org.grimmory.comic4j.error.ComicError;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * Parses ComicInfo.xml into a {@link ComicInfo} domain object. Uses XXE-hardened DOM parsing. All
 * fields are parsed gracefully; missing or invalid values result in null rather than exceptions.
 */
public final class ComicInfoReader {

  public static final String COMIC_INFO_FILENAME = "ComicInfo.xml";

  private ComicInfoReader() {}

  /**
   * Parses ComicInfo.xml from the given input stream.
   *
   * @param inputStream the XML content
   * @return parsed ComicInfo, never null
   * @throws org.grimmory.comic4j.error.ComicException if the XML is malformed or contains XXE
   */
  public static ComicInfo read(InputStream inputStream) {
    Document doc;
    try {
      doc = SecureXmlParser.newDocumentBuilder().parse(inputStream);
    } catch (SAXException e) {
      String msg = e.getMessage();
      if (msg != null && msg.contains("DOCTYPE")) {
        throw ComicError.ERR_C012.exception(e);
      }
      throw ComicError.ERR_C011.exception(e);
    } catch (ParserConfigurationException | IOException e) {
      throw ComicError.ERR_C011.exception(e);
    }

    return mapDocument(doc);
  }

  /**
   * Parses ComicInfo.xml from a byte array. Cleans common HTML entities before parsing since
   * ComicInfo.xml files from various sources often contain non-XML entities like &amp;nbsp;.
   */
  public static ComicInfo read(byte[] xmlBytes) {
    String xml = new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8);
    xml = replaceHtmlEntities(xml);
    return read(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  /**
   * Replaces common HTML entities that are invalid in XML with their Unicode equivalents.
   * Real-world ComicInfo.xml files often contain these.
   */
  private static String replaceHtmlEntities(String xml) {
    return xml.replace("&nbsp;", "&#160;")
        .replace("&copy;", "&#169;")
        .replace("&mdash;", "&#8212;")
        .replace("&ndash;", "&#8211;")
        .replace("&lsquo;", "&#8216;")
        .replace("&rsquo;", "&#8217;")
        .replace("&ldquo;", "&#8220;")
        .replace("&rdquo;", "&#8221;")
        .replace("&hellip;", "&#8230;");
  }

  private static ComicInfo mapDocument(Document doc) {
    Element root = doc.getDocumentElement();
    if (root == null) {
      throw ComicError.ERR_C011.exception("Empty document");
    }

    ComicInfo info = new ComicInfo();

    // Core identification
    info.setTitle(getText(root, "Title"));
    info.setSeries(getText(root, "Series"));
    info.setNumber(getText(root, "Number"));
    info.setCount(getInteger(root, "Count"));
    info.setVolume(getInteger(root, "Volume"));

    // Alternate series
    info.setAlternateSeries(getText(root, "AlternateSeries"));
    info.setAlternateNumber(getText(root, "AlternateNumber"));
    info.setAlternateCount(getInteger(root, "AlternateCount"));

    // Description
    info.setSummary(getText(root, "Summary"));
    info.setNotes(getText(root, "Notes"));
    info.setReview(getText(root, "Review"));

    // Publication date
    info.setYear(getInteger(root, "Year"));
    info.setMonth(getInteger(root, "Month"));
    info.setDay(getInteger(root, "Day"));

    // Creators
    info.setWriter(getText(root, "Writer"));
    info.setPenciller(getText(root, "Penciller"));
    info.setInker(getText(root, "Inker"));
    info.setColorist(getText(root, "Colorist"));
    info.setLetterer(getText(root, "Letterer"));
    info.setCoverArtist(getText(root, "CoverArtist"));
    info.setEditor(getText(root, "Editor"));
    info.setTranslator(getText(root, "Translator"));

    // Publisher
    info.setPublisher(getText(root, "Publisher"));
    info.setImprint(getText(root, "Imprint"));

    // Classification
    info.setGenre(getText(root, "Genre"));
    info.setTags(getText(root, "Tags"));
    info.setWeb(getText(root, "Web"));
    info.setPageCount(getInteger(root, "PageCount"));
    info.setLanguageISO(getText(root, "LanguageISO"));
    info.setFormat(getText(root, "Format"));

    String bw = getText(root, "BlackAndWhite");
    if (bw != null) info.setBlackAndWhite(YesNo.fromXmlValue(bw));

    String manga = getText(root, "Manga");
    if (manga != null) info.setManga(ReadingDirection.fromXmlValue(manga));

    String ageRating = getText(root, "AgeRating");
    if (ageRating != null) info.setAgeRating(AgeRating.fromXmlValue(ageRating));

    info.setCommunityRating(getFloat(root, "CommunityRating"));

    // Content details
    info.setCharacters(getText(root, "Characters"));
    info.setTeams(getText(root, "Teams"));
    info.setLocations(getText(root, "Locations"));
    info.setMainCharacterOrTeam(getText(root, "MainCharacterOrTeam"));
    info.setScanInformation(getText(root, "ScanInformation"));

    // Story arcs
    info.setStoryArc(getText(root, "StoryArc"));
    info.setStoryArcNumber(getText(root, "StoryArcNumber"));
    info.setSeriesGroup(getText(root, "SeriesGroup"));

    // Identifiers
    info.setGtin(getText(root, "GTIN"));

    // Pages
    info.setPages(parsePages(root));

    // Extensions
    info.setLocalizedSeries(getText(root, "LocalizedSeries"));
    info.setSeriesSort(getText(root, "SeriesSort"));
    info.setTitleSort(getText(root, "TitleSort"));

    return info;
  }

  private static List<ComicPage> parsePages(Element root) {
    NodeList pagesNodes = root.getElementsByTagName("Pages");
    if (pagesNodes.getLength() == 0) {
      return List.of();
    }

    Element pagesElement = (Element) pagesNodes.item(0);
    NodeList pageNodes = pagesElement.getElementsByTagName("Page");
    if (pageNodes.getLength() == 0) {
      return List.of();
    }

    List<ComicPage> pages = new ArrayList<>(pageNodes.getLength());
    for (int i = 0; i < pageNodes.getLength(); i++) {
      Element pageEl = (Element) pageNodes.item(i);
      ComicPage page =
          ComicPage.builder()
              .imageIndex(getAttrInt(pageEl, "Image", i))
              .pageType(PageType.fromXmlValue(pageEl.getAttribute("Type")))
              .bookmark(getAttrString(pageEl, "Bookmark"))
              .imageFileSize(getAttrLong(pageEl, "ImageSize", 0))
              .imageWidth(getAttrInt(pageEl, "ImageWidth", 0))
              .imageHeight(getAttrInt(pageEl, "ImageHeight", 0))
              .key(getAttrString(pageEl, "Key"))
              .build();
      pages.add(page);
    }
    return pages;
  }

  // --- DOM helper methods ---

  private static String getText(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() == 0) {
      // Try case-insensitive search as fallback
      return getTextCaseInsensitive(parent, tagName);
    }
    String text = nodes.item(0).getTextContent();
    return (text != null && !text.isBlank()) ? text.strip() : null;
  }

  private static String getTextCaseInsensitive(Element parent, String tagName) {
    NodeList children = parent.getChildNodes();
    String lowerTag = tagName.toLowerCase(Locale.ROOT);
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE
          && child.getNodeName().toLowerCase(Locale.ROOT).equals(lowerTag)) {
        String text = child.getTextContent();
        return (text != null && !text.isBlank()) ? text.strip() : null;
      }
    }
    return null;
  }

  private static Integer getInteger(Element parent, String tagName) {
    String text = getText(parent, tagName);
    if (text == null) return null;
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Float getFloat(Element parent, String tagName) {
    String text = getText(parent, tagName);
    if (text == null) return null;
    try {
      // Handle comma as decimal separator (common in non-English locales)
      return Float.parseFloat(text.replace(',', '.'));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String getAttrString(Element element, String attrName) {
    String val = element.getAttribute(attrName);
    return (val != null && !val.isBlank()) ? val.strip() : null;
  }

  private static int getAttrInt(Element element, String attrName, int defaultValue) {
    String val = element.getAttribute(attrName);
    if (val == null || val.isBlank()) return defaultValue;
    try {
      return Integer.parseInt(val.strip());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static long getAttrLong(Element element, String attrName, long defaultValue) {
    String val = element.getAttribute(attrName);
    if (val == null || val.isBlank()) return defaultValue;
    try {
      return Long.parseLong(val.strip());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
