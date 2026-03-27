package org.grimmory.epub4j.bookprocessor;

import java.io.IOException;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Helper class for BookProcessors that only manipulate html type resources.
 *
 * @author paul
 */
public abstract class HtmlBookProcessor implements BookProcessor {

  private static final System.Logger log = System.getLogger(HtmlBookProcessor.class.getName());
  public static final String OUTPUT_ENCODING = "UTF-8";

  @Override
  public Book processBook(Book book) {
    for (Resource resource : book.getResources().getAll()) {
      try {
        cleanupResource(resource, book);
      } catch (IOException e) {
        log.log(System.Logger.Level.ERROR, e.getMessage(), e);
      }
    }
    return book;
  }

  private void cleanupResource(Resource resource, Book book) throws IOException {
    if (resource.getMediaType() == MediaTypes.XHTML) {
      byte[] cleanedHtml = processHtml(resource, book);
      resource.setData(cleanedHtml);
      resource.setInputEncoding(Constants.CHARACTER_ENCODING);
    }
  }

  protected abstract byte[] processHtml(Resource resource, Book book) throws IOException;
}
