package org.grimmory.epub4j.epub;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Metadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Streaming EPUB metadata writer that modifies OPF metadata in-place without extracting the entire
 * archive to disk. Reads the ZIP once, modifies the OPF document in memory, and rewrites the ZIP in
 * a single pass.
 */
public class EpubMetadataWriter {

  private static final System.Logger log = System.getLogger(EpubMetadataWriter.class.getName());

  private static final String OPF_NS = "http://www.idpf.org/2007/opf";
  private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
  private static final String MIMETYPE_ENTRY = "mimetype";
  private static final String CONTAINER_XML = "META-INF/container.xml";

  private EpubMetadataWriter() {}

  /**
   * Updates metadata in an existing EPUB file using a streaming ZIP rewrite. The modifier receives
   * the parsed OPF Document for direct DOM manipulation.
   *
   * @param epubPath path to the EPUB file
   * @param modifier consumer that receives the OPF Document for modification
   * @throws IOException if an I/O error occurs
   */
  public static void updateMetadata(Path epubPath, Consumer<Document> modifier) throws IOException {
    Objects.requireNonNull(epubPath, "epubPath");
    Objects.requireNonNull(modifier, "modifier");

    Path tempFile = epubPath.resolveSibling(epubPath.getFileName() + ".tmp");
    try {
      rewriteEpub(epubPath, tempFile, modifier, null);
      Files.move(tempFile, epubPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException suppressed) {
        e.addSuppressed(suppressed);
      }
      throw e;
    }
  }

  /**
   * Updates metadata fields from a Book's Metadata object. Replaces DC elements and EPUB3 meta
   * properties to match the given metadata.
   *
   * @param epubPath path to the EPUB file
   * @param metadata the metadata to apply
   * @throws IOException if an I/O error occurs
   */
  public static void writeMetadata(Path epubPath, Metadata metadata) throws IOException {
    Objects.requireNonNull(metadata, "metadata");
    updateMetadata(epubPath, doc -> applyMetadata(doc, metadata));
  }

  /**
   * Replaces the cover image in an existing EPUB file without extracting to disk.
   *
   * @param epubPath path to the EPUB file
   * @param coverData the new cover image bytes
   * @throws IOException if an I/O error occurs or no cover item is found
   */
  public static void replaceCoverImage(Path epubPath, byte[] coverData) throws IOException {
    Objects.requireNonNull(epubPath, "epubPath");
    Objects.requireNonNull(coverData, "coverData");
    if (coverData.length == 0) {
      throw new IllegalArgumentException("coverData must not be empty");
    }

    Path tempFile = epubPath.resolveSibling(epubPath.getFileName() + ".tmp");
    try {
      rewriteEpub(epubPath, tempFile, null, coverData);
      Files.move(tempFile, epubPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException suppressed) {
        e.addSuppressed(suppressed);
      }
      throw e;
    }
  }

  /**
   * Writes a Book's metadata to an EPUB file at the given output path. Creates a complete EPUB
   * using EpubWriter but then patches the OPF with the book's metadata.
   *
   * @param book the book model to write
   * @param outputPath output file path
   * @throws IOException if an I/O error occurs
   */
  public static void writeMetadata(Book book, Path outputPath) throws IOException {
    Objects.requireNonNull(book, "book");
    Objects.requireNonNull(outputPath, "outputPath");

    try (OutputStream out = Files.newOutputStream(outputPath)) {
      new EpubWriter().write(book, out);
    }
  }

  // -- Streaming ZIP rewrite engine --

  private static void rewriteEpub(
      Path source, Path target, Consumer<Document> modifier, byte[] newCoverData)
      throws IOException {

    String opfEntryPath = findOpfPath(source);
    String coverHref = null;

    // If replacing cover, resolve the cover href from OPF before rewriting
    if (newCoverData != null) {
      coverHref = resolveCoverHref(source, opfEntryPath);
      if (coverHref == null) {
        throw new IOException("No cover image item found in OPF manifest");
      }
    }

    try (InputStream fis = Files.newInputStream(source);
        ZipInputStream zis = new ZipInputStream(fis);
        OutputStream fos = Files.newOutputStream(target);
        ZipOutputStream zos = new ZipOutputStream(fos)) {

      ZipEntry entry;
      boolean mimetypeWritten = false;

      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName();

        if (MIMETYPE_ENTRY.equals(name)) {
          // Preserve mimetype as first STORED entry
          writeMimetypeEntry(zos, zis.readAllBytes());
          mimetypeWritten = true;
          continue;
        }

        if (name.equals(opfEntryPath) && modifier != null) {
          byte[] opfBytes = zis.readAllBytes();
          byte[] modifiedOpf = modifyOpf(opfBytes, modifier);
          writeDeflatedEntry(zos, name, modifiedOpf);
          continue;
        }

        if (coverHref != null && name.equals(coverHref)) {
          writeStoredEntry(zos, name, newCoverData);
          continue;
        }

        // Copy entry as-is
        copyEntry(zos, zis, entry);
      }

      if (!mimetypeWritten) {
        log.log(System.Logger.Level.WARNING, "Source EPUB had no mimetype entry");
      }
    }
  }

  private static String findOpfPath(Path epubPath) throws IOException {
    try (InputStream fis = Files.newInputStream(epubPath);
        ZipInputStream zis = new ZipInputStream(fis)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (CONTAINER_XML.equals(entry.getName())) {
          return parseOpfPathFromContainer(zis.readAllBytes());
        }
      }
    }
    throw new IOException("No container.xml found in EPUB");
  }

  private static String parseOpfPathFromContainer(byte[] containerXml) throws IOException {
    try {
      DocumentBuilder builder = createSecureDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(containerXml));
      NodeList rootfiles = doc.getElementsByTagName("rootfile");
      if (rootfiles.getLength() == 0) {
        throw new IOException("No <rootfile> element found in container.xml");
      }
      String fullPath = ((Element) rootfiles.item(0)).getAttribute("full-path");
      if (fullPath == null || fullPath.isBlank()) {
        throw new IOException("Missing full-path attribute on rootfile");
      }
      return fullPath;
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Failed to parse container.xml", e);
    }
  }

  private static String resolveCoverHref(Path epubPath, String opfEntryPath) throws IOException {
    try (InputStream fis = Files.newInputStream(epubPath);
        ZipInputStream zis = new ZipInputStream(fis)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().equals(opfEntryPath)) {
          byte[] opfBytes = zis.readAllBytes();
          return findCoverHrefInOpf(opfBytes, opfEntryPath);
        }
      }
    }
    return null;
  }

  private static String findCoverHrefInOpf(byte[] opfBytes, String opfEntryPath)
      throws IOException {
    try {
      DocumentBuilder builder = createSecureDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(opfBytes));

      Element manifest =
          DOMUtil.getFirstElementByTagNameNS(doc.getDocumentElement(), OPF_NS, "manifest");
      if (manifest == null) {
        return null;
      }

      // Strategy 1: meta name="cover" content -> manifest item id
      Element metadata =
          DOMUtil.getFirstElementByTagNameNS(doc.getDocumentElement(), OPF_NS, "metadata");
      if (metadata != null) {
        String coverItemId =
            DOMUtil.getFindAttributeValue(doc, OPF_NS, "meta", "name", "cover", "content");
        if (coverItemId == null) {
          coverItemId = DOMUtil.getFindAttributeValue(doc, "", "meta", "name", "cover", "content");
        }
        if (coverItemId != null) {
          String href = findItemHrefById(manifest, coverItemId);
          if (href != null) {
            return resolveHrefRelativeToOpf(opfEntryPath, href);
          }
        }
      }

      // Strategy 2: properties="cover-image"
      NodeList items = manifest.getElementsByTagNameNS(OPF_NS, "item");
      for (int i = 0; i < items.getLength(); i++) {
        Element item = (Element) items.item(i);
        String props = item.getAttribute("properties");
        if (props != null && props.contains("cover-image")) {
          String href = item.getAttribute("href");
          if (href != null && !href.isBlank()) {
            return resolveHrefRelativeToOpf(opfEntryPath, href);
          }
        }
      }

      // Strategy 3: common cover id values
      for (String candidateId : new String[] {"cover-image", "cover", "coverimg"}) {
        String href = findItemHrefById(manifest, candidateId);
        if (href != null) {
          return resolveHrefRelativeToOpf(opfEntryPath, href);
        }
      }

    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Failed to parse OPF document", e);
    }
    return null;
  }

  private static String findItemHrefById(Element manifest, String itemId) {
    NodeList items = manifest.getElementsByTagNameNS(OPF_NS, "item");
    for (int i = 0; i < items.getLength(); i++) {
      Element item = (Element) items.item(i);
      if (itemId.equals(item.getAttribute("id"))) {
        String href = item.getAttribute("href");
        if (href != null && !href.isBlank()) {
          return URLDecoder.decode(href, StandardCharsets.UTF_8);
        }
      }
    }
    return null;
  }

  private static String resolveHrefRelativeToOpf(String opfEntryPath, String href) {
    int lastSlash = opfEntryPath.lastIndexOf('/');
    if (lastSlash < 0) {
      return href;
    }
    return opfEntryPath.substring(0, lastSlash + 1) + href;
  }

  private static byte[] modifyOpf(byte[] opfBytes, Consumer<Document> modifier) throws IOException {
    try {
      DocumentBuilder builder = createSecureDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(opfBytes));
      modifier.accept(doc);
      return serializeDocument(doc);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Failed to parse OPF for modification", e);
    }
  }

  private static byte[] serializeDocument(Document doc) throws IOException {
    try {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(doc), new StreamResult(out));
      return out.toByteArray();
    } catch (TransformerException e) {
      throw new IOException("Failed to serialize OPF document", e);
    }
  }

  // -- ZIP entry helpers --

  private static void writeMimetypeEntry(ZipOutputStream zos, byte[] data) throws IOException {
    ZipEntry mimeEntry = new ZipEntry(MIMETYPE_ENTRY);
    mimeEntry.setMethod(ZipEntry.STORED);
    mimeEntry.setSize(data.length);
    mimeEntry.setCompressedSize(data.length);
    CRC32 crc = new CRC32();
    crc.update(data);
    mimeEntry.setCrc(crc.getValue());
    zos.putNextEntry(mimeEntry);
    zos.write(data);
    zos.closeEntry();
  }

  private static void writeDeflatedEntry(ZipOutputStream zos, String name, byte[] data)
      throws IOException {
    ZipEntry newEntry = new ZipEntry(name);
    newEntry.setMethod(ZipEntry.DEFLATED);
    zos.putNextEntry(newEntry);
    zos.write(data);
    zos.closeEntry();
  }

  private static void writeStoredEntry(ZipOutputStream zos, String name, byte[] data)
      throws IOException {
    ZipEntry newEntry = new ZipEntry(name);
    newEntry.setMethod(ZipEntry.STORED);
    newEntry.setSize(data.length);
    newEntry.setCompressedSize(data.length);
    CRC32 crc = new CRC32();
    crc.update(data);
    newEntry.setCrc(crc.getValue());
    zos.putNextEntry(newEntry);
    zos.write(data);
    zos.closeEntry();
  }

  private static void copyEntry(ZipOutputStream zos, ZipInputStream zis, ZipEntry original)
      throws IOException {
    byte[] data = zis.readAllBytes();

    ZipEntry copy = new ZipEntry(original.getName());
    if (original.getMethod() == ZipEntry.STORED) {
      copy.setMethod(ZipEntry.STORED);
      copy.setSize(original.getSize());
      copy.setCompressedSize(original.getCompressedSize());
      copy.setCrc(original.getCrc());
    } else {
      copy.setMethod(ZipEntry.DEFLATED);
    }
    zos.putNextEntry(copy);
    zos.write(data);
    zos.closeEntry();
  }

  // -- Metadata application --

  private static void applyMetadata(Document opfDoc, Metadata metadata) {
    Element metadataEl =
        DOMUtil.getFirstElementByTagNameNS(opfDoc.getDocumentElement(), OPF_NS, "metadata");
    if (metadataEl == null) {
      return;
    }

    // Titles
    if (!metadata.getTitles().isEmpty()) {
      removeElementsByNs(metadataEl, DC_NS, "title");
      for (String title : metadata.getTitles()) {
        if (title != null && !title.isBlank()) {
          Element el = opfDoc.createElementNS(DC_NS, "dc:title");
          el.setTextContent(title);
          metadataEl.appendChild(el);
        }
      }
    }

    // Authors
    if (!metadata.getAuthors().isEmpty()) {
      removeElementsByNs(metadataEl, DC_NS, "creator");
      for (var author : metadata.getAuthors()) {
        Element el = opfDoc.createElementNS(DC_NS, "dc:creator");
        String display = buildAuthorDisplay(author);
        el.setTextContent(display);
        metadataEl.appendChild(el);
      }
    }

    // Language
    if (metadata.getLanguage() != null && !metadata.getLanguage().isBlank()) {
      removeElementsByNs(metadataEl, DC_NS, "language");
      Element el = opfDoc.createElementNS(DC_NS, "dc:language");
      el.setTextContent(metadata.getLanguage());
      metadataEl.appendChild(el);
    }

    // Subjects
    removeElementsByNs(metadataEl, DC_NS, "subject");
    for (String subject : metadata.getSubjects()) {
      if (subject != null && !subject.isBlank()) {
        Element el = opfDoc.createElementNS(DC_NS, "dc:subject");
        el.setTextContent(subject.trim());
        metadataEl.appendChild(el);
      }
    }

    // Descriptions
    if (!metadata.getDescriptions().isEmpty()) {
      removeElementsByNs(metadataEl, DC_NS, "description");
      for (String desc : metadata.getDescriptions()) {
        if (desc != null && !desc.isBlank()) {
          Element el = opfDoc.createElementNS(DC_NS, "dc:description");
          el.setTextContent(desc);
          metadataEl.appendChild(el);
        }
      }
    }

    // Publishers
    if (!metadata.getPublishers().isEmpty()) {
      removeElementsByNs(metadataEl, DC_NS, "publisher");
      for (String pub : metadata.getPublishers()) {
        if (pub != null && !pub.isBlank()) {
          Element el = opfDoc.createElementNS(DC_NS, "dc:publisher");
          el.setTextContent(pub);
          metadataEl.appendChild(el);
        }
      }
    }

    // Update dcterms:modified timestamp
    updateModifiedTimestamp(opfDoc, metadataEl);
  }

  private static void updateModifiedTimestamp(Document doc, Element metadataEl) {
    // Remove existing dcterms:modified meta
    NodeList metas = metadataEl.getElementsByTagNameNS("*", "meta");
    for (int i = metas.getLength() - 1; i >= 0; i--) {
      Element meta = (Element) metas.item(i);
      if ("dcterms:modified".equals(meta.getAttribute("property"))) {
        metadataEl.removeChild(meta);
      }
    }

    Element modified = doc.createElementNS(OPF_NS, "meta");
    modified.setAttribute("property", "dcterms:modified");
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
    // Truncate to seconds per EPUB3 spec
    if (timestamp.endsWith("Z")) {
      modified.setTextContent(timestamp.substring(0, 19) + "Z");
    } else {
      modified.setTextContent(timestamp.substring(0, 19) + "+00:00");
    }
    metadataEl.appendChild(modified);
  }

  private static String buildAuthorDisplay(org.grimmory.epub4j.domain.Author author) {
    String first = author.getFirstname();
    String last = author.getLastname();
    boolean hasFirst = first != null && !first.trim().isEmpty();
    boolean hasLast = last != null && !last.trim().isEmpty();
    if (hasFirst && hasLast) {
      return first + " " + last;
    } else if (hasFirst) {
      return first;
    } else if (hasLast) {
      return last;
    }
    return "";
  }

  private static void removeElementsByNs(Element parent, String ns, String localName) {
    NodeList nodes = parent.getElementsByTagNameNS(ns, localName);
    for (int i = nodes.getLength() - 1; i >= 0; i--) {
      Node node = nodes.item(i);
      node.getParentNode().removeChild(node);
    }
  }

  // DocumentBuilderFactory is thread-safe after configuration; cache it to avoid
  // repeated ServiceLoader lookups on every parse call.
  private static final DocumentBuilderFactory SECURE_DBF;

  static {
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      SECURE_DBF = f;
    } catch (ParserConfigurationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static DocumentBuilder createSecureDocumentBuilder() throws ParserConfigurationException {
    return SECURE_DBF.newDocumentBuilder();
  }
}
