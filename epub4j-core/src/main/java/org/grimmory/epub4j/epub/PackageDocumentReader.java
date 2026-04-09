/*
 * Originally from epub4j (https://github.com/documentnode/epub4j)
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) Paul Siegmund and epub4j contributors
 *
 * Modifications:
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Guide;
import org.grimmory.epub4j.domain.GuideReference;
import org.grimmory.epub4j.domain.ManifestItemProperties;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.grimmory.epub4j.domain.Spine;
import org.grimmory.epub4j.domain.SpineReference;
import org.grimmory.epub4j.util.ResourceUtil;
import org.grimmory.epub4j.util.StringUtil;
import org.grimmory.epub4j.util.XmlCleaner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the opf package document as defined by namespace http://www.idpf.org/2007/opf
 *
 * @author paul
 */
public class PackageDocumentReader extends PackageDocumentBase {

  private static final System.Logger log = System.getLogger(PackageDocumentReader.class.getName());
  private static final String[] POSSIBLE_NCX_ITEM_IDS = {"toc", "ncx", "ncxtoc"};
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  public static void read(
      Resource packageResource, EpubReader epubReader, Book book, Resources resources)
      throws SAXException, IOException, ParserConfigurationException {
    Document packageDocument = parsePackageDocument(packageResource);
    String packageHref = packageResource.getHref();
    resources = fixHrefs(packageHref, resources);
    readGuide(packageDocument, book, resources);

    // Books sometimes use non-identifier ids. We map these here to legal ones
    Map<String, String> idMapping = new HashMap<>();

    resources = readManifest(packageDocument, resources, idMapping);
    book.setResources(resources);

    // Detect EPUB3 nav resource from manifest item properties
    for (Resource r : resources.getAll()) {
      if (r.hasProperty(ManifestItemProperties.NAV)) {
        book.setNavResource(r);
        break;
      }
    }

    readCover(packageDocument, book);
    book.setMetadata(PackageDocumentMetadataReader.readMetadata(packageDocument));
    book.setSpine(readSpine(packageDocument, book.getResources(), idMapping));

    // if we did not find a cover page then we make the first page of the book the cover page
    if (book.getCoverPage() == null && !book.getSpine().isEmpty()) {
      book.setCoverPage(book.getSpine().getResource(0));
    }
  }

  /**
   * Parses the OPF package document with a fallback tier: first attempts strict XML parsing, then
   * retries after cleaning invalid XML characters that are common in real-world EPUBs.
   */
  private static Document parsePackageDocument(Resource packageResource)
      throws SAXException, IOException, ParserConfigurationException {
    try {
      return ResourceUtil.getAsDocument(packageResource);
    } catch (SAXException e) {
      log.log(
          System.Logger.Level.WARNING,
          "Strict XML parse of package document failed, retrying with cleaned XML: "
              + e.getMessage());
      byte[] data = packageResource.getData();
      if (data == null) {
        throw e;
      }
      String cleaned = XmlCleaner.cleanForXml(new String(data, StandardCharsets.UTF_8));
      packageResource.setData(cleaned.getBytes(StandardCharsets.UTF_8));
      return ResourceUtil.getAsDocument(packageResource);
    }
  }

  //	private static Resource readCoverImage(Element metadataElement, Resources resources) {
  //		String coverResourceId = DOMUtil.getFindAttributeValue(metadataElement.getOwnerDocument(),
  // NAMESPACE_OPF, OPFTags.meta, OPFAttributes.name, OPFValues.meta_cover, OPFAttributes.content);
  //		if (StringUtil.isBlank(coverResourceId)) {
  //			return null;
  //		}
  //		Resource coverResource = resources.getByIdOrHref(coverResourceId);
  //		return coverResource;
  //	}

  /**
   * Reads the manifest containing the resource ids, hrefs and mediatypes.
   *
   * @param packageDocument the OPF package document
   * @param resources the loaded resources
   * @param idMapping output map for id remapping
   * @return a Map with resources, with their id's as key.
   */
  private static Resources readManifest(
      Document packageDocument, Resources resources, Map<String, String> idMapping) {
    Element manifestElement =
        DOMUtil.getFirstElementByTagNameNS(
            packageDocument.getDocumentElement(), NAMESPACE_OPF, OPFTags.manifest);
    Resources result = new Resources();
    if (manifestElement == null) {
      // Namespace-tolerant fallback: try without namespace qualification
      manifestElement =
          getFirstElementByLocalName(packageDocument.getDocumentElement(), OPFTags.manifest);
    }
    if (manifestElement == null) {
      log.log(
          System.Logger.Level.ERROR,
          "Package document does not contain element " + OPFTags.manifest);
      return result;
    }
    NodeList itemElements = manifestElement.getElementsByTagNameNS(NAMESPACE_OPF, OPFTags.item);
    int itemCount = itemElements.getLength();
    for (int i = 0; i < itemCount; i++) {
      Element itemElement = (Element) itemElements.item(i);
      String id = DOMUtil.getAttribute(itemElement, NAMESPACE_OPF, OPFAttributes.id);
      String href = DOMUtil.getAttribute(itemElement, NAMESPACE_OPF, OPFAttributes.href);
      href = decodeUtf8(href);
      String mediaTypeName =
          DOMUtil.getAttribute(itemElement, NAMESPACE_OPF, OPFAttributes.media_type);
      Resource resource = resources.remove(href);
      if (resource == null) {
        log.log(System.Logger.Level.ERROR, "resource with href '" + href + "' not found");
        continue;
      }
      resource.setId(id);
      MediaType mediaType = MediaTypes.getMediaTypeByName(mediaTypeName);
      if (mediaType == null) {
        // Fall back to extension-based detection when manifest media-type is missing or
        // unrecognized
        mediaType = MediaTypes.determineMediaType(href);
      }
      if (mediaType != null) {
        resource.setMediaType(mediaType);
      }

      // Parse EPUB3 manifest item properties
      String propertiesAttr =
          DOMUtil.getAttribute(itemElement, NAMESPACE_OPF, OPFAttributes.properties);
      if (propertiesAttr == null || propertiesAttr.isEmpty()) {
        propertiesAttr = itemElement.getAttribute(OPFAttributes.properties);
      }
      if (propertiesAttr != null && !propertiesAttr.isEmpty()) {
        Set<ManifestItemProperties> props = EnumSet.noneOf(ManifestItemProperties.class);
        for (String token : WHITESPACE_PATTERN.split(propertiesAttr.trim())) {
          for (ManifestItemProperties mip : ManifestItemProperties.values()) {
            if (mip.getName().equals(token)) {
              props.add(mip);
              break;
            }
          }
        }
        resource.setProperties(props);
      }

      // Parse media-overlay attribute
      String mediaOverlay = DOMUtil.getAttribute(itemElement, NAMESPACE_OPF, "media-overlay");
      if (mediaOverlay == null || mediaOverlay.isEmpty()) {
        mediaOverlay = itemElement.getAttribute("media-overlay");
      }
      if (mediaOverlay != null && !mediaOverlay.isEmpty()) {
        resource.setMediaOverlayId(mediaOverlay);
      }

      result.add(resource);
      idMapping.put(id, resource.getId());
    }
    return result;
  }

  /**
   * Reads the book's guide. Here some more attempts are made at finding the cover page.
   *
   * @param packageDocument the OPF package document
   * @param book the book being built
   * @param resources the loaded resources
   */
  private static void readGuide(Document packageDocument, Book book, Resources resources) {
    Element guideElement =
        DOMUtil.getFirstElementByTagNameNS(
            packageDocument.getDocumentElement(), NAMESPACE_OPF, OPFTags.guide);
    if (guideElement == null) {
      return;
    }
    Guide guide = book.getGuide();
    NodeList guideReferences =
        guideElement.getElementsByTagNameNS(NAMESPACE_OPF, OPFTags.reference);
    int referenceCount = guideReferences.getLength();
    for (int i = 0; i < referenceCount; i++) {
      Element referenceElement = (Element) guideReferences.item(i);
      String resourceHref =
          DOMUtil.getAttribute(referenceElement, NAMESPACE_OPF, OPFAttributes.href);
      if (StringUtil.isBlank(resourceHref)) {
        continue;
      }
      Resource resource =
          resources.getByHref(
              StringUtil.substringBefore(resourceHref, Constants.FRAGMENT_SEPARATOR_CHAR));
      if (resource == null) {
        log.log(
            System.Logger.Level.ERROR,
            "Guide is referencing resource with href "
                + resourceHref
                + " which could not be found");
        continue;
      }
      String type = DOMUtil.getAttribute(referenceElement, NAMESPACE_OPF, OPFAttributes.type);
      if (StringUtil.isBlank(type)) {
        log.log(
            System.Logger.Level.ERROR,
            "Guide is referencing resource with href "
                + resourceHref
                + " which is missing the 'type' attribute");
        continue;
      }
      String title = DOMUtil.getAttribute(referenceElement, NAMESPACE_OPF, OPFAttributes.title);
      if (GuideReference.COVER.equalsIgnoreCase(type)) {
        continue; // cover is handled elsewhere
      }
      GuideReference reference =
          new GuideReference(
              resource,
              type,
              title,
              StringUtil.substringAfter(resourceHref, Constants.FRAGMENT_SEPARATOR_CHAR));
      guide.addReference(reference);
    }
  }

  /**
   * Strips off the package prefixes up to the href of the packageHref.
   *
   * <p>Example: If the packageHref is "OEBPS/content.opf" then a resource href like
   * "OEBPS/foo/bar.html" will be turned into "foo/bar.html"
   *
   * @param packageHref
   * @param resourcesByHref
   * @return The stripped package href
   */
  static Resources fixHrefs(String packageHref, Resources resourcesByHref) {
    int lastSlashPos = packageHref.lastIndexOf('/');
    if (lastSlashPos < 0) {
      return resourcesByHref;
    }
    Resources result = new Resources();
    for (Resource resource : resourcesByHref.getAll()) {
      if (StringUtil.isNotBlank(resource.getHref()) && resource.getHref().length() > lastSlashPos) {
        resource.setHref(resource.getHref().substring(lastSlashPos + 1));
      }
      result.add(resource);
    }
    return result;
  }

  /**
   * Reads the document's spine, containing all sections in reading order.
   *
   * @param packageDocument
   * @param resources
   * @param idMapping
   * @return the document's spine, containing all sections in reading order.
   */
  private static Spine readSpine(
      Document packageDocument, Resources resources, Map<String, String> idMapping) {

    Element spineElement =
        DOMUtil.getFirstElementByTagNameNS(
            packageDocument.getDocumentElement(), NAMESPACE_OPF, OPFTags.spine);
    if (spineElement == null) {
      // Namespace-tolerant fallback
      spineElement =
          getFirstElementByLocalName(packageDocument.getDocumentElement(), OPFTags.spine);
    }
    if (spineElement == null) {
      log.log(
          System.Logger.Level.ERROR,
          "Element "
              + OPFTags.spine
              + " not found in package document, generating one automatically");
      return generateSpineFromResources(resources);
    }
    Spine result = new Spine();
    String tocResourceId = DOMUtil.getAttribute(spineElement, NAMESPACE_OPF, OPFAttributes.toc);
    result.setTocResource(findTableOfContentsResource(tocResourceId, resources));

    // EPUB3 page-progression-direction
    String ppd = DOMUtil.getAttribute(spineElement, NAMESPACE_OPF, "page-progression-direction");
    if (ppd == null || ppd.isEmpty()) {
      ppd = spineElement.getAttribute("page-progression-direction");
    }
    if (ppd != null && !ppd.isEmpty()) {
      result.setPageProgressionDirection(ppd);
    }

    NodeList spineNodes = packageDocument.getElementsByTagNameNS(NAMESPACE_OPF, OPFTags.itemref);
    int spineNodeCount = spineNodes.getLength();
    List<SpineReference> spineReferences = new ArrayList<>(spineNodeCount);
    for (int i = 0; i < spineNodeCount; i++) {
      Element spineItem = (Element) spineNodes.item(i);
      String itemref = DOMUtil.getAttribute(spineItem, NAMESPACE_OPF, OPFAttributes.idref);
      if (StringUtil.isBlank(itemref)) {
        log.log(System.Logger.Level.ERROR, "itemref with missing or empty idref");
        continue;
      }
      String id = idMapping.get(itemref);
      if (id == null) {
        id = itemref;
      }
      Resource resource = resources.getByIdOrHref(id);
      if (resource == null) {
        log.log(System.Logger.Level.ERROR, "resource with id '" + id + "' not found");
        continue;
      }

      SpineReference spineReference = new SpineReference(resource);
      if (OPFValues.no.equalsIgnoreCase(
          DOMUtil.getAttribute(spineItem, NAMESPACE_OPF, OPFAttributes.linear))) {
        spineReference.setLinear(false);
      }
      spineReferences.add(spineReference);
    }
    result.setSpineReferences(spineReferences);
    return result;
  }

  /**
   * Creates a spine out of all resources in the resources. The generated spine consists of all
   * XHTML pages in order of their href.
   *
   * @param resources
   * @return a spine created out of all resources in the resources.
   */
  private static Spine generateSpineFromResources(Resources resources) {
    Spine result = new Spine();
    List<String> resourceHrefs = new ArrayList<>(resources.getAllHrefs());
    resourceHrefs.sort(String.CASE_INSENSITIVE_ORDER);
    for (String resourceHref : resourceHrefs) {
      Resource resource = resources.getByHref(resourceHref);
      if (resource.getMediaType() == MediaTypes.NCX) {
        result.setTocResource(resource);
      } else if (resource.getMediaType() == MediaTypes.XHTML) {
        result.addSpineReference(new SpineReference(resource));
      }
    }
    return result;
  }

  /**
   * The spine tag should contain a 'toc' attribute with as value the resource id of the table of
   * contents resource.
   *
   * <p>Here we try several ways of finding this table of contents resource. We try the given
   * attribute value, some often-used ones and finally look through all resources for the first
   * resource with the table of contents mimetype.
   *
   * @param tocResourceId
   * @param resources
   * @return the Resource containing the table of contents
   */
  static Resource findTableOfContentsResource(String tocResourceId, Resources resources) {
    Resource tocResource = null;
    if (StringUtil.isNotBlank(tocResourceId)) {
      tocResource = resources.getByIdOrHref(tocResourceId);
    }

    if (tocResource != null) {
      return tocResource;
    }

    // Try conventional NCX item IDs first
    for (String possibleNcxItemId : POSSIBLE_NCX_ITEM_IDS) {
      tocResource = resources.getByIdOrHref(possibleNcxItemId);
      if (tocResource != null) {
        break;
      }
      tocResource = resources.getByIdOrHref(possibleNcxItemId.toUpperCase());
      if (tocResource != null) {
        break;
      }
    }

    // Fall back to the first resource with the NCX mediatype
    if (tocResource == null) {
      tocResource = resources.findFirstResourceByMediaType(MediaTypes.NCX);
    }

    if (tocResource == null) {
      log.log(
          System.Logger.Level.ERROR,
          "Could not find table of contents resource. Tried resource with id '"
              + tocResourceId
              + "', "
              + Constants.DEFAULT_TOC_ID
              + ", "
              + Constants.DEFAULT_TOC_ID.toUpperCase()
              + " and any NCX resource.");
    }
    return tocResource;
  }

  /**
   * Find all resources that have something to do with the coverpage and the cover image. Search the
   * meta tags and the guide references
   *
   * @param packageDocument
   * @return all resources that have something to do with the coverpage and the cover image.
   */
  // package
  static Set<String> findCoverHrefs(Document packageDocument) {

    Set<String> result = new HashSet<>();

    // EPUB3: Look for manifest item with properties="cover-image"
    Element manifestElement =
        DOMUtil.getFirstElementByTagNameNS(
            packageDocument.getDocumentElement(), NAMESPACE_OPF, OPFTags.manifest);
    if (manifestElement != null) {
      NodeList items = manifestElement.getElementsByTagNameNS(NAMESPACE_OPF, OPFTags.item);
      int itemCount = items.getLength();
      for (int i = 0; i < itemCount; i++) {
        Element item = (Element) items.item(i);
        String properties = DOMUtil.getAttribute(item, NAMESPACE_OPF, OPFAttributes.properties);
        if (properties == null) {
          properties = item.getAttribute(OPFAttributes.properties);
        }
        if (properties.contains("cover-image")) {
          String href = DOMUtil.getAttribute(item, NAMESPACE_OPF, OPFAttributes.href);
          if (href == null) {
            href = item.getAttribute(OPFAttributes.href);
          }
          if (StringUtil.isNotBlank(href)) {
            href = decodeUtf8(href);
            result.add(href);
          }
        }
      }
    }

    // EPUB2: try and find a meta tag with name = 'cover' and a non-blank id
    String coverResourceId =
        DOMUtil.getFindAttributeValue(
            packageDocument,
            NAMESPACE_OPF,
            OPFTags.meta,
            OPFAttributes.name,
            OPFValues.meta_cover,
            OPFAttributes.content);

    if (StringUtil.isNotBlank(coverResourceId)) {
      String coverHref =
          DOMUtil.getFindAttributeValue(
              packageDocument,
              NAMESPACE_OPF,
              OPFTags.item,
              OPFAttributes.id,
              coverResourceId,
              OPFAttributes.href);
      if (StringUtil.isNotBlank(coverHref)) {
        coverHref = decodeUtf8(coverHref);
        result.add(coverHref);
      }
      // Don't add raw coverResourceId - it's an ID, not an href.
      // Let the heuristic fallback below handle unresolved covers.
    }
    // try and find a reference tag with type is 'cover' and reference is not blank
    String coverHref =
        DOMUtil.getFindAttributeValue(
            packageDocument,
            NAMESPACE_OPF,
            OPFTags.reference,
            OPFAttributes.type,
            OPFValues.reference_cover,
            OPFAttributes.href);
    if (StringUtil.isNotBlank(coverHref)) {
      coverHref = decodeUtf8(coverHref);
      result.add(coverHref);
    }

    // Heuristic fallback: search manifest for items where id or href
    // contains "cover" and media-type is an image
    if (result.isEmpty() && manifestElement != null) {
      NodeList items = manifestElement.getElementsByTagNameNS(NAMESPACE_OPF, OPFTags.item);
      int itemCount = items.getLength();
      for (int i = 0; i < itemCount; i++) {
        Element item = (Element) items.item(i);
        String id = DOMUtil.getAttribute(item, NAMESPACE_OPF, OPFAttributes.id);
        String href = DOMUtil.getAttribute(item, NAMESPACE_OPF, OPFAttributes.href);
        String mediaType = DOMUtil.getAttribute(item, NAMESPACE_OPF, OPFAttributes.media_type);

        if (mediaType != null && mediaType.startsWith("image/")) {
          boolean idMatch = id != null && id.toLowerCase(Locale.ROOT).contains("cover");
          boolean hrefMatch = href != null && href.toLowerCase(Locale.ROOT).contains("cover");
          if (idMatch || hrefMatch) {
            if (StringUtil.isNotBlank(href)) {
              href = decodeUtf8(href);
              result.add(href);
              break;
            }
          }
        }
      }
    }

    return result;
  }

  /**
   * Finds the cover resource in the packageDocument and adds it to the book if found. Keeps the
   * cover resource in the resources map.
   *
   * @param packageDocument the OPF package document
   * @param book the book being built
   */
  private static void readCover(Document packageDocument, Book book) {

    // EPUB3: detect cover image via manifest item properties="cover-image"
    if (book.getCoverImage() == null) {
      for (Resource r : book.getResources().getAll()) {
        if (r.hasProperty(ManifestItemProperties.COVER_IMAGE)
            && MediaTypes.isBitmapImage(r.getMediaType())) {
          book.setCoverImage(r);
          break;
        }
      }
    }

    Collection<String> coverHrefs = findCoverHrefs(packageDocument);
    for (String coverHref : coverHrefs) {
      Resource resource = book.getResources().getByHref(coverHref);
      if (resource == null) {
        log.log(System.Logger.Level.ERROR, "Cover resource " + coverHref + " not found");
        continue;
      }
      if (resource.getMediaType() == MediaTypes.XHTML) {
        book.setCoverPage(resource);
      } else if (book.getCoverImage() == null
          && MediaTypes.isBitmapImage(resource.getMediaType())) {
        book.setCoverImage(resource);
      }
    }
  }

  /**
   * Namespace-tolerant element lookup by local name only. Handles EPUBs with missing or incorrect
   * OPF namespace declarations.
   */
  private static Element getFirstElementByLocalName(Element parent, String localName) {
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
      log.log(System.Logger.Level.DEBUG, "Failed to decode href: " + value);
      return value;
    }
  }
}
