package org.grimmory.epub4j.epub;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.TOCReference;

/**
 * Generates the EPUB3 XHTML Navigation Document as defined by <a
 * href="https://www.w3.org/TR/epub-33/#sec-nav">EPUB 3.3 §7 Navigation Document</a>.
 *
 * <p>The Navigation Document replaces the EPUB2 NCX as the primary table of contents mechanism in
 * EPUB3. It is a valid XHTML5 document containing a {@code <nav>} element with {@code
 * epub:type="toc"}.
 */
public class NavDocument {

  public static final String NAV_ITEM_ID = "nav";
  public static final String DEFAULT_NAV_HREF = "toc.xhtml";
  public static final String NAMESPACE_EPUB_OPS = "http://www.idpf.org/2007/ops";

  /**
   * Creates an EPUB3 Navigation Document resource from the book's table of contents.
   *
   * @param book the book whose TOC will be used
   * @return a Resource containing the XHTML navigation document
   */
  public static Resource createNavResource(Book book) {
    String title = book.getTitle();
    if (title == null || title.isEmpty()) {
      title = "Table of Contents";
    }

    String escapedTitle = escapeXml(title);
    String header =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="%s">
                <head><title>%s</title></head>
                <body>
                <nav epub:type="toc" id="toc">
                  <h1>%s</h1>
                  <ol>
                """
            .formatted(NAMESPACE_EPUB_OPS, escapedTitle, escapedTitle);

    StringBuilder sb = new StringBuilder(header);

    List<TOCReference> tocRefs = book.getTableOfContents().getTocReferences();
    if (tocRefs != null) {
      writeTocEntries(sb, tocRefs, 2);
    }

    sb.append(
        """
                  </ol>
                </nav>
                </body>
                </html>
                """);

    return new Resource(
        NAV_ITEM_ID,
        sb.toString().getBytes(StandardCharsets.UTF_8),
        DEFAULT_NAV_HREF,
        MediaTypes.XHTML);
  }

  private static void writeTocEntries(
      StringBuilder sb, List<TOCReference> tocReferences, int indent) {
    String pad = "  ".repeat(indent);
    for (TOCReference ref : tocReferences) {
      String title = ref.getTitle();
      if (title == null || title.isEmpty()) {
        title = "Untitled";
      }

      if (ref.getResource() == null) {
        // Abstract section with no resource - emit list item with nested children
        if (ref.getChildren() != null && !ref.getChildren().isEmpty()) {
          sb.append(pad).append("<li>\n");
          sb.append(pad).append("  <span>").append(escapeXml(title)).append("</span>\n");
          sb.append(pad).append("  <ol>\n");
          writeTocEntries(sb, ref.getChildren(), indent + 2);
          sb.append(pad).append("  </ol>\n");
          sb.append(pad).append("</li>\n");
        }
        continue;
      }

      String href = ref.getCompleteHref();

      sb.append(pad).append("<li>\n");
      sb.append(pad)
          .append("  <a href=\"")
          .append(escapeXmlAttr(href))
          .append("\">")
          .append(escapeXml(title))
          .append("</a>\n");

      if (ref.getChildren() != null && !ref.getChildren().isEmpty()) {
        sb.append(pad).append("  <ol>\n");
        writeTocEntries(sb, ref.getChildren(), indent + 2);
        sb.append(pad).append("  </ol>\n");
      }

      sb.append(pad).append("</li>\n");
    }
  }

  private static String escapeXml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String escapeXmlAttr(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
