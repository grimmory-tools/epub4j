package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Metadata;
import org.grimmory.epub4j.domain.OffHeapResource;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.grimmory.epub4j.domain.Spine;
import org.grimmory.epub4j.domain.TableOfContents;
import org.grimmory.epub4j.util.IOUtil;
import org.grimmory.epub4j.util.StringUtil;
import org.xmlpull.v1.XmlSerializer;

/**
 * Generates an epub file. Not thread-safe, single use object.
 *
 * @author paul
 */
public class EpubWriter {

  private static final System.Logger log = System.getLogger(EpubWriter.class.getName());

  // package
  static final String EMPTY_NAMESPACE_PREFIX = "";

  /**
   * Pattern matching illegal characters in OCF filenames as per EPUB spec. Rejects: \ : * ? " < > |
   * DEL (0x7F) and control characters (0x00-0x1F)
   */
  private static final Pattern ILLEGAL_OCF_CHARS =
      Pattern.compile("[\\\\:*?\"<>|\\x00-\\x1F\\x7F]");

  private BookProcessor bookProcessor = BookProcessor.IDENTITY_BOOKPROCESSOR;
  private EpubWriterConfig config = EpubWriterConfig.defaultConfig();

  public EpubWriter() {
    this(BookProcessor.IDENTITY_BOOKPROCESSOR);
  }

  public EpubWriter(BookProcessor bookProcessor) {
    this.bookProcessor = bookProcessor;
  }

  public EpubWriter(BookProcessor bookProcessor, EpubWriterConfig config) {
    this.bookProcessor = bookProcessor;
    this.config = config;
  }

  public EpubWriter(EpubWriterConfig config) {
    this.config = config;
  }

  public void write(Book book, OutputStream out) throws IOException {
    book = processBook(book);
    try (ZipOutputStream resultStream = new ZipOutputStream(out)) {
      resultStream.setLevel(config.compressionLevel());
      writeMimeType(resultStream);
      writeContainer(resultStream);
      initTOCResource(book);
      initNavResource(book);
      writeResources(book, resultStream);
      writePackageDocument(book, resultStream);
    }
  }

  private Book processBook(Book book) {
    if (book == null) {
      book = new Book();
    }
    if (book.getMetadata() == null) {
      book.setMetadata(new Metadata());
    }
    if (book.getResources() == null) {
      book.setResources(new Resources());
    }
    if (book.getSpine() == null) {
      book.setSpine(new Spine());
    }
    if (book.getTableOfContents() == null) {
      book.setTableOfContents(new TableOfContents());
    }
    if (bookProcessor != null) {
      book = bookProcessor.processBook(book);
    }
    return book;
  }

  private static void initTOCResource(Book book) {
    Resource tocResource;
    try {
      tocResource = NCXDocument.createNCXResource(book);
      Resource currentTocResource = book.getSpine().getTocResource();
      if (currentTocResource != null) {
        book.getResources().remove(currentTocResource.getHref());
      }
      book.getSpine().setTocResource(tocResource);
      book.getResources().add(tocResource);
    } catch (Exception ex) {
      log.log(
          System.Logger.Level.ERROR,
          "Error writing table of contents: " + ex.getClass().getName() + ": " + ex.getMessage(),
          ex);
    }
  }

  /** Generates the EPUB3 Navigation Document and adds it to the book's resources. */
  private static void initNavResource(Book book) {
    try {
      Resource navResource = NavDocument.createNavResource(book);
      // Remove any existing NAV resource
      Resource existingNav = book.getResources().getByHref(NavDocument.DEFAULT_NAV_HREF);
      if (existingNav != null) {
        book.getResources().remove(existingNav.getHref());
      }
      book.getResources().add(navResource);
    } catch (Exception ex) {
      log.log(
          System.Logger.Level.ERROR,
          "Error writing navigation document: " + ex.getClass().getName() + ": " + ex.getMessage(),
          ex);
    }
  }

  private void writeResources(Book book, ZipOutputStream resultStream) throws IOException {
    Set<String> writtenEntries = new HashSet<>();
    for (Resource resource : book.getResources().getAll()) {
      writeResource(resource, resultStream, writtenEntries, config);
    }
  }

  /**
   * Writes the resource to the resultStream.
   *
   * @param resource
   * @param resultStream
   * @param writtenEntries set of already written entry names to detect duplicates
   * @throws IOException
   */
  private static void writeResource(
      Resource resource,
      ZipOutputStream resultStream,
      Set<String> writtenEntries,
      EpubWriterConfig writerConfig)
      throws IOException {
    if (resource == null) {
      return;
    }
    String href = sanitizeHref(encodeHref(resource.getHref()));
    // NFC normalization for cross-platform ZIP compatibility
    String raw = "OEBPS/" + href;
    String entryName =
        Normalizer.isNormalized(raw, Normalizer.Form.NFC)
            ? raw
            : Normalizer.normalize(raw, Normalizer.Form.NFC);
    var zipEntry = new ZipEntry(entryName);

    // Store already-compressed formats without re-deflating
    if (writerConfig.shouldStore(resource.getMediaType())) {
      zipEntry.setMethod(ZipEntry.STORED);
      try {
        if (resource instanceof OffHeapResource offHeap && offHeap.getSegment() != null) {
          MemorySegment seg = offHeap.getSegment();
          long size = seg.byteSize();
          zipEntry.setSize(size);
          zipEntry.setCompressedSize(size);
          zipEntry.setCrc(calculateCrcFromSegment(seg));
        } else {
          byte[] data = resource.getData();
          zipEntry.setSize(data.length);
          zipEntry.setCompressedSize(data.length);
          zipEntry.setCrc(calculateCrc(data));
        }
      } catch (IOException e) {
        log.log(System.Logger.Level.WARNING, "Failed to pre-calculate STORED entry: " + href, e);
        zipEntry.setMethod(ZipEntry.DEFLATED);
      }
    }

    // PKG_060: Prevent duplicate ZIP entries
    if (!writtenEntries.add(zipEntry.getName())) {
      log.log(System.Logger.Level.WARNING, "Duplicate ZIP entry skipped: " + zipEntry.getName());
      return;
    }

    resultStream.putNextEntry(zipEntry);
    try {
      if (resource instanceof OffHeapResource offHeap && offHeap.getSegment() != null) {
        // Stream directly from off-heap memory without going through InputStream
        writeSegmentToStream(offHeap.getSegment(), resultStream);
      } else {
        try (InputStream inputStream = resource.getInputStream()) {
          IOUtil.copy(inputStream, resultStream);
        }
      }
    } catch (Exception e) {
      log.log(System.Logger.Level.ERROR, e.getMessage(), e);
    } finally {
      resultStream.closeEntry();
    }
  }

  private void writePackageDocument(Book book, ZipOutputStream resultStream) throws IOException {
    ZipEntry opfEntry = new ZipEntry("OEBPS/content.opf");
    // PKG_060: Check for duplicate
    resultStream.putNextEntry(opfEntry);
    XmlSerializer xmlSerializer = EpubProcessorSupport.createXmlSerializer(resultStream);
    PackageDocumentWriter.write(this, xmlSerializer, book);
    xmlSerializer.flush();
    resultStream.closeEntry();
  }

  /**
   * Writes the META-INF/container.xml file.
   *
   * @param resultStream
   * @throws IOException
   */
  private static void writeContainer(ZipOutputStream resultStream) throws IOException {
    ZipEntry containerEntry = new ZipEntry("META-INF/container.xml");
    resultStream.putNextEntry(containerEntry);
    Writer out = new OutputStreamWriter(resultStream, StandardCharsets.UTF_8);
    out.write("<?xml version=\"1.0\"?>\n");
    out.write(
        "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n");
    out.write("\t<rootfiles>\n");
    out.write(
        "\t\t<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n");
    out.write("\t</rootfiles>\n");
    out.write("</container>");
    out.flush();
    resultStream.closeEntry();
  }

  /**
   * Stores the mimetype as an uncompressed file in the ZipOutputStream. Must be the first entry in
   * the ZIP file per EPUB spec (PKG_006). Must use STORED method (no compression) per PKG_007. Must
   * have no extra field per PKG_005.
   *
   * @param resultStream
   * @throws IOException
   */
  private static void writeMimeType(ZipOutputStream resultStream) throws IOException {
    ZipEntry mimetypeZipEntry = new ZipEntry("mimetype");
    mimetypeZipEntry.setMethod(ZipEntry.STORED); // must be uncompressed
    mimetypeZipEntry.setExtra(new byte[0]); // PKG_005: no extra field
    byte[] mimetypeBytes = MediaTypes.EPUB.name().getBytes(StandardCharsets.US_ASCII);
    mimetypeZipEntry.setSize(mimetypeBytes.length);
    mimetypeZipEntry.setCompressedSize(mimetypeBytes.length);
    mimetypeZipEntry.setCrc(calculateCrc(mimetypeBytes));
    resultStream.putNextEntry(mimetypeZipEntry);
    resultStream.write(mimetypeBytes);
    resultStream.closeEntry();
  }

  private static long calculateCrc(byte[] data) {
    CRC32 crc = new CRC32();
    crc.update(data);
    return crc.getValue();
  }

  /** Computes CRC32 directly from a MemorySegment without copying to a heap byte[]. */
  private static long calculateCrcFromSegment(MemorySegment segment) {
    CRC32 crc = new CRC32();
    long size = segment.byteSize();
    long offset = 0;
    byte[] buffer = new byte[(int) Math.min(IOUtil.IO_COPY_BUFFER_SIZE, size)];
    while (offset < size) {
      int chunk = (int) Math.min(buffer.length, size - offset);
      MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, buffer, 0, chunk);
      crc.update(buffer, 0, chunk);
      offset += chunk;
    }
    return crc.getValue();
  }

  /** Copies a MemorySegment to an OutputStream in chunks, avoiding full heap materialization. */
  private static void writeSegmentToStream(MemorySegment segment, OutputStream out)
      throws IOException {
    long size = segment.byteSize();
    long offset = 0;
    byte[] buffer = new byte[(int) Math.min(IOUtil.IO_COPY_BUFFER_SIZE, size)];
    while (offset < size) {
      int toWrite = (int) Math.min(buffer.length, size - offset);
      MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, buffer, 0, toWrite);
      out.write(buffer, 0, toWrite);
      offset += toWrite;
    }
  }

  static String getNcxId() {
    return "ncx";
  }

  static String getNcxHref() {
    return "toc.ncx";
  }

  static String getNcxMediaType() {
    return MediaTypes.NCX.name();
  }

  public BookProcessor getBookProcessor() {
    return bookProcessor;
  }

  public void setBookProcessor(BookProcessor bookProcessor) {
    this.bookProcessor = bookProcessor;
  }

  public EpubWriterConfig getConfig() {
    return config;
  }

  public void setConfig(EpubWriterConfig config) {
    this.config = config;
  }

  static String encodeHref(String href) {
    if (StringUtil.isBlank(href)) {
      return href;
    }
    String decodedHref = URLDecoder.decode(href, StandardCharsets.UTF_8);
    String[] segments = decodedHref.split("/", -1);
    StringBuilder encoded = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        encoded.append('/');
      }
      // PKG_010: URL-encode spaces as %20
      encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
    }
    return encoded.toString();
  }

  /**
   * Sanitizes filenames by replacing illegal OCF characters with underscores. PKG_009: Rejects
   * filenames containing \ : * ? " < > | DEL (0x7F) or control characters.
   *
   * @param href the href to sanitize
   * @return the sanitized href
   */
  public static String sanitizeHref(String href) {
    if (href == null) {
      return null;
    }
    return ILLEGAL_OCF_CHARS.matcher(href).replaceAll("_");
  }
}
