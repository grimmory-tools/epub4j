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
package org.grimmory.epub4j.epub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Date;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.Metadata;
import org.grimmory.epub4j.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the package document metadata.
 *
 * <p>In its own separate class because the PackageDocumentReader became a bit large and unwieldy.
 *
 * @author paul
 */
// package
class PackageDocumentMetadataReader extends PackageDocumentBase {

  private static final System.Logger log =
      System.getLogger(PackageDocumentMetadataReader.class.getName());

  public static Metadata readMetadata(Document packageDocument) {
    Metadata result = new Metadata();
    Element metadataElement =
        DOMUtil.getFirstElementByTagNameNS(
            packageDocument.getDocumentElement(), NAMESPACE_OPF, OPFTags.metadata);
    if (metadataElement == null) {
      log.log(System.Logger.Level.ERROR, "Package does not contain element " + OPFTags.metadata);
      return result;
    }
    result.setTitles(
        DOMUtil.getElementsTextChild(metadataElement, NAMESPACE_DUBLIN_CORE, DCTags.title));
    result.setPublishers(
        DOMUtil.getElementsTextChild(metadataElement, NAMESPACE_DUBLIN_CORE, DCTags.publisher));
    result.setDescriptions(
        DOMUtil.getElementsTextChild(metadataElement, NAMESPACE_DUBLIN_CORE, DCTags.description));
    result.setRights(
        DOMUtil.getElementsTextChild(metadataElement, NAMESPACE_DUBLIN_CORE, DCTags.rights));
    result.setTypes(
        DOMUtil.getElementsTextChild(metadataElement, NAMESPACE_DUBLIN_CORE, DCTags.type));
    result.setSubjects(
        DOMUtil.getElementsTextChild(metadataElement, NAMESPACE_DUBLIN_CORE, DCTags.subject));
    result.setIdentifiers(readIdentifiers(metadataElement));
    result.setAuthors(readCreators(metadataElement));
    result.setContributors(readContributors(metadataElement));
    result.setDates(readDates(metadataElement));
    result.setOtherProperties(readOtherProperties(metadataElement));
    result.setMetaAttributes(readMetaProperties(metadataElement));
    Element languageTag =
        DOMUtil.getFirstElementByTagNameNS(metadataElement, NAMESPACE_DUBLIN_CORE, DCTags.language);
    if (languageTag != null) {
      result.setLanguage(DOMUtil.getTextChildrenContent(languageTag));
    }

    readEpub3Refinements(metadataElement, result);
    readSeriesMetadata(metadataElement, result);
    readPageCount(metadataElement, result);
    readRenditionProperties(metadataElement, result);

    return result;
  }

  /**
   * consumes meta tags that have a property attribute as defined in the standard. For example:
   * &lt;meta property="rendition:layout"&gt;pre-paginated&lt;/meta&gt;
   *
   * @param metadataElement
   * @return
   */
  private static Map<QName, String> readOtherProperties(Element metadataElement) {
    NodeList metaTags = metadataElement.getElementsByTagName(OPFTags.meta);
    int metaTagCount = metaTags.getLength();
    Map<QName, String> result = new HashMap<>(Math.max(16, metaTagCount));

    for (int i = 0; i < metaTagCount; i++) {
      Node metaNode = metaTags.item(i);
      Node property = metaNode.getAttributes().getNamedItem(OPFAttributes.property);
      if (property != null) {
        String name = property.getNodeValue();
        String value = metaNode.getTextContent();
        result.put(new QName(name), value);
      }
    }

    return result;
  }

  /**
   * consumes meta tags that have a property attribute as defined in the standard. For example:
   * &lt;meta property="rendition:layout"&gt;pre-paginated&lt;/meta&gt;
   *
   * @param metadataElement
   * @return
   */
  private static Map<String, String> readMetaProperties(Element metadataElement) {
    NodeList metaTags = metadataElement.getElementsByTagName(OPFTags.meta);
    int metaTagCount = metaTags.getLength();
    Map<String, String> result = new HashMap<>(Math.max(16, metaTagCount));

    for (int i = 0; i < metaTagCount; i++) {
      Element metaElement = (Element) metaTags.item(i);
      String name = metaElement.getAttribute(OPFAttributes.name);
      String value = metaElement.getAttribute(OPFAttributes.content);
      result.put(name, value);
    }

    return result;
  }

  private static String getBookIdId(Document document) {
    Element packageElement =
        DOMUtil.getFirstElementByTagNameNS(
            document.getDocumentElement(), NAMESPACE_OPF, OPFTags.packageTag);
    if (packageElement == null) {
      return null;
    }
    return packageElement.getAttributeNS(NAMESPACE_OPF, OPFAttributes.uniqueIdentifier);
  }

  private static List<Author> readCreators(Element metadataElement) {
    return readAuthors(DCTags.creator, metadataElement);
  }

  private static List<Author> readContributors(Element metadataElement) {
    return readAuthors(DCTags.contributor, metadataElement);
  }

  private static List<Author> readAuthors(String authorTag, Element metadataElement) {
    NodeList elements = metadataElement.getElementsByTagNameNS(NAMESPACE_DUBLIN_CORE, authorTag);
    int elementCount = elements.getLength();
    List<Author> result = new ArrayList<>(elementCount);
    for (int i = 0; i < elementCount; i++) {
      Element authorElement = (Element) elements.item(i);
      Author author = createAuthor(authorElement);
      if (author != null) {
        result.add(author);
      }
    }
    return result;
  }

  private static List<Date> readDates(Element metadataElement) {
    NodeList elements = metadataElement.getElementsByTagNameNS(NAMESPACE_DUBLIN_CORE, DCTags.date);
    int elementCount = elements.getLength();
    List<Date> result = new ArrayList<>(elementCount);
    for (int i = 0; i < elementCount; i++) {
      Element dateElement = (Element) elements.item(i);
      Date date;
      try {
        date =
            new Date(
                DOMUtil.getTextChildrenContent(dateElement),
                dateElement.getAttributeNS(NAMESPACE_OPF, OPFAttributes.event));
        result.add(date);
      } catch (IllegalArgumentException e) {
        log.log(System.Logger.Level.ERROR, e.getMessage());
      }
    }
    return result;
  }

  private static Author createAuthor(Element authorElement) {
    String authorString = DOMUtil.getTextChildrenContent(authorElement);
    if (StringUtil.isBlank(authorString)) {
      return null;
    }
    int spacePos = authorString.lastIndexOf(' ');
    Author result;
    if (spacePos < 0) {
      result = new Author(authorString);
    } else {
      result =
          new Author(authorString.substring(0, spacePos), authorString.substring(spacePos + 1));
    }
    result.setRole(authorElement.getAttributeNS(NAMESPACE_OPF, OPFAttributes.role));
    return result;
  }

  private static List<Identifier> readIdentifiers(Element metadataElement) {
    NodeList identifierElements =
        metadataElement.getElementsByTagNameNS(NAMESPACE_DUBLIN_CORE, DCTags.identifier);
    int identifierCount = identifierElements.getLength();
    if (identifierCount == 0) {
      log.log(System.Logger.Level.ERROR, "Package does not contain element " + DCTags.identifier);
      return new ArrayList<>();
    }
    String bookIdId = getBookIdId(metadataElement.getOwnerDocument());
    List<Identifier> result = new ArrayList<>(identifierCount);
    for (int i = 0; i < identifierCount; i++) {
      Element identifierElement = (Element) identifierElements.item(i);
      String schemeName = identifierElement.getAttributeNS(NAMESPACE_OPF, DCAttributes.scheme);
      String identifierValue = DOMUtil.getTextChildrenContent(identifierElement);
      if (StringUtil.isBlank(identifierValue)) {
        continue;
      }
      Identifier identifier = Identifier.fromUrn(schemeName, identifierValue);
      if (identifierElement.getAttribute("id").equals(bookIdId)) {
        identifier.setBookId(true);
      }
      result.add(identifier);
    }
    return result;
  }

  /**
   * Reads EPUB3 refinement meta elements that refine dc:title and dc:creator elements. Handles
   * title-type (for subtitle detection) and role (for author role assignment).
   */
  private static void readEpub3Refinements(Element metadataElement, Metadata result) {
    // Build id -> title text map
    NodeList titleElements =
        metadataElement.getElementsByTagNameNS(NAMESPACE_DUBLIN_CORE, DCTags.title);
    int titleCount = titleElements.getLength();
    Map<String, String> titlesById = new HashMap<>(Math.max(16, titleCount));
    for (int i = 0; i < titleCount; i++) {
      Element el = (Element) titleElements.item(i);
      String id = el.getAttribute("id");
      if (StringUtil.isNotBlank(id)) {
        titlesById.put(id, DOMUtil.getTextChildrenContent(el));
      }
    }

    // Build id -> Author map by matching creator element text to parsed authors
    NodeList creatorElements =
        metadataElement.getElementsByTagNameNS(NAMESPACE_DUBLIN_CORE, DCTags.creator);
    int creatorCount = creatorElements.getLength();
    Map<String, Author> authorsById = new HashMap<>(Math.max(16, creatorCount));
    Map<String, Author> authorsByFullName =
        new HashMap<>(Math.max(16, result.getAuthors().size() * 2));
    for (Author author : result.getAuthors()) {
      String fullName = (author.getFirstname() + " " + author.getLastname()).trim();
      if (StringUtil.isNotBlank(fullName)) {
        authorsByFullName.put(fullName, author);
      }
    }
    for (int i = 0; i < creatorCount; i++) {
      Element el = (Element) creatorElements.item(i);
      String id = el.getAttribute("id");
      if (StringUtil.isNotBlank(id)) {
        String text = DOMUtil.getTextChildrenContent(el).trim();
        Author author = authorsByFullName.get(text);
        if (author != null) {
          authorsById.put(id, author);
        }
      }
    }

    // Scan meta elements for refines
    NodeList metaTags = metadataElement.getElementsByTagName(OPFTags.meta);
    int metaTagCount = metaTags.getLength();
    for (int i = 0; i < metaTagCount; i++) {
      Element meta = (Element) metaTags.item(i);
      String refines = meta.getAttribute(OPFAttributes.refines);
      String property = meta.getAttribute(OPFAttributes.property);
      if (StringUtil.isBlank(refines) || StringUtil.isBlank(property)) {
        continue;
      }

      String refId = refines.startsWith("#") ? refines.substring(1) : refines;
      String value = meta.getTextContent().trim();

      if (MetaProperties.TITLE_TYPE.equals(property)) {
        if ("subtitle".equalsIgnoreCase(value) && titlesById.containsKey(refId)) {
          result.setSubtitle(titlesById.get(refId));
        }
      } else if (MetaProperties.ROLE.equals(property)) {
        Author author = authorsById.get(refId);
        if (author != null) {
          author.setRole(value);
        }
      }
    }
  }

  /**
   * Reads series metadata from EPUB3 belongs-to-collection / group-position and legacy series /
   * series_index meta elements used by many authoring tools.
   */
  private static void readSeriesMetadata(Element metadataElement, Metadata result) {
    NodeList metaTags = metadataElement.getElementsByTagName(OPFTags.meta);
    int metaTagCount = metaTags.getLength();

    for (int i = 0; i < metaTagCount; i++) {
      Element meta = (Element) metaTags.item(i);
      String property = meta.getAttribute(OPFAttributes.property);
      String name = meta.getAttribute(OPFAttributes.name);
      String content = meta.getAttribute(OPFAttributes.content);
      String refines = meta.getAttribute(OPFAttributes.refines);
      String text = meta.getTextContent().trim();

      // EPUB3: belongs-to-collection (top-level, no refines)
      if (result.getSeriesName() == null
          && MetaProperties.BELONGS_TO_COLLECTION.equals(property)
          && StringUtil.isBlank(refines)) {
        result.setSeriesName(text);
      }

      // Legacy fallback: series name from tool-specific meta element
      if (result.getSeriesName() == null && LegacyMeta.SERIES.equals(name)) {
        result.setSeriesName(content);
      }

      // EPUB3: group-position
      if (result.getSeriesNumber() == null && MetaProperties.GROUP_POSITION.equals(property)) {
        try {
          result.setSeriesNumber(Float.parseFloat(text));
        } catch (NumberFormatException ignored) {
        }
      }

      // Legacy fallback: series index from tool-specific meta element
      if (result.getSeriesNumber() == null && LegacyMeta.SERIES_INDEX.equals(name)) {
        try {
          result.setSeriesNumber(Float.parseFloat(content));
        } catch (NumberFormatException ignored) {
        }
      }
    }
  }

  /** Reads page count from various standard and legacy meta elements. */
  private static void readPageCount(Element metadataElement, Metadata result) {
    NodeList metaTags = metadataElement.getElementsByTagName(OPFTags.meta);
    int metaTagCount = metaTags.getLength();

    for (int i = 0; i < metaTagCount; i++) {
      Element meta = (Element) metaTags.item(i);
      String property = meta.getAttribute(OPFAttributes.property);
      String name = meta.getAttribute(OPFAttributes.name);
      String content = meta.getAttribute(OPFAttributes.content);
      String text = meta.getTextContent().trim();

      String valueToTry = null;

      if (MetaProperties.SCHEMA_PAGE_COUNT.equals(property)
          || MetaProperties.MEDIA_PAGE_COUNT.equals(property)) {
        valueToTry = text;
      } else if (LegacyMeta.PAGES.equals(name) || "pagecount".equals(name)) {
        valueToTry = content;
      }

      if (valueToTry != null && result.getPageCount() == null) {
        try {
          result.setPageCount(Integer.parseInt(valueToTry));
        } catch (NumberFormatException ignored) {
        }
      }
    }
  }

  /**
   * Reads EPUB3 rendition and media duration properties from {@code <meta property="...">}
   * elements. Handles rendition:layout, rendition:orientation, rendition:spread, and media:duration
   * (total, not per-item).
   */
  private static void readRenditionProperties(Element metadataElement, Metadata result) {
    NodeList metaTags = metadataElement.getElementsByTagName(OPFTags.meta);
    int metaTagCount = metaTags.getLength();

    for (int i = 0; i < metaTagCount; i++) {
      Element meta = (Element) metaTags.item(i);
      String property = meta.getAttribute(OPFAttributes.property);
      String refines = meta.getAttribute(OPFAttributes.refines);
      if (StringUtil.isBlank(property)) {
        continue;
      }
      String text = meta.getTextContent().trim();
      if (StringUtil.isBlank(text)) {
        continue;
      }

      switch (property) {
        case "rendition:layout" -> {
          if (StringUtil.isBlank(refines) && result.getRenditionLayout() == null) {
            result.setRenditionLayout(text);
          }
        }
        case "rendition:orientation" -> {
          if (StringUtil.isBlank(refines) && result.getRenditionOrientation() == null) {
            result.setRenditionOrientation(text);
          }
        }
        case "rendition:spread" -> {
          if (StringUtil.isBlank(refines) && result.getRenditionSpread() == null) {
            result.setRenditionSpread(text);
          }
        }
        case "media:duration" -> {
          // Only read total duration (no refines), not per-SMIL durations
          if (StringUtil.isBlank(refines) && result.getMediaDuration() == null) {
            result.setMediaDuration(text);
          }
        }
        default -> {}
      }
    }
  }
}
