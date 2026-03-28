package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileInputStream;
import java.io.IOException;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.util.IOUtil;
import org.junit.jupiter.api.*;

public class NCXDocumentTest {

  byte[] ncxData;

  @BeforeEach
  public void setUp() throws IOException {
    ncxData = IOUtil.toByteArray(new FileInputStream("src/test/resources/toc.xml"));
  }

  private static void addResource(Book book, String filename) {
    Resource chapterResource =
        new Resource("id1", "Hello, world !".getBytes(), filename, MediaTypes.XHTML);
    book.addResource(chapterResource);
    book.getSpine().addResource(chapterResource);
  }

  /** Test of read method, of class NCXDocument. */
  @Test
  public void testReadWithNonRootLevelTOC() {

    // If the tox.ncx file is not in the root, the hrefs it refers to need to preserve its path.
    Book book = new Book();
    Resource ncxResource = new Resource(ncxData, "xhtml/toc.ncx");
    addResource(book, "xhtml/chapter1.html");
    addResource(book, "xhtml/chapter2.html");
    addResource(book, "xhtml/chapter2_1.html");
    addResource(book, "xhtml/chapter3.html");

    book.setNcxResource(ncxResource);
    book.getSpine().setTocResource(ncxResource);

    NCXDocument.read(book, new EpubReader());
    assertEquals(
        "xhtml/chapter1.html",
        book.getTableOfContents().getTocReferences().getFirst().getCompleteHref());
  }
}
