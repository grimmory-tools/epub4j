package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.Resource;

/**
 * Fluent builder for EPUB creation, particularly suited to image-based EPUBs (comic book
 * conversions). Handles mimetype, META-INF/container.xml, OPF, NCX, and nav.xhtml generation.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * EpubBuilder.create()
 *     .title("My Comic")
 *     .author("Author Name")
 *     .language("en")
 *     .addImagePage(imageBytes, MediaTypes.JPG)
 *     .build(outputPath);
 * }</pre>
 */
public class EpubBuilder {

  private static final String MIMETYPE_CONTENT = "application/epub+zip";
  private static final String OEBPS = "OEBPS/";
  private static final String IMAGE_DIR = OEBPS + "Images/";
  private static final String TEXT_DIR = OEBPS + "Text/";
  private static final String OPF_PATH = OEBPS + "content.opf";
  private static final String NAV_PATH = OEBPS + "nav.xhtml";

  private String title = "Untitled";
  private String language = "en";
  private String subtitle;
  private String description;
  private String publisher;
  private String identifier;
  private final List<Author> authors = new ArrayList<>();
  private final List<String> subjects = new ArrayList<>();
  private final List<PageEntry> pages = new ArrayList<>();
  private byte[] coverImageData;
  private MediaType coverMediaType;

  private EpubBuilder() {}

  /** Creates a new EpubBuilder instance. */
  public static EpubBuilder create() {
    return new EpubBuilder();
  }

  public EpubBuilder title(String title) {
    this.title = Objects.requireNonNull(title, "title");
    return this;
  }

  public EpubBuilder subtitle(String subtitle) {
    this.subtitle = subtitle;
    return this;
  }

  public EpubBuilder language(String language) {
    this.language = Objects.requireNonNull(language, "language");
    return this;
  }

  public EpubBuilder author(String name) {
    Objects.requireNonNull(name, "name");
    String[] parts = name.split(" ", 2);
    if (parts.length == 2) {
      authors.add(new Author(parts[0], parts[1]));
    } else {
      authors.add(new Author(name));
    }
    return this;
  }

  public EpubBuilder author(Author author) {
    authors.add(Objects.requireNonNull(author, "author"));
    return this;
  }

  public EpubBuilder description(String description) {
    this.description = description;
    return this;
  }

  public EpubBuilder publisher(String publisher) {
    this.publisher = publisher;
    return this;
  }

  public EpubBuilder identifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  public EpubBuilder subject(String subject) {
    if (subject != null && !subject.isBlank()) {
      subjects.add(subject.trim());
    }
    return this;
  }

  /**
   * Sets the cover image data. The first page added will also use this as cover if no separate
   * cover is desired.
   */
  public EpubBuilder coverImage(byte[] data, MediaType mediaType) {
    this.coverImageData = Objects.requireNonNull(data, "data").clone();
    this.coverMediaType = Objects.requireNonNull(mediaType, "mediaType");
    return this;
  }

  /** Adds an image as a page in the EPUB. Each image gets its own XHTML wrapper page. */
  public EpubBuilder addImagePage(byte[] imageData, MediaType mediaType) {
    Objects.requireNonNull(imageData, "imageData");
    Objects.requireNonNull(mediaType, "mediaType");
    pages.add(new PageEntry(imageData.clone(), mediaType, null));
    return this;
  }

  /** Adds an XHTML content page. */
  public EpubBuilder addPage(String xhtmlContent) {
    Objects.requireNonNull(xhtmlContent, "xhtmlContent");
    pages.add(new PageEntry(null, null, xhtmlContent));
    return this;
  }

  /** Builds the EPUB and writes it to the given path. */
  public void build(Path outputPath) throws IOException {
    Objects.requireNonNull(outputPath, "outputPath");
    try (OutputStream out = Files.newOutputStream(outputPath)) {
      build(out);
    }
  }

  /** Builds the EPUB and writes it to the given output stream. */
  public void build(OutputStream out) throws IOException {
    Objects.requireNonNull(out, "out");

    if (pages.isEmpty()) {
      throw new IllegalStateException("At least one page is required");
    }

    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      writeMimetype(zos);
      writeContainerXml(zos);
      writeNav(zos);
      writePages(zos);
      writeContentOpf(zos);
    }
  }

  /**
   * Builds the EPUB and returns it as a Book model. Useful for further processing with EpubWriter
   * or EpubOptimizer.
   */
  public Book toBook() {
    Book book = new Book();
    book.getMetadata().addTitle(title);
    book.getMetadata().setLanguage(language);

    if (subtitle != null) {
      book.getMetadata().setSubtitle(subtitle);
    }
    if (description != null) {
      book.getMetadata().addDescription(description);
    }
    if (publisher != null) {
      book.getMetadata().addPublisher(publisher);
    }
    for (Author a : authors) {
      book.getMetadata().addAuthor(a);
    }
    for (String s : subjects) {
      book.getMetadata().getSubjects().add(s);
    }
    if (identifier != null) {
      book.getMetadata().addIdentifier(new Identifier(Identifier.Scheme.UUID, identifier));
    }

    int pageNum = 0;
    for (PageEntry page : pages) {
      pageNum++;
      if (page.isImage()) {
        String ext = page.mediaType().defaultExtension();
        String imageHref = "Images/image_" + String.format("%04d", pageNum) + ext;
        Resource imageResource = new Resource(page.imageData(), imageHref);
        book.addResource(imageResource);

        String xhtml =
            generateImageXhtml("../Images/image_" + String.format("%04d", pageNum) + ext, pageNum);
        String pageHref = "Text/page_" + String.format("%04d", pageNum) + ".xhtml";
        Resource pageResource = new Resource(xhtml.getBytes(StandardCharsets.UTF_8), pageHref);
        book.addResource(pageResource);
        book.getSpine().addResource(pageResource);

        if (pageNum == 1 && coverImageData == null) {
          book.setCoverImage(imageResource);
        }
      } else {
        String pageHref = "Text/page_" + String.format("%04d", pageNum) + ".xhtml";
        Resource pageResource =
            new Resource(page.xhtmlContent().getBytes(StandardCharsets.UTF_8), pageHref);
        book.addResource(pageResource);
        book.getSpine().addResource(pageResource);
      }
    }

    if (coverImageData != null) {
      String ext = coverMediaType.defaultExtension();
      Resource coverResource = new Resource(coverImageData, "Images/cover" + ext);
      book.setCoverImage(coverResource);
    }

    return book;
  }

  // -- ZIP writing --

  private static void writeMimetype(ZipOutputStream zos) throws IOException {
    byte[] data = MIMETYPE_CONTENT.getBytes(StandardCharsets.UTF_8);
    ZipEntry entry = new ZipEntry("mimetype");
    entry.setMethod(ZipEntry.STORED);
    entry.setSize(data.length);
    entry.setCompressedSize(data.length);
    CRC32 crc = new CRC32();
    crc.update(data);
    entry.setCrc(crc.getValue());
    zos.putNextEntry(entry);
    zos.write(data);
    zos.closeEntry();
  }

  private static void writeContainerXml(ZipOutputStream zos) throws IOException {
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
            + "  <rootfiles>\n"
            + "    <rootfile full-path=\""
            + OPF_PATH
            + "\" media-type=\"application/oebps-package+xml\"/>\n"
            + "  </rootfiles>\n"
            + "</container>\n";
    writeDeflated(zos, "META-INF/container.xml", xml.getBytes(StandardCharsets.UTF_8));
  }

  private void writeNav(ZipOutputStream zos) throws IOException {
    StringBuilder nav = new StringBuilder();
    nav.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    nav.append(
        "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n");
    nav.append("<head><title>").append(escapeXml(title)).append("</title></head>\n");
    nav.append("<body>\n<nav epub:type=\"toc\" id=\"toc\">\n<ol>\n");

    for (int i = 0; i < pages.size(); i++) {
      String href = "Text/page_" + String.format("%04d", i + 1) + ".xhtml";
      nav.append("  <li><a href=\"")
          .append(href)
          .append("\">Page ")
          .append(i + 1)
          .append("</a></li>\n");
    }

    nav.append("</ol>\n</nav>\n</body>\n</html>\n");
    writeDeflated(zos, NAV_PATH, nav.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void writePages(ZipOutputStream zos) throws IOException {
    // Cover image
    if (coverImageData != null) {
      String ext = coverMediaType.defaultExtension();
      writeStored(zos, IMAGE_DIR + "cover" + ext, coverImageData);
    }

    for (int i = 0; i < pages.size(); i++) {
      PageEntry page = pages.get(i);
      int num = i + 1;

      if (page.isImage()) {
        String ext = page.mediaType().defaultExtension();
        String imageName = "image_" + String.format("%04d", num) + ext;
        writeStored(zos, IMAGE_DIR + imageName, page.imageData());

        String xhtml = generateImageXhtml("../Images/" + imageName, num);
        String pageName = "page_" + String.format("%04d", num) + ".xhtml";
        writeDeflated(zos, TEXT_DIR + pageName, xhtml.getBytes(StandardCharsets.UTF_8));
      } else {
        String pageName = "page_" + String.format("%04d", num) + ".xhtml";
        writeDeflated(
            zos, TEXT_DIR + pageName, page.xhtmlContent().getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  private void writeContentOpf(ZipOutputStream zos) throws IOException {
    StringBuilder opf = new StringBuilder();
    String bookId = identifier != null ? identifier : "urn:uuid:" + UUID.randomUUID();

    opf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    opf.append(
        "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"BookId\">\n");

    // Metadata
    opf.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
    opf.append("    <dc:identifier id=\"BookId\">")
        .append(escapeXml(bookId))
        .append("</dc:identifier>\n");
    opf.append("    <dc:title>").append(escapeXml(title)).append("</dc:title>\n");
    opf.append("    <dc:language>").append(escapeXml(language)).append("</dc:language>\n");

    for (Author a : authors) {
      String display = buildAuthorDisplay(a);
      if (!display.isBlank()) {
        opf.append("    <dc:creator>").append(escapeXml(display)).append("</dc:creator>\n");
      }
    }
    if (description != null) {
      opf.append("    <dc:description>")
          .append(escapeXml(description))
          .append("</dc:description>\n");
    }
    if (publisher != null) {
      opf.append("    <dc:publisher>").append(escapeXml(publisher)).append("</dc:publisher>\n");
    }
    for (String s : subjects) {
      opf.append("    <dc:subject>").append(escapeXml(s)).append("</dc:subject>\n");
    }

    String modified = Instant.now().toString();
    if (modified.length() > 20) {
      modified = modified.substring(0, 19) + "Z";
    }
    opf.append("    <meta property=\"dcterms:modified\">").append(modified).append("</meta>\n");
    opf.append("  </metadata>\n");

    // Manifest
    opf.append("  <manifest>\n");
    opf.append(
        "    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");

    if (coverImageData != null) {
      String ext = coverMediaType.defaultExtension();
      opf.append("    <item id=\"cover-image\" href=\"Images/cover")
          .append(ext)
          .append("\" media-type=\"")
          .append(coverMediaType.name())
          .append("\" properties=\"cover-image\"/>\n");
    }

    for (int i = 0; i < pages.size(); i++) {
      int num = i + 1;
      PageEntry page = pages.get(i);

      if (page.isImage()) {
        String ext = page.mediaType().defaultExtension();
        String imageId = "image_" + String.format("%04d", num);
        String imageName = imageId + ext;
        opf.append("    <item id=\"")
            .append(imageId)
            .append("\" href=\"Images/")
            .append(imageName)
            .append("\" media-type=\"")
            .append(page.mediaType().name())
            .append("\"/>\n");
      }

      String pageId = "page_" + String.format("%04d", num);
      opf.append("    <item id=\"")
          .append(pageId)
          .append("\" href=\"Text/")
          .append(pageId)
          .append(".xhtml\" media-type=\"application/xhtml+xml\"/>\n");
    }
    opf.append("  </manifest>\n");

    // Spine
    opf.append("  <spine>\n");
    for (int i = 0; i < pages.size(); i++) {
      String pageId = "page_" + String.format("%04d", i + 1);
      opf.append("    <itemref idref=\"").append(pageId).append("\"/>\n");
    }
    opf.append("  </spine>\n");
    opf.append("</package>\n");

    writeDeflated(zos, OPF_PATH, opf.toString().getBytes(StandardCharsets.UTF_8));
  }

  // -- Helpers --

  private static String generateImageXhtml(String imageSrc, int pageNumber) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE html>\n"
        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
        + "<head><title>Page "
        + pageNumber
        + "</title>\n"
        + "<style>body{margin:0;padding:0}img{max-width:100%;max-height:100vh;"
        + "display:block;margin:auto}</style>\n"
        + "</head>\n"
        + "<body><img src=\""
        + escapeXml(imageSrc)
        + "\" alt=\"Page "
        + pageNumber
        + "\"/></body>\n"
        + "</html>\n";
  }

  private static String buildAuthorDisplay(Author author) {
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

  private static String escapeXml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static void writeDeflated(ZipOutputStream zos, String path, byte[] data)
      throws IOException {
    ZipEntry entry = new ZipEntry(path);
    entry.setMethod(ZipEntry.DEFLATED);
    zos.putNextEntry(entry);
    zos.write(data);
    zos.closeEntry();
  }

  private static void writeStored(ZipOutputStream zos, String path, byte[] data)
      throws IOException {
    ZipEntry entry = new ZipEntry(path);
    entry.setMethod(ZipEntry.STORED);
    entry.setSize(data.length);
    entry.setCompressedSize(data.length);
    CRC32 crc = new CRC32();
    crc.update(data);
    entry.setCrc(crc.getValue());
    zos.putNextEntry(entry);
    zos.write(data);
    zos.closeEntry();
  }

  private record PageEntry(byte[] imageData, MediaType mediaType, String xhtmlContent) {
    boolean isImage() {
      return imageData != null;
    }
  }
}
