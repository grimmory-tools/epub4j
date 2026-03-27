package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Spine;
import org.grimmory.epub4j.domain.SpineReference;
import org.grimmory.epub4j.util.StringUtil;
import org.xmlpull.v1.XmlSerializer;

/**
 * Writes the OPF package document in EPUB3 format as defined by namespace
 * http://www.idpf.org/2007/opf
 *
 * @author paul
 */
public class PackageDocumentWriter extends PackageDocumentBase {

  private static final System.Logger log = System.getLogger(PackageDocumentWriter.class.getName());

  public static void write(EpubWriter epubWriter, XmlSerializer serializer, Book book)
      throws IOException {
    serializer.startDocument(Constants.CHARACTER_ENCODING, false);
    // EPUB3: Use OPF as the default namespace (no opf: prefix on elements)
    serializer.setPrefix(EpubWriter.EMPTY_NAMESPACE_PREFIX, NAMESPACE_OPF);
    serializer.setPrefix(PREFIX_DUBLIN_CORE, NAMESPACE_DUBLIN_CORE);
    serializer.startTag(NAMESPACE_OPF, OPFTags.packageTag);
    serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.version, "3.0");
    serializer.attribute(
        EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.uniqueIdentifier, BOOK_ID_ID);

    PackageDocumentMetadataWriter.writeMetaData(book, serializer);

    writeManifest(book, serializer);
    writeSpine(book, serializer);
    // EPUB3: <guide> element is deprecated; omitted entirely

    serializer.endTag(NAMESPACE_OPF, OPFTags.packageTag);
    serializer.endDocument();
    serializer.flush();
  }

  /** Writes the package's spine. */
  private static void writeSpine(Book book, XmlSerializer serializer)
      throws IllegalArgumentException, IllegalStateException, IOException {
    serializer.startTag(NAMESPACE_OPF, OPFTags.spine);
    Resource tocResource = book.getSpine().getTocResource();
    if (tocResource != null && StringUtil.isNotBlank(tocResource.getId())) {
      serializer.attribute(
          EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.toc, tocResource.getId());
    }

    // EPUB3/OPF-096: If cover page exists but is not in the spine,
    // add it as a linear item (first page). Non-linear items must be
    // reachable via hyperlinks, which is hard to guarantee for auto-inserted covers.
    if (book.getCoverPage() != null
        && book.getSpine().findFirstResourceById(book.getCoverPage().getId()) < 0) {
      serializer.startTag(NAMESPACE_OPF, OPFTags.itemref);
      serializer.attribute(
          EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.idref, book.getCoverPage().getId());
      serializer.endTag(NAMESPACE_OPF, OPFTags.itemref);
    }
    writeSpineItems(book.getSpine(), serializer);
    serializer.endTag(NAMESPACE_OPF, OPFTags.spine);
  }

  private static void writeManifest(Book book, XmlSerializer serializer)
      throws IllegalArgumentException, IllegalStateException, IOException {
    serializer.startTag(NAMESPACE_OPF, OPFTags.manifest);

    serializer.startTag(NAMESPACE_OPF, OPFTags.item);
    serializer.attribute(
        EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.id, EpubWriter.getNcxId());
    serializer.attribute(
        EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.href, EpubWriter.getNcxHref());
    serializer.attribute(
        EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.media_type, EpubWriter.getNcxMediaType());
    serializer.endTag(NAMESPACE_OPF, OPFTags.item);

    for (Resource resource : getAllResourcesSortById(book)) {
      writeItem(book, resource, serializer);
    }

    serializer.endTag(NAMESPACE_OPF, OPFTags.manifest);
  }

  private static List<Resource> getAllResourcesSortById(Book book) {
    List<Resource> allResources = new ArrayList<>(book.getResources().getAll());
    allResources.sort(
        (resource1, resource2) -> resource1.getId().compareToIgnoreCase(resource2.getId()));
    return allResources;
  }

  /**
   * Writes a resource as an item element.
   *
   * <p>EPUB3: Adds {@code properties} attribute when the resource is:
   *
   * <ul>
   *   <li>The NAV document -&gt; {@code properties="nav"}
   *   <li>The cover image -&gt; {@code properties="cover-image"}
   * </ul>
   */
  private static void writeItem(Book book, Resource resource, XmlSerializer serializer)
      throws IllegalArgumentException, IllegalStateException, IOException {
    if (resource == null
        || (resource.getMediaType() == MediaTypes.NCX
            && book.getSpine().getTocResource() != null)) {
      return;
    }
    if (StringUtil.isBlank(resource.getId())) {
      log.log(
          System.Logger.Level.ERROR,
          "resource id must not be empty (href: "
              + resource.getHref()
              + ", mediatype:"
              + resource.getMediaType()
              + ")");
      return;
    }
    if (StringUtil.isBlank(resource.getHref())) {
      log.log(
          System.Logger.Level.ERROR,
          "resource href must not be empty (id: "
              + resource.getId()
              + ", mediatype:"
              + resource.getMediaType()
              + ")");
      return;
    }
    if (resource.getMediaType() == null) {
      log.log(
          System.Logger.Level.ERROR,
          "resource mediatype must not be empty (id: "
              + resource.getId()
              + ", href:"
              + resource.getHref()
              + ")");
      return;
    }
    serializer.startTag(NAMESPACE_OPF, OPFTags.item);
    serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.id, resource.getId());
    serializer.attribute(
        EpubWriter.EMPTY_NAMESPACE_PREFIX,
        OPFAttributes.href,
        EpubWriter.encodeHref(resource.getHref()));
    serializer.attribute(
        EpubWriter.EMPTY_NAMESPACE_PREFIX,
        OPFAttributes.media_type,
        resource.getMediaType().name());

    // EPUB3: Add properties attribute where needed
    String properties = getItemProperties(book, resource);
    if (properties != null) {
      serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.properties, properties);
    }

    serializer.endTag(NAMESPACE_OPF, OPFTags.item);
  }

  /**
   * Determines the EPUB3 {@code properties} attribute value for a manifest item.
   *
   * @return the properties string, or null if no properties apply
   */
  private static String getItemProperties(Book book, Resource resource) {
    List<String> props = new ArrayList<>();

    // NAV document gets properties="nav"
    if (NavDocument.NAV_ITEM_ID.equals(resource.getId())) {
      props.add("nav");
    }

    // Cover image gets properties="cover-image"
    if (book.getCoverImage() != null && book.getCoverImage().getHref().equals(resource.getHref())) {
      props.add("cover-image");
    }

    return props.isEmpty() ? null : String.join(" ", props);
  }

  /** List all spine references */
  private static void writeSpineItems(Spine spine, XmlSerializer serializer)
      throws IllegalArgumentException, IllegalStateException, IOException {
    // OPF_034: Deduplicate spine references
    Set<String> spineIds = new LinkedHashSet<>();
    boolean hasLinear = false;

    for (SpineReference spineReference : spine.getSpineReferences()) {
      // Skip duplicate spine references (OPF_034)
      if (!spineIds.add(spineReference.getResourceId())) {
        log.log(
            System.Logger.Level.WARNING,
            "Duplicate spine reference removed: " + spineReference.getResourceId());
        continue;
      }

      if (spineReference.isLinear()) {
        hasLinear = true;
      }

      serializer.startTag(NAMESPACE_OPF, OPFTags.itemref);
      serializer.attribute(
          EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.idref, spineReference.getResourceId());
      if (!spineReference.isLinear()) {
        serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.linear, OPFValues.no);
      }
      serializer.endTag(NAMESPACE_OPF, OPFTags.itemref);
    }

    // OPF_033: Ensure at least one linear item exists
    if (!hasLinear && !spineIds.isEmpty()) {
      log.log(
          System.Logger.Level.WARNING,
          "Spine has no linear items; preserving explicit linear attributes");
    }
  }
}
