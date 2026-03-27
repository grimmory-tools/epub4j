package org.grimmory.epub4j.bookprocessor;

import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.epub.BookProcessor;

public class FixMissingResourceBookProcessor implements BookProcessor {

  @Override
  public Book processBook(Book book) {
    return book;
  }
}
