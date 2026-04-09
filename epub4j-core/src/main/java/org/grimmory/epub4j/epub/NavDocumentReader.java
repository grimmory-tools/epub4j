/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.TOCReference;
import org.grimmory.epub4j.domain.TableOfContents;
import org.grimmory.epub4j.util.ResourceUtil;
import org.grimmory.epub4j.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the EPUB3 XHTML Navigation Document as defined by
 * <a href="https://www.w3.org/TR/epub-33/#sec-nav">EPUB 3.3 §7 Navigation Document</a>.
 *
 * <p>Parses the {@code <nav epub:type="toc">} element to build a {@link TableOfContents}.
 */
public class NavDocumentReader {

  private static final System.Logger log = System.getLogger(NavDocumentReader.class.getName());
  private static final String NAMESPACE_XHTML = "http://www.w3.org/1999/xhtml";
  private static final String NAMESPACE_EPUB_OPS = "http://www.idpf.org/2007/ops";

  /**
   * Reads the EPUB3 nav document and populates the book's table of contents.
   *
   * @param book the book (must have navResource and resources set)
   * @return true if the nav document was successfully parsed, false otherwise
   */
  public static boolean read(Book book) {
    Resource navResource = book.getNavResource();
    if (navResource == null) {
      return false;
    }
    try {
      Document navDocument = ResourceUtil.getAsDocument(navResource);
      Element tocNav = findTocNav(navDocument.getDocumentElement());
      if (tocNav == null) {
        log.log(System.Logger.Level.WARNING, "No <nav epub:type=\"toc\"> found in nav document");
        return false;
      }
      Element rootOl = findFirstChildElement(tocNav, "ol");
      if (rootOl == null) {
        log.log(System.Logger.Level.WARNING, "No <ol> found inside toc nav element");
        return false;
      }
      String navHref = navResource.getHref();
      String navRoot = StringUtil.substringBeforeLast(navHref, '/');
      if (navRoot.length() == navHref.length()) {
        navRoot = "";
      } else {
        navRoot = navRoot + "/";
      }
      List<TOCReference> tocRefs = readOlEntries(rootOl, navRoot, book);
      if (!tocRefs.isEmpty()) {
        book.setTableOfContents(new TableOfContents(tocRefs));
        return true;
      }
    } catch (Exception e) {
      log.log(System.Logger.Level.ERROR, "Failed to parse nav document: " + e.getMessage(), e);
    }
    return false;
  }

  private static Element findTocNav(Element root) {
    NodeList navElements = root.getElementsByTagNameNS(NAMESPACE_XHTML, "nav");
    int count = navElements.getLength();
    if (count == 0) {
      // Namespace-tolerant fallback
      navElements = root.getElementsByTagName("nav");
      count = navElements.getLength();
    }
    for (int i = 0; i < count; i++) {
      Element nav = (Element) navElements.item(i);
      String epubType = nav.getAttributeNS(NAMESPACE_EPUB_OPS, "type");
      if (epubType == null || epubType.isEmpty()) {
        epubType = nav.getAttribute("epub:type");
      }
      if (epubType != null && epubType.contains("toc")) {
        return nav;
      }
    }
    return null;
  }

  private static List<TOCReference> readOlEntries(Element olElement, String navRoot, Book book) {
    List<TOCReference> result = new ArrayList<>();
    NodeList children = olElement.getChildNodes();
    int childCount = children.getLength();
    for (int i = 0; i < childCount; i++) {
      Node node = children.item(i);
      if (node instanceof Element li && "li".equals(li.getLocalName())) {
        TOCReference ref = readLiEntry(li, navRoot, book);
        if (ref != null) {
          result.add(ref);
        }
      }
    }
    return result;
  }

  private static TOCReference readLiEntry(Element liElement, String navRoot, Book book) {
    String label = null;
    String href = null;
    List<TOCReference> children = new ArrayList<>();

    NodeList liChildren = liElement.getChildNodes();
    int childCount = liChildren.getLength();
    for (int i = 0; i < childCount; i++) {
      Node node = liChildren.item(i);
      if (!(node instanceof Element child)) {
        continue;
      }
      String localName = child.getLocalName();
      if ("a".equals(localName)) {
        label = child.getTextContent().trim();
        href = child.getAttribute("href");
      } else if ("span".equals(localName) && label == null) {
        label = child.getTextContent().trim();
      } else if ("ol".equals(localName)) {
        children = readOlEntries(child, navRoot, book);
      }
    }

    if (label == null || label.isEmpty()) {
      label = "Untitled";
    }

    if (href != null && !href.isEmpty()) {
      href = decodeUtf8(href);
      String fullRef = StringUtil.collapsePathDots(navRoot + href);
      String resourceHref = StringUtil.substringBefore(fullRef, Constants.FRAGMENT_SEPARATOR_CHAR);
      String fragmentId = StringUtil.substringAfter(fullRef, Constants.FRAGMENT_SEPARATOR_CHAR);
      Resource resource = book.getResources().getByHref(resourceHref);
      if (resource == null) {
        log.log(System.Logger.Level.WARNING,
            "Resource with href '" + resourceHref + "' referenced in nav document not found");
      }
      TOCReference ref = new TOCReference(label, resource, fragmentId);
      ref.setChildren(children);
      return ref;
    }

    // No href — abstract section with only children
    if (!children.isEmpty()) {
      TOCReference ref = new TOCReference(label, null, null);
      ref.setChildren(children);
      return ref;
    }

    return null;
  }

  private static Element findFirstChildElement(Element parent, String localName) {
    NodeList children = parent.getChildNodes();
    int childCount = children.getLength();
    for (int i = 0; i < childCount; i++) {
      if (children.item(i) instanceof Element child && localName.equals(child.getLocalName())) {
        return child;
      }
    }
    return null;
  }

  private static String decodeUtf8(String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      log.log(System.Logger.Level.DEBUG, "Failed to decode nav href: " + value);
      return value;
    }
  }
}
