package org.grimmory.epub4j.bookprocessor;

import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * If the book has no identifier it adds a generated UUID as identifier.
 *
 * @author paul
 */
public class FixIdentifierBookProcessor implements BookProcessor {

  @Override
  public Book processBook(Book book) {
    if (book.getMetadata().getIdentifiers().isEmpty()) {
      book.getMetadata().addIdentifier(new Identifier());
    }
    return book;
  }
}
