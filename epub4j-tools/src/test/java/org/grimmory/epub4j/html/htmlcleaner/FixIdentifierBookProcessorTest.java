package org.grimmory.epub4j.html.htmlcleaner;

import static org.junit.jupiter.api.Assertions.*;

import org.grimmory.epub4j.bookprocessor.FixIdentifierBookProcessor;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.util.CollectionUtil;
import org.junit.jupiter.api.Test;

public class FixIdentifierBookProcessorTest {

  @Test
  public void test_empty_book() {
    Book book = new Book();
    FixIdentifierBookProcessor fixIdentifierBookProcessor = new FixIdentifierBookProcessor();
    Book resultBook = fixIdentifierBookProcessor.processBook(book);
    assertEquals(1, resultBook.getMetadata().getIdentifiers().size());
    Identifier identifier = CollectionUtil.first(resultBook.getMetadata().getIdentifiers());
    assertEquals(Identifier.Scheme.UUID, identifier.getScheme());
  }

  @Test
  public void test_single_identifier() {
    Book book = new Book();
    Identifier identifier = new Identifier(Identifier.Scheme.ISBN, "1234");
    book.getMetadata().addIdentifier(identifier);
    FixIdentifierBookProcessor fixIdentifierBookProcessor = new FixIdentifierBookProcessor();
    Book resultBook = fixIdentifierBookProcessor.processBook(book);
    assertEquals(1, resultBook.getMetadata().getIdentifiers().size());
    Identifier actualIdentifier = CollectionUtil.first(resultBook.getMetadata().getIdentifiers());
    assertEquals(identifier, actualIdentifier);
  }
}
